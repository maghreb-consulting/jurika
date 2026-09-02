import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  CheckCircle,
  Clock,
  Download,
  Eye,
  FileSpreadsheet,
  FileText,
  Landmark,
  Loader2,
  Lock,
  MessageSquare,
  Plus,
  Printer,
  Send,
  Shield,
  Trash2,
  Upload,
} from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { PdfPreviewModal } from '../../components/ui/PdfPreviewModal';
import { dataroomService } from '../../services/dataroom.service';
import { extractError } from '../../lib/api';
import { isDossierArchived } from '../../lib/dossierArchive';
import { requiredMsg } from '../../lib/formValidation';
import { useDataroomSocket } from '../../lib/useDataroomSocket';
import type {
  ClientPermissions,
  DataroomDocumentEvent,
  DemandeStatut,
  DemandeSummary,
  DepotSummary,
  DocumentSummary,
  DossierBrief,
  DossierJuridiqueView,
} from '../../types/dataroom';
import { DOCUMENT_TYPE_LABELS } from '../../types/dataroom';
import { DossierComptableTab } from './DossierComptableTab';
import { DossierFiscalTab } from './DossierFiscalTab';
import { DataroomReadOnlyHint } from './components/DataroomReadOnlyHint';

type Tab = 'juridique' | 'comptable' | 'fiscal' | 'depots' | 'demandes';

const STATUT_LABEL: Record<DemandeStatut, string> = {
  NON_TRAITEE: 'Non traitee',
  EN_COURS: 'En cours',
  TRAITEE: 'Traitee',
};

const STATUT_PILL: Record<DemandeStatut, { bg: string; text: string }> = {
  NON_TRAITEE: { bg: 'bg-danger', text: 'text-bg-raised' },
  EN_COURS: { bg: 'bg-accent', text: 'text-bg-raised' },
  TRAITEE: { bg: 'bg-success', text: 'text-bg-raised' },
};

/**
 * Portail client — version simplifiee de la Data Room pour les utilisateurs CLIENT.
 * - Hero : nom de la societe + cabinet gestionnaire + permissions.
 * - Encart violet "Vos demandes" avec compteurs et CTA.
 * - 3 onglets : Dossier Juridique (lecture seule, versions courantes uniquement),
 *   Dossier Comptable (uploadable par le client — V2 spec),
 *   Mes Demandes (CRUD limite : creation + lecture).
 *
 * Le client ne voit PAS l'historique des tickets clotures (volontairement).
 */
export function ClientDataroomView() {
  const [dossier, setDossier] = useState<DossierBrief | null>(null);
  const [settings, setSettings] = useState<ClientPermissions | null>(null);
  const [juridique, setJuridique] = useState<DossierJuridiqueView | null>(null);
  const [demandes, setDemandes] = useState<DemandeSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  // Deep-link `?tab=depots` (depuis une requête PIECE « Aller à mes Dépôts »)
  // pour atterrir directement sur l'onglet voulu ; défaut = juridique.
  const [tab, setTab] = useState<Tab>(() => {
    const t = new URLSearchParams(window.location.search).get('tab');
    const allowed: Tab[] = ['juridique', 'comptable', 'fiscal', 'depots', 'demandes'];
    return (allowed as string[]).includes(t ?? '') ? (t as Tab) : 'juridique';
  });

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // Le backend renvoie le(s) dossier(s) accessibles au client courant.
      const list = await dataroomService.listDossiers();
      const first = list[0] ?? null;
      setDossier(first);
      if (first) {
        const [s, j, d] = await Promise.all([
          dataroomService.getMyPermissions(first.id).catch(() => null),
          dataroomService.getJuridique(first.id).catch(() => null),
          dataroomService.listDemandesByDossier(first.id).catch(() => [] as DemandeSummary[]),
        ]);
        setSettings(s);
        setJuridique(j);
        setDemandes(d);
      }
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  // Sprint 7 / TASK 5.3 -- WebSocket : reload du juridique + toast quand
  // l'employe upload/delete un document cote cabinet. Le payload arrive
  // via realtime-service (Socket.io room "dataroom:{dossierId}").
  const [realtimeToast, setRealtimeToast] = useState<{
    text: string;
    until: number;
  } | null>(null);
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const handleDataroomEvent = useCallback(
    (event: DataroomDocumentEvent) => {
      const verb =
        event.kind === 'UPLOADED'
          ? 'Nouveau document ajoute'
          : event.kind === 'DELETED'
            ? 'Document retire'
            : 'Document mis a jour';
      const label = event.title ?? event.documentType ?? 'Document';
      setRealtimeToast({ text: `${verb} : ${label}`, until: Date.now() + 4000 });
      if (toastTimer.current) clearTimeout(toastTimer.current);
      toastTimer.current = setTimeout(() => setRealtimeToast(null), 4000);
      // reload du juridique (sans bloquer le reste)
      if (dossier) {
        dataroomService
          .getJuridique(dossier.id)
          .then((j) => setJuridique(j))
          .catch(() => {/* silent : best-effort refresh */});
      }
    },
    [dossier],
  );

  useDataroomSocket(dossier?.id, handleDataroomEvent);

  const canDownload = settings?.permDownload ?? false;
  const canPrint = settings?.permPrint ?? false;
  /**
   * Lot DIVERS §A (2026-08-13) — societe dissoute / liquidee / radiee : la Data
   * Room est une archive legale. Le client conserve consultation et
   * telechargement, mais ne peut plus deposer ni ouvrir de demande (le backend
   * refuse : `DOSSIER_ARCHIVED_READ_ONLY`).
   */
  const archived = isDossierArchived(dossier?.statut);
  const canDepot = (settings?.permDepot ?? false) && !archived;

  const counts = useMemo(() => {
    return demandes.reduce(
      (acc, d) => {
        acc[d.statut] = (acc[d.statut] ?? 0) + 1;
        return acc;
      },
      { NON_TRAITEE: 0, EN_COURS: 0, TRAITEE: 0 } as Record<DemandeStatut, number>,
    );
  }, [demandes]);

  if (loading) {
    return (
      <div className="flex justify-center py-20">
        <Loader2 className="h-8 w-8 animate-spin text-accent" />
      </div>
    );
  }

  if (error || !dossier) {
    return (
      <div className="rounded-2xl border border-danger/40 bg-danger/10 p-6 text-center">
        <p className="text-sm text-danger">
          {error ?? "Aucun Data Room n'est associe a votre compte."}
        </p>
      </div>
    );
  }

  const lastUpdate = juridique?.documentsEnVigueur
    .map((d) => new Date(d.createdAt).getTime())
    .reduce((acc, v) => (v > acc ? v : acc), 0);

  return (
    <div className="min-h-full bg-bg-overlay -m-4 md:-m-6 p-4 md:p-8">
      <div className="max-w-[1100px] mx-auto space-y-6">
        {/* Sprint 7 / TASK 5.3 -- toast realtime quand le cabinet upload/delete */}
        {realtimeToast && (
          <div
            className="fixed bottom-4 right-4 z-50 max-w-sm rounded-lg border border-accent/40 bg-bg-raised px-4 py-3 shadow-lg"
            role="status"
            aria-live="polite"
          >
            <div className="flex items-start gap-2">
              <div className="mt-0.5 h-2 w-2 rounded-full bg-accent animate-pulse" />
              <p className="text-sm text-fg">{realtimeToast.text}</p>
            </div>
          </div>
        )}

        {/* Hero */}
        <div className="bg-bg-raised rounded-xl p-6 shadow-sm border border-border">
          <div className="bg-accent/10 rounded-lg p-5 flex flex-col md:flex-row items-start md:items-center justify-between gap-3">
            <div className="flex items-start gap-4">
              <div className="w-12 h-12 bg-accent rounded-lg flex items-center justify-center text-bg-raised flex-shrink-0">
                <Lock className="w-6 h-6" />
              </div>
              <div>
                <h2 className="text-xl md:text-2xl font-bold text-fg mb-1">
                  {dossier.raisonSociale}
                </h2>
                <p className="text-sm text-fg-subtle">
                  Dossier gere par : votre cabinet juridique
                </p>
                {lastUpdate ? (
                  <p className="text-xs text-fg-subtle mt-1">
                    Derniere mise a jour :{' '}
                    {new Date(lastUpdate).toLocaleDateString('fr-FR')}
                  </p>
                ) : (
                  <p className="text-xs text-fg-subtle mt-1">
                    Aucun document publie pour le moment
                  </p>
                )}
              </div>
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                disabled={!canDownload}
                className={`px-4 h-9 border-2 rounded-lg flex items-center gap-2 text-xs transition ${
                  canDownload
                    ? 'border-border text-fg hover:border-accent bg-bg-raised'
                    : 'opacity-50 cursor-not-allowed border-border text-fg-subtle bg-bg-raised'
                }`}
                title="Telecharger tous les documents en vigueur"
              >
                <Download className="w-3.5 h-3.5" /> Tout telecharger
              </button>
              <button
                type="button"
                disabled={!canPrint}
                className={`px-4 h-9 rounded-lg flex items-center gap-2 text-xs transition ${
                  canPrint
                    ? 'text-fg hover:bg-bg-overlay'
                    : 'opacity-50 cursor-not-allowed text-fg-subtle'
                }`}
              >
                <Printer className="w-3.5 h-3.5" /> Imprimer
              </button>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-3 md:gap-4 mt-4 pt-4 border-t border-border text-xs text-fg-subtle">
            <span className="font-medium text-fg">Vos permissions :</span>
            <span className="flex items-center gap-1">
              <Eye className="w-3 h-3 text-success" /> Visualiser
            </span>
            <span
              className={`flex items-center gap-1 ${
                canDownload ? 'text-success' : 'text-danger'
              }`}
            >
              {canDownload ? (
                <Download className="w-3 h-3" />
              ) : (
                <Lock className="w-3 h-3" />
              )}{' '}
              Telecharger {canDownload ? '' : '(non autorise)'}
            </span>
            <span
              className={`flex items-center gap-1 ${
                canPrint ? 'text-success' : 'text-danger'
              }`}
            >
              {canPrint ? (
                <Printer className="w-3 h-3" />
              ) : (
                <Lock className="w-3 h-3" />
              )}{' '}
              Imprimer {canPrint ? '' : '(non autorise)'}
            </span>
            <span
              className={`flex items-center gap-1 ${
                canDepot ? 'text-success' : 'text-danger'
              }`}
            >
              {canDepot ? (
                <Upload className="w-3 h-3" />
              ) : (
                <Lock className="w-3 h-3" />
              )}{' '}
              Depot {canDepot ? '' : '(non autorise)'}
            </span>
          </div>

          {/* §A — bandeau archive legale, cote client. */}
          {archived && (
            <div className="mt-4">
              <DataroomReadOnlyHint
                statut={dossier.statut}
                testId="client-dataroom-readonly-banner"
              />
            </div>
          )}
        </div>

        {/* Encart violet — Vos demandes */}
        <div className="bg-gradient-to-r from-[#F5F3FF] to-[#EFF6FF] rounded-xl p-4 md:p-5 shadow-sm border border-accent/20 flex flex-col md:flex-row items-start md:items-center justify-between gap-3">
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 bg-accent rounded-xl flex items-center justify-center flex-shrink-0">
              <MessageSquare className="w-6 h-6 text-bg-raised" />
            </div>
            <div>
              <h3 className="text-base font-bold text-fg">
                Vos demandes
              </h3>
              <p className="text-xs text-fg-subtle">
                {counts.NON_TRAITEE > 0 ? (
                  <>
                    <span className="font-bold text-danger">
                      {counts.NON_TRAITEE} non traitee
                      {counts.NON_TRAITEE > 1 ? 's' : ''}
                    </span>{' '}
                    · {counts.EN_COURS} en cours · {counts.TRAITEE} traitee
                    {counts.TRAITEE > 1 ? 's' : ''}
                  </>
                ) : (
                  <>
                    {counts.EN_COURS} en cours · {counts.TRAITEE} traitee
                    {counts.TRAITEE > 1 ? 's' : ''}
                  </>
                )}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2">
              {counts.NON_TRAITEE > 0 && (
                <span className="px-2.5 py-1 bg-danger/10 text-danger rounded-full text-[10px] font-bold flex items-center gap-1">
                  <Clock className="w-3 h-3" /> {counts.NON_TRAITEE}
                </span>
              )}
              {counts.EN_COURS > 0 && (
                <span className="px-2.5 py-1 bg-accent/10 text-accent rounded-full text-[10px] font-bold flex items-center gap-1">
                  <span className="w-1.5 h-1.5 bg-accent rounded-full animate-pulse" />{' '}
                  {counts.EN_COURS}
                </span>
              )}
            </div>
            <button
              type="button"
              onClick={() => setTab('demandes')}
              className="flex items-center gap-2 px-4 md:px-5 h-10 bg-accent text-bg-raised rounded-lg hover:bg-accent-hover transition font-medium text-sm"
            >
              <Send className="w-4 h-4" /> Faire une demande
            </button>
          </div>
        </div>

        {/* Tabs */}
        <div className="bg-bg-raised rounded-xl shadow-sm border border-border overflow-hidden">
          <div className="border-b border-border px-3 md:px-6">
            <div className="flex gap-0 overflow-x-auto">
              <TabButton
                active={tab === 'juridique'}
                onClick={() => setTab('juridique')}
                color="#2563EB"
                icon={<FileText className="w-4 h-4" />}
                label="Dossier Juridique"
              />
              <TabButton
                active={tab === 'comptable'}
                onClick={() => setTab('comptable')}
                color="#10B981"
                icon={<FileSpreadsheet className="w-4 h-4" />}
                label="Dossier Comptable"
              />
              <TabButton
                active={tab === 'fiscal'}
                onClick={() => setTab('fiscal')}
                color="#0EA5E9"
                icon={<Landmark className="w-4 h-4" />}
                label="Dossier Fiscal"
              />
              <TabButton
                active={tab === 'depots'}
                onClick={() => setTab('depots')}
                color="#F59E0B"
                icon={<Upload className="w-4 h-4" />}
                label="Depots"
              />
              <TabButton
                active={tab === 'demandes'}
                onClick={() => setTab('demandes')}
                color="#7C3AED"
                icon={<MessageSquare className="w-4 h-4" />}
                label="Mes Demandes"
                badge={counts.NON_TRAITEE + counts.EN_COURS}
              />
            </div>
          </div>

          <div className="p-4 md:p-6">
            {tab === 'juridique' && (
              <ClientJuridique
                docs={juridique?.documentsEnVigueur ?? []}
                canDownload={canDownload}
                canPrint={canPrint}
              />
            )}
            {tab === 'comptable' && (
              <DossierComptableTab
                dossierId={dossier.id}
                role="CLIENT"
                canDepot={settings?.permDepot ?? false}
                canDownload={canDownload}
                canPrint={canPrint}
                readOnly={archived}
                readOnlyStatut={dossier.statut}
              />
            )}
            {tab === 'fiscal' && (
              // Lecture seule cote CLIENT : DossierFiscalTab masque upload/delete
              // quand role !== EMPLOYE ; le download suit perm_download (backend).
              // Lot AA : « Voir » toujours actif ; download/print gates par perms.
              <DossierFiscalTab
                dossierId={dossier.id}
                role="CLIENT"
                canDownload={canDownload}
                canPrint={canPrint}
                readOnly={archived}
                readOnlyStatut={dossier.statut}
              />
            )}
            {tab === 'depots' && (
              <ClientDepots dossierId={dossier.id} canDepot={canDepot} />
            )}
            {tab === 'demandes' && (
              <ClientDemandes
                dossierId={dossier.id}
                demandes={demandes}
                onChanged={load}
                readOnly={archived}
                readOnlyStatut={dossier.statut}
              />
            )}
          </div>
        </div>

        <div className="text-center mt-2 text-xs text-fg-subtle">
          <p>
            Espace securise propulse par{' '}
            <strong className="text-fg-subtle">JURIKA</strong> — Plateforme de
            gestion juridique
          </p>
        </div>
      </div>
    </div>
  );
}

function TabButton({
  active,
  onClick,
  color,
  icon,
  label,
  badge,
}: {
  active: boolean;
  onClick: () => void;
  color: string;
  icon: React.ReactNode;
  label: string;
  badge?: number;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="px-4 md:px-6 py-3 md:py-4 text-sm font-medium transition relative flex items-center gap-2 whitespace-nowrap"
      style={{ color: active ? color : '#64748B' }}
    >
      {icon} {label}
      {badge !== undefined && badge > 0 && (
        <span className="px-1.5 py-0.5 text-[10px] font-bold bg-danger text-bg-raised rounded-full">
          {badge}
        </span>
      )}
      {active && (
        <span
          className="absolute bottom-0 left-0 right-0 h-0.5"
          style={{ backgroundColor: color }}
        />
      )}
    </button>
  );
}

// ============================================================
// Juridique (vue client — read-only, current versions only)
// ============================================================

function ClientJuridique({
  docs,
  canDownload,
  canPrint,
}: {
  docs: DocumentSummary[];
  canDownload: boolean;
  canPrint: boolean;
}) {
  return (
    <>
      <div className="bg-success/10 border-l-4 border-success rounded-lg p-3 mb-5 flex items-center gap-2">
        <Shield className="w-4 h-4 text-success flex-shrink-0" />
        <p className="text-xs text-success">
          Vous consultez les{' '}
          <strong>dernieres versions</strong> de vos documents juridiques. Les
          anciennes versions ne sont pas accessibles depuis cet espace.
        </p>
      </div>

      {docs.length === 0 ? (
        <div className="text-center py-12">
          <FileText className="w-10 h-10 text-fg-subtle mx-auto mb-3" />
          <p className="text-sm text-fg-subtle">
            Aucun document juridique disponible pour le moment.
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {docs.map((doc) => (
            <ClientDocRow
              key={doc.id}
              doc={doc}
              canDownload={canDownload}
              canPrint={canPrint}
            />
          ))}
        </div>
      )}

      <div className="text-center mt-6 pt-5 border-t border-border">
        <p className="text-xs text-fg-subtle">
          {docs.length} document{docs.length > 1 ? 's' : ''} juridique
          {docs.length > 1 ? 's' : ''} — Versions actuelles uniquement
        </p>
      </div>
    </>
  );
}

function ClientDocRow({
  doc,
  canDownload,
  canPrint,
}: {
  doc: DocumentSummary;
  canDownload: boolean;
  canPrint: boolean;
}) {
  const [downloading, setDownloading] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const typeKey = String(doc.documentType);
  const label = (DOCUMENT_TYPE_LABELS as Record<string, string>)[typeKey] ?? typeKey;

  async function download() {
    if (!canDownload) return;
    setDownloading(true);
    try {
      await dataroomService.downloadDocument(doc.id, doc.filename);
    } finally {
      setDownloading(false);
    }
  }

  return (
    <div className="flex items-center gap-4 p-3 md:p-4 bg-bg-overlay rounded-lg hover:bg-bg-overlay transition">
      <div className="w-10 h-10 flex items-center justify-center flex-shrink-0">
        <FileText className="w-6 h-6 text-danger" />
      </div>
      <div className="flex-1 min-w-0">
        <h4 className="text-sm font-bold text-fg truncate">
          {doc.title}
        </h4>
        <p className="text-xs text-fg-subtle">{label} • v{doc.version}</p>
        <p className="text-[10px] text-fg-subtle mt-0.5">
          {new Date(doc.createdAt).toLocaleDateString('fr-FR')}
        </p>
      </div>
      <div className="hidden md:flex items-center gap-2 px-3 py-1 bg-success/10 rounded-full flex-shrink-0">
        <Shield className="w-3 h-3 text-success" />
        <span className="text-[10px] font-medium text-success">
          Version actuelle
        </span>
      </div>
      <div className="flex items-center gap-1 flex-shrink-0">
        {/* Lot preview client : le visionnage est un droit inconditionnel
            (backend /preview autorise ROLE_CLIENT, stream inline, hors dossier
            SUSPENDED). Toujours actif, independamment de perm_download. */}
        <button
          type="button"
          onClick={() => setPreviewOpen(true)}
          className="px-3 h-8 text-xs border rounded transition flex items-center gap-1 border-border text-fg hover:border-accent bg-bg-raised"
          title="Voir le document"
        >
          <Eye className="w-3.5 h-3.5" /> Voir
        </button>
        <button
          type="button"
          onClick={download}
          disabled={!canDownload || downloading}
          className={`px-3 h-8 text-xs border rounded transition flex items-center gap-1 ${
            canDownload
              ? 'border-border text-fg hover:border-accent bg-bg-raised'
              : 'border-border text-fg-subtle cursor-not-allowed opacity-50 bg-bg-raised'
          }`}
        >
          {canDownload ? (
            <Download className="w-3.5 h-3.5" />
          ) : (
            <Lock className="w-3.5 h-3.5" />
          )}{' '}
          {downloading ? '...' : 'PDF'}
        </button>
      </div>
      <PdfPreviewModal
        open={previewOpen}
        documentId={previewOpen ? doc.id : null}
        filename={doc.filename}
        canDownload={canDownload}
        canPrint={canPrint}
        onClose={() => setPreviewOpen(false)}
        onDownload={download}
      />
    </div>
  );
}

// ============================================================
// Demandes (vue client — creation + lecture)
// ============================================================

function ClientDemandes({
  dossierId,
  demandes,
  onChanged,
  readOnly = false,
  readOnlyStatut = null,
}: {
  dossierId: string;
  demandes: DemandeSummary[];
  onChanged: () => Promise<void> | void;
  /** §A — societe archivee : plus de nouvelle demande (lecture de l'historique OK). */
  readOnly?: boolean;
  readOnlyStatut?: string | null;
}) {
  const [showForm, setShowForm] = useState(false);
  const [sujet, setSujet] = useState('');
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    // Validation JS in-app AVANT l'appel API (aucune bulle native).
    const sujetErr = requiredMsg(sujet, 'Le sujet est requis.');
    if (sujetErr) {
      setError(sujetErr);
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await dataroomService.createDemande({
        dossierId,
        sujet: sujet.trim(),
        description: description.trim() || undefined,
      });
      setSent(true);
      setSujet('');
      setDescription('');
      await onChanged();
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setSubmitting(false);
    }
  }

  function close() {
    setShowForm(false);
    setSent(false);
    setSujet('');
    setDescription('');
    setError(null);
  }

  return (
    <>
      {/* §A — societe archivee : l'historique reste lisible, la creation part. */}
      {readOnly ? (
        <DataroomReadOnlyHint
          statut={readOnlyStatut}
          testId="client-demandes-readonly-hint"
          className="mb-5"
        />
      ) : (
        <div className="bg-accent/10 border-l-4 border-accent rounded-lg p-3 mb-5 flex items-center gap-2">
          <MessageSquare className="w-4 h-4 text-accent flex-shrink-0" />
          <p className="text-xs text-accent">
            Envoyez vos <strong>demandes juridiques</strong> directement depuis
            cet espace. Votre conseiller les traitera dans les meilleurs delais.
          </p>
        </div>
      )}

      {readOnly ? null : !showForm ? (
        <Button
          variant="primary"
          onClick={() => setShowForm(true)}
          className="w-full bg-accent hover:bg-accent-hover h-11 mb-6"
        >
          <Plus className="w-4 h-4 mr-2" /> Nouvelle demande
        </Button>
      ) : (
        <div className="bg-bg-raised border-2 border-accent/30 rounded-xl p-5 mb-6">
          {sent ? (
            <div className="text-center py-6">
              <div className="w-14 h-14 bg-success/10 rounded-full flex items-center justify-center mx-auto mb-3">
                <CheckCircle className="w-7 h-7 text-success" />
              </div>
              <h4 className="text-base font-bold text-fg mb-1">
                Demande envoyee !
              </h4>
              <p className="text-xs text-fg-subtle mb-4">
                Votre conseiller juridique a ete notifie et traitera votre
                demande dans les meilleurs delais.
              </p>
              <button
                type="button"
                onClick={close}
                className="px-4 h-9 text-sm text-accent hover:bg-accent/10 rounded-lg transition"
              >
                Fermer
              </button>
            </div>
          ) : (
            <form onSubmit={submit} noValidate className="space-y-4">
              <h4 className="text-sm font-bold text-fg flex items-center gap-2">
                <Plus className="w-4 h-4 text-accent" /> Nouvelle demande
              </h4>
              <div>
                <label className="text-xs font-medium text-fg mb-1.5 block">
                  Sujet
                </label>
                <input
                  type="text"
                  value={sujet}
                  onChange={(e) => setSujet(e.target.value)}
                  placeholder="Ex : Modification de l'objet social"
                  className="w-full h-10 border border-border rounded-lg px-3 text-sm text-fg bg-bg-raised focus:border-accent focus:ring-1 focus:ring-[#7C3AED] outline-none"
                  required
                />
              </div>
              <div>
                <label className="text-xs font-medium text-fg mb-1.5 block">
                  Description <span className="font-normal text-fg-subtle">(facultatif)</span>
                </label>
                <textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  rows={3}
                  placeholder="Decrivez votre demande en detail... (facultatif)"
                  className="w-full border border-border rounded-lg px-3 py-2.5 text-sm text-fg resize-none focus:border-accent focus:ring-1 focus:ring-[#7C3AED] outline-none"
                />
              </div>
              {error && (
                <div className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
                  {error}
                </div>
              )}
              <div className="flex items-center gap-3">
                <button
                  type="submit"
                  disabled={!sujet.trim() || submitting}
                  className={`flex items-center gap-2 px-6 h-10 rounded-lg text-sm font-medium transition ${
                    sujet.trim() && !submitting
                      ? 'bg-accent text-bg-raised hover:bg-accent-hover'
                      : 'bg-border text-fg-subtle cursor-not-allowed'
                  }`}
                >
                  <Send className="w-3.5 h-3.5" /> Envoyer
                </button>
                <button
                  type="button"
                  onClick={close}
                  className="px-4 h-10 text-sm text-fg-subtle hover:text-fg transition"
                >
                  Annuler
                </button>
              </div>
            </form>
          )}
        </div>
      )}

      <h4 className="text-sm font-bold text-fg mb-3">
        Historique des demandes
      </h4>
      {demandes.length === 0 ? (
        <div className="text-center py-12">
          <MessageSquare className="w-10 h-10 text-fg-subtle mx-auto mb-3" />
          <p className="text-sm text-fg-subtle">
            Vous n'avez pas encore envoye de demande.
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {demandes.map((dem) => {
            const st = STATUT_PILL[dem.statut];
            return (
              <div
                key={dem.id}
                className="flex items-start gap-4 p-3 md:p-4 bg-bg-overlay rounded-lg hover:bg-bg-overlay transition border border-border"
              >
                <div className="w-10 h-10 flex items-center justify-center flex-shrink-0">
                  {dem.statut === 'NON_TRAITEE' && (
                    <Clock className="w-6 h-6 text-danger" />
                  )}
                  {dem.statut === 'EN_COURS' && (
                    <MessageSquare className="w-6 h-6 text-accent" />
                  )}
                  {dem.statut === 'TRAITEE' && (
                    <CheckCircle className="w-6 h-6 text-success" />
                  )}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1 flex-wrap">
                    <span
                      className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${st.bg} ${st.text}`}
                    >
                      {STATUT_LABEL[dem.statut]}
                    </span>
                    <span className="text-[10px] font-mono text-fg-subtle">
                      {dem.id.slice(0, 8)}
                    </span>
                  </div>
                  <h4 className="text-sm font-bold text-fg mb-0.5">
                    {dem.sujet}
                  </h4>
                  {dem.description && (
                    <p className="text-xs text-fg-subtle whitespace-pre-wrap">
                      {dem.description}
                    </p>
                  )}
                  <p className="text-[10px] text-fg-subtle mt-1">
                    Envoyee le{' '}
                    {new Date(dem.createdAt).toLocaleDateString('fr-FR')}
                    {dem.traiteAt
                      ? ` • Traitee le ${new Date(
                          dem.traiteAt,
                        ).toLocaleDateString('fr-FR')}`
                      : ''}
                  </p>
                </div>
                {dem.statut === 'EN_COURS' && (
                  <div className="hidden md:flex items-center gap-1 px-3 py-1.5 bg-accent/10 rounded-full flex-shrink-0">
                    <span className="w-1.5 h-1.5 bg-accent rounded-full animate-pulse" />
                    <span className="text-[10px] font-medium text-accent">
                      En traitement
                    </span>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      <div className="text-center mt-6 pt-5 border-t border-border">
        <p className="text-xs text-fg-subtle">
          {demandes.length} demande{demandes.length > 1 ? 's' : ''} au total
        </p>
      </div>
    </>
  );
}

// ============================================================
// Depots (vue client — depot libre gate par perm_depot)
// ============================================================

function formatBytes(n: number): string {
  if (!n || n <= 0) return '0 o';
  const units = ['o', 'Ko', 'Mo', 'Go'];
  const i = Math.min(Math.floor(Math.log(n) / Math.log(1024)), units.length - 1);
  return `${(n / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
}

function ClientDepots({
  dossierId,
  canDepot,
}: {
  dossierId: string;
  canDepot: boolean;
}) {
  const [depots, setDepots] = useState<DepotSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [dragOver, setDragOver] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setDepots(await dataroomService.listDepots(dossierId));
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }, [dossierId]);

  useEffect(() => {
    reload();
  }, [reload]);

  const uploadFiles = useCallback(
    async (files: FileList | File[]) => {
      const list = Array.from(files);
      if (list.length === 0 || !canDepot) return;
      setUploading(true);
      setError(null);
      try {
        for (const f of list) {
          await dataroomService.uploadDepot(dossierId, f);
        }
        setToast(
          list.length > 1
            ? `${list.length} fichiers deposes`
            : 'Fichier depose',
        );
        setTimeout(() => setToast(null), 3000);
        await reload();
      } catch (err) {
        setError(extractError(err).message);
      } finally {
        setUploading(false);
      }
    },
    [canDepot, dossierId, reload],
  );

  async function remove(id: string) {
    try {
      await dataroomService.deleteDepot(id);
      await reload();
    } catch (err) {
      setError(extractError(err).message);
    }
  }

  return (
    <>
      {toast && (
        <div className="bg-success/10 border-l-4 border-success rounded-lg p-3 mb-5 flex items-center gap-2">
          <CheckCircle className="w-4 h-4 text-success flex-shrink-0" />
          <p className="text-xs text-success">{toast}</p>
        </div>
      )}

      {canDepot ? (
        <div
          onDragOver={(e) => {
            e.preventDefault();
            setDragOver(true);
          }}
          onDragLeave={() => setDragOver(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDragOver(false);
            if (e.dataTransfer.files?.length) uploadFiles(e.dataTransfer.files);
          }}
          className={`rounded-xl border-2 border-dashed p-6 mb-6 text-center transition ${
            dragOver
              ? 'border-warning bg-warning/10'
              : 'border-border bg-bg-overlay'
          }`}
        >
          <Upload className="w-8 h-8 text-warning mx-auto mb-2" />
          <p className="text-sm font-medium text-fg mb-1">
            Deposez vos fichiers ici
          </p>
          <p className="text-xs text-fg-subtle mb-3">
            PDF, image ou tout autre document (10 Mo max par fichier)
          </p>
          <input
            ref={inputRef}
            type="file"
            multiple
            className="hidden"
            onChange={(e) => {
              if (e.target.files?.length) uploadFiles(e.target.files);
              e.target.value = '';
            }}
          />
          <Button
            variant="primary"
            onClick={() => inputRef.current?.click()}
            disabled={uploading}
            className="bg-warning hover:opacity-90"
          >
            {uploading ? (
              <Loader2 className="w-4 h-4 mr-2 animate-spin" />
            ) : (
              <Upload className="w-4 h-4 mr-2" />
            )}
            {uploading ? 'Depot en cours...' : 'Choisir des fichiers'}
          </Button>
        </div>
      ) : (
        <div className="bg-warning/10 border-l-4 border-warning rounded-lg p-3 mb-5 flex items-center gap-2">
          <Lock className="w-4 h-4 text-warning flex-shrink-0" />
          <p className="text-xs text-warning">
            Le depot de documents n'est pas autorise par votre cabinet.
          </p>
        </div>
      )}

      {error && (
        <div className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger mb-4">
          {error}
        </div>
      )}

      {loading ? (
        <div className="flex justify-center py-10">
          <Loader2 className="h-6 w-6 animate-spin text-warning" />
        </div>
      ) : depots.length === 0 ? (
        <div className="text-center py-12">
          <Upload className="w-10 h-10 text-fg-subtle mx-auto mb-3" />
          <p className="text-sm text-fg-subtle">
            Vous n'avez encore rien depose.
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {depots.map((dep) => (
            <ClientDepotRow key={dep.id} depot={dep} onDelete={remove} />
          ))}
        </div>
      )}

      <div className="text-center mt-6 pt-5 border-t border-border">
        <p className="text-xs text-fg-subtle">
          {depots.length} depot{depots.length > 1 ? 's' : ''}
        </p>
      </div>
    </>
  );
}

function ClientDepotRow({
  depot,
  onDelete,
}: {
  depot: DepotSummary;
  onDelete: (id: string) => void;
}) {
  const [downloading, setDownloading] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);

  async function download() {
    setDownloading(true);
    try {
      await dataroomService.downloadDepot(depot.id, depot.filename);
    } finally {
      setDownloading(false);
    }
  }

  return (
    <div className="flex items-center gap-4 p-3 md:p-4 bg-bg-overlay rounded-lg transition">
      <div className="w-10 h-10 flex items-center justify-center flex-shrink-0">
        <FileText className="w-6 h-6 text-warning" />
      </div>
      <div className="flex-1 min-w-0">
        <h4 className="text-sm font-bold text-fg truncate">{depot.title}</h4>
        <p className="text-[10px] text-fg-subtle mt-0.5">
          {formatBytes(depot.sizeBytes)} •{' '}
          {new Date(depot.createdAt).toLocaleDateString('fr-FR')}
        </p>
      </div>
      <div className="flex items-center gap-1 flex-shrink-0">
        {/* Voir + Telecharger : inconditionnels (c'est le fichier du client). */}
        <button
          type="button"
          onClick={() => setPreviewOpen(true)}
          className="px-3 h-8 text-xs border rounded transition flex items-center gap-1 border-border text-fg hover:border-accent bg-bg-raised"
          title="Voir le document"
        >
          <Eye className="w-3.5 h-3.5" /> Voir
        </button>
        <button
          type="button"
          onClick={download}
          disabled={downloading}
          className="px-3 h-8 text-xs border rounded transition flex items-center gap-1 border-border text-fg hover:border-accent bg-bg-raised disabled:opacity-50"
        >
          <Download className="w-3.5 h-3.5" /> {downloading ? '...' : 'PDF'}
        </button>
        <button
          type="button"
          onClick={() => onDelete(depot.id)}
          className="px-2 h-8 text-xs border rounded transition flex items-center gap-1 border-border text-danger hover:border-danger bg-bg-raised"
          title="Supprimer ce depot"
        >
          <Trash2 className="w-3.5 h-3.5" />
        </button>
      </div>
      <PdfPreviewModal
        open={previewOpen}
        documentId={previewOpen ? depot.id : null}
        filename={depot.filename}
        canDownload
        canPrint
        onClose={() => setPreviewOpen(false)}
        onDownload={download}
        fetchPreview={dataroomService.previewDepot}
      />
    </div>
  );
}
