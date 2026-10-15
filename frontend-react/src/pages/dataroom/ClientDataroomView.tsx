import { useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import {
  CheckCircle,
  Download,
  Eye,
  FileText,
  Lock,
  MessageSquare,
  Plus,
  Printer,
  Receipt,
  Send,
  Trash2,
  Upload,
} from 'lucide-react';
import { Page } from '../../components/layout/Page';
import { Button } from '../../components/ui/Button';
import { Badge, type BadgeVariant } from '../../components/ui/Badge';
import { Card } from '../../components/ui/Card';
import { Lien } from '../../components/ui/Lien';
import { Message } from '../../components/ui/Message';
import { EmptyState } from '../../components/ui/EmptyState';
import { Onglets } from '../../components/ui/Onglets';
import { Skeleton } from '../../components/ui/LoadingSkeleton';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { TextField, ZoneTexte } from '../../components/ui/TextField';
import { ToastContext } from '../../components/ui/useToast';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { PdfPreviewModal } from '../../components/ui/PdfPreviewModal';
import { useEnEssai } from '../../store/essaiStore';
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
import { DataroomReadOnlyHint } from './components/DataroomReadOnlyHint';
import { ClientDebours } from './components/ClientDebours';

type Tab = 'juridique' | 'depots' | 'debours' | 'demandes';

const STATUT_LABEL: Record<DemandeStatut, string> = {
  NON_TRAITEE: 'Non traitée',
  EN_COURS: 'En cours',
  TRAITEE: 'Traitée',
};

const STATUT_VARIANTE: Record<DemandeStatut, BadgeVariant> = {
  NON_TRAITEE: 'warning',
  EN_COURS: 'info',
  TRAITEE: 'success',
};

const ESSAI_TELECHARGEMENT =
  'Téléchargement indisponible pendant l’essai gratuit : le document se consulte en aperçu, avec la mention d’évaluation. Il sera téléchargeable après la souscription d’un forfait.';

/**
 * Portail client — version simplifiee de la Data Room pour les utilisateurs CLIENT.
 * - En-tete commun (Page) : nom de la societe, derniere mise a jour, action principale
 *   « Faire une demande ».
 * - Permissions rappelees en clair ; onglets : Dossier juridique (versions courantes
 *   uniquement), Depots du client, Debours, Demandes du client ; lien vers les requetes
 *   du cabinet (page dediee).
 *
 * Le client ne voit PAS l'historique des tickets clotures (volontairement).
 * Lot L6b (E6) : coquille et bibliotheque communes ; l'espace reste simple.
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
    const allowed: Tab[] = ['juridique', 'depots', 'demandes'];
    return (allowed as string[]).includes(t ?? '') ? (t as Tab) : 'juridique';
  });
  // « Faire une demande » (en-tete) : ouvre l'onglet et son formulaire.
  const [signalDemande, setSignalDemande] = useState(0);

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
    // Chargement par promesse : aucun etat pose de facon synchrone dans l'effet.
    void Promise.resolve().then(load);
  }, [load]);

  // Sprint 7 / TASK 5.3 -- WebSocket : reload du juridique + toast quand
  // l'employe upload/delete un document cote cabinet. Le payload arrive
  // via realtime-service (Socket.io room "dataroom:{dossierId}").
  const toast = useContext(ToastContext);
  const [realtimeToast, setRealtimeToast] = useState<string | null>(null);
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const handleDataroomEvent = useCallback(
    (event: DataroomDocumentEvent) => {
      const verb =
        event.kind === 'UPLOADED'
          ? 'Nouveau document ajouté'
          : event.kind === 'DELETED'
            ? 'Document retiré'
            : 'Document mis à jour';
      const label = event.title ?? event.documentType ?? 'Document';
      const texte = `${verb} : ${label}`;
      if (toast) {
        toast.info(texte, 4000);
      } else {
        setRealtimeToast(texte);
        if (toastTimer.current) clearTimeout(toastTimer.current);
        toastTimer.current = setTimeout(() => setRealtimeToast(null), 4000);
      }
      // reload du juridique (sans bloquer le reste)
      if (dossier) {
        dataroomService
          .getJuridique(dossier.id)
          .then((j) => setJuridique(j))
          .catch(() => {/* silent : best-effort refresh */});
      }
    },
    [dossier, toast],
  );

  useDataroomSocket(dossier?.id, handleDataroomEvent);

  // Decision du 2026-10-14 (RG-ESS-05) : pendant l'essai gratuit du cabinet, ni telechargement
  // ni impression, quelles que soient les permissions ; les documents se consultent en apercu.
  const enEssai = useEnEssai();
  const canDownload = (settings?.permDownload ?? false) && enEssai === false;
  const canPrint = (settings?.permPrint ?? false) && enEssai === false;
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
      <Page titre="Mon espace" fil={false}>
        <div className="space-y-4" aria-busy="true" aria-label="Chargement de votre espace">
          <Card className="p-5">
            <div className="space-y-2">
              <Skeleton className="h-4 w-64" />
              <Skeleton className="h-4 w-40" />
            </div>
          </Card>
          <Card className="p-5">
            <div className="space-y-3">
              <Skeleton className="h-9 w-full" />
              <Skeleton className="h-14 w-full" />
              <Skeleton className="h-14 w-full" />
            </div>
          </Card>
        </div>
      </Page>
    );
  }

  if (error || !dossier) {
    return (
      <Page titre="Mon espace">
        {error ? (
          <Message
            ton="erreur"
            titre="Votre espace n’a pas pu être chargé."
            action={
              <Button type="button" size="sm" variant="secondary" onClick={() => void load()}>
                Réessayer
              </Button>
            }
          >
            {error}
          </Message>
        ) : (
          <Card>
            <EmptyState
              icon={<Lock />}
              title="Aucune Data Room associée à votre compte"
              description="Si votre cabinet vous a invité, contactez votre conseiller : il rattache votre compte au dossier de votre société."
            />
          </Card>
        )}
      </Page>
    );
  }

  const lastUpdate = juridique?.documentsEnVigueur
    .map((d) => new Date(d.createdAt).getTime())
    .reduce((acc, v) => (v > acc ? v : acc), 0);
  const demandesOuvertes = counts.NON_TRAITEE + counts.EN_COURS;

  return (
    <Page
      titre={dossier.raisonSociale}
      description={
        <>
          Dossier géré par votre cabinet juridique ·{' '}
          {lastUpdate
            ? `dernière mise à jour le ${new Date(lastUpdate).toLocaleDateString('fr-FR')}`
            : 'aucun document publié pour le moment'}
        </>
      }
      action={
        archived ? undefined : (
          <Button
            type="button"
            icon={<Send />}
            onClick={() => {
              setTab('demandes');
              setSignalDemande((n) => n + 1);
            }}
          >
            Faire une demande
          </Button>
        )
      }
      aide={
        /* Lot L6 (E9, CDC 15.4) : objet de l'espace et aide. */
        <TexteAide cle="espace-client-dataroom" titre="Votre espace documentaire">
          <p>
            Votre Data Room réunit les documents de votre société. « Dossier juridique » : les
            documents publiés par votre cabinet, dans leur dernière version. « Dépôts du client » :
            les pièces que vous transmettez au cabinet. « Débours » : les frais avancés pour vous.
            « Demandes du client » : vos demandes au cabinet. « Requêtes du cabinet » : les pièces ou
            réponses que votre conseiller attend de vous ; répondez-y en priorité.
          </p>
        </TexteAide>
      }
    >
      <div className="space-y-5">
        {/* Sprint 7 / TASK 5.3 -- avis temps reel (repli sans fournisseur de toasts) */}
        {realtimeToast && (
          <div
            className="fixed bottom-4 right-4 z-50 max-w-sm rounded-lg border border-border bg-bg-raised px-4 py-3 text-sm text-fg shadow-card-lifted"
            role="status"
            aria-live="polite"
          >
            {realtimeToast}
          </div>
        )}

        {/* §A — bandeau archive legale, cote client. */}
        {archived && (
          <DataroomReadOnlyHint
            statut={dossier.statut}
            testId="client-dataroom-readonly-banner"
          />
        )}

        <Card padding={false} className="flex flex-wrap items-center gap-2 px-5 py-3">
          <span className="mr-1 text-sm font-medium text-fg">Vos permissions :</span>
          <Badge variant="success">
            <Eye aria-hidden="true" className="h-3 w-3" /> Visualiser
          </Badge>
          <Badge variant={canDownload ? 'success' : 'neutral'}>
            {canDownload ? <Download aria-hidden="true" className="h-3 w-3" /> : <Lock aria-hidden="true" className="h-3 w-3" />}
            Télécharger{canDownload ? '' : enEssai !== false ? ' (indisponible pendant l’essai gratuit)' : ' (non autorisé)'}
          </Badge>
          <Badge variant={canPrint ? 'success' : 'neutral'}>
            {canPrint ? <Printer aria-hidden="true" className="h-3 w-3" /> : <Lock aria-hidden="true" className="h-3 w-3" />}
            Imprimer{canPrint ? '' : enEssai !== false ? ' (indisponible pendant l’essai gratuit)' : ' (non autorisé)'}
          </Badge>
          <Badge variant={canDepot ? 'success' : 'neutral'}>
            {canDepot ? <Upload aria-hidden="true" className="h-3 w-3" /> : <Lock aria-hidden="true" className="h-3 w-3" />}
            Déposer{canDepot ? '' : ' (non autorisé)'}
          </Badge>
        </Card>

        <div>
          <div className="flex flex-wrap items-end justify-between gap-x-4 gap-y-2">
            <Onglets
              libelle="Sections de votre espace"
              actif={tab}
              onChange={(id) => setTab(id as Tab)}
              className="min-w-0"
              onglets={[
                { id: 'juridique', libelle: <><FileText aria-hidden="true" className="h-4 w-4" />Dossier juridique</> },
                { id: 'depots', libelle: <><Upload aria-hidden="true" className="h-4 w-4" />Dépôts du client</> },
                { id: 'debours', libelle: <><Receipt aria-hidden="true" className="h-4 w-4" />Débours</> },
                {
                  id: 'demandes',
                  libelle: <><MessageSquare aria-hidden="true" className="h-4 w-4" />Demandes du client</>,
                  badge: demandesOuvertes > 0 ? <Badge variant="warning">{demandesOuvertes}</Badge> : undefined,
                },
              ]}
            />
            {/* RG-DR-01 : quatrieme section, les requetes du cabinet ont leur page. */}
            <Lien to="/mes-requetes" className="mb-2.5 inline-flex items-center gap-1.5 text-sm" data-testid="lien-requetes-cabinet">
              <Send aria-hidden="true" className="h-4 w-4" /> Requêtes du cabinet
            </Lien>
          </div>

          <div role="tabpanel" aria-label="Contenu de la section" className="pt-4">
            {tab === 'juridique' && (
              <ClientJuridique
                docs={juridique?.documentsEnVigueur ?? []}
                canDownload={canDownload}
                canPrint={canPrint}
                enEssai={enEssai !== false}
              />
            )}
            {tab === 'depots' && <ClientDepots dossierId={dossier.id} canDepot={canDepot} />}
            {tab === 'debours' && (
              <ClientDebours operations={juridique?.dossiersParTicket ?? []} canDownload={canDownload} />
            )}
            {tab === 'demandes' && (
              <ClientDemandes
                dossierId={dossier.id}
                demandes={demandes}
                counts={counts}
                onChanged={load}
                readOnly={archived}
                readOnlyStatut={dossier.statut}
                signalOuverture={signalDemande}
              />
            )}
          </div>
        </div>

        <p className="text-center text-xs text-fg-muted">
          Espace sécurisé propulsé par <strong className="font-semibold text-fg">JURIKA</strong> — plateforme de
          gestion juridique
        </p>
      </div>
    </Page>
  );
}

// ============================================================
// Juridique (vue client — read-only, current versions only)
// ============================================================

function ClientJuridique({
  docs,
  canDownload,
  canPrint,
  enEssai,
}: {
  docs: DocumentSummary[];
  canDownload: boolean;
  canPrint: boolean;
  enEssai: boolean;
}) {
  return (
    <Card padding={false}>
      <p className="border-b border-border px-5 py-3 text-sm text-fg-muted">
        Vous consultez les <strong className="text-fg">dernières versions</strong> de vos documents juridiques. Les
        anciennes versions ne sont pas accessibles depuis cet espace.
      </p>

      {docs.length === 0 ? (
        <EmptyState
          compact
          icon={<FileText />}
          title="Aucun document juridique pour le moment"
          description="Les documents que votre cabinet publie pour votre société apparaîtront ici, dans leur dernière version."
        />
      ) : (
        <ul className="divide-y divide-border">
          {docs.map((doc) => (
            <ClientDocRow key={doc.id} doc={doc} canDownload={canDownload} canPrint={canPrint} enEssai={enEssai} />
          ))}
        </ul>
      )}

      <p className="border-t border-border px-5 py-3 text-center text-xs text-fg-muted">
        {docs.length} document{docs.length > 1 ? 's' : ''} juridique
        {docs.length > 1 ? 's' : ''} — versions actuelles uniquement
      </p>
    </Card>
  );
}

function ClientDocRow({
  doc,
  canDownload,
  canPrint,
  enEssai,
}: {
  doc: DocumentSummary;
  canDownload: boolean;
  canPrint: boolean;
  enEssai: boolean;
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
    <li className="flex flex-wrap items-center gap-3 px-5 py-3">
      <div aria-hidden="true" className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-bg-overlay">
        <FileText className="h-4 w-4 text-fg-muted" />
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-fg">{doc.title}</p>
        <p className="text-xs text-fg-muted">
          {label} · version {doc.version} · {new Date(doc.createdAt).toLocaleDateString('fr-FR')}
        </p>
      </div>
      <Badge variant="success" className="hidden @md:inline-flex">
        Version actuelle
      </Badge>
      <div className="ml-auto flex shrink-0 items-center gap-1">
        {/* Lot preview client : le visionnage est un droit inconditionnel
            (backend /preview autorise ROLE_CLIENT, stream inline, hors dossier
            SUSPENDED). Toujours actif, independamment de perm_download. */}
        <Button type="button" size="sm" variant="ghost" icon={<Eye />} onClick={() => setPreviewOpen(true)} title="Voir le document">
          Voir
        </Button>
        {/* Lot L6b : un bouton que le serveur refuserait n'est pas affiche (essai : apercu
            seulement ; sans permission : les permissions ci-dessus le disent). */}
        {canDownload ? (
          <Button type="button" size="sm" variant="secondary" icon={<Download />} onClick={download} loading={downloading}>
            Télécharger
          </Button>
        ) : (
          enEssai && <InfoBulle libelle="Téléchargement indisponible" texte={ESSAI_TELECHARGEMENT} />
        )}
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
    </li>
  );
}

// ============================================================
// Demandes (vue client — creation + lecture)
// ============================================================

function ClientDemandes({
  dossierId,
  demandes,
  counts,
  onChanged,
  readOnly = false,
  readOnlyStatut = null,
  signalOuverture = 0,
}: {
  dossierId: string;
  demandes: DemandeSummary[];
  counts: Record<DemandeStatut, number>;
  onChanged: () => Promise<void> | void;
  /** §A — societe archivee : plus de nouvelle demande (lecture de l'historique OK). */
  readOnly?: boolean;
  readOnlyStatut?: string | null;
  /** Change a chaque « Faire une demande » de l'en-tete : le formulaire s'ouvre. */
  signalOuverture?: number;
}) {
  const [showForm, setShowForm] = useState(signalOuverture > 0);
  const [signalVu, setSignalVu] = useState(signalOuverture);
  const [sujet, setSujet] = useState('');
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Ouverture demandee par l'en-tete, ajustee pendant le rendu (pas d'etat pose dans un effet).
  if (signalVu !== signalOuverture) {
    setSignalVu(signalOuverture);
    setShowForm(true);
    setSent(false);
  }

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
    <div className="space-y-4">
      {/* §A — societe archivee : l'historique reste lisible, la creation part. */}
      {readOnly ? (
        <DataroomReadOnlyHint statut={readOnlyStatut} testId="client-demandes-readonly-hint" />
      ) : !showForm ? (
        <Card padding={false} className="flex flex-wrap items-center justify-between gap-3 px-5 py-3">
          <p className="text-sm text-fg-muted">
            Envoyez vos <strong className="text-fg">demandes juridiques</strong> depuis cet espace : votre conseiller les
            traite et vous êtes averti par courriel.
          </p>
          <Button type="button" variant="secondary" icon={<Plus />} onClick={() => setShowForm(true)}>
            Nouvelle demande
          </Button>
        </Card>
      ) : (
        <Card accent title={sent ? undefined : 'Nouvelle demande'}>
          {sent ? (
            <div className="flex flex-col items-center py-4 text-center">
              <CheckCircle aria-hidden="true" className="mb-2 h-8 w-8 text-success" />
              <h3 className="text-base font-semibold text-heading">Demande envoyée</h3>
              <p className="mb-4 mt-1 text-sm text-fg-muted">
                Votre conseiller juridique a été prévenu et traitera votre demande dans les meilleurs délais.
              </p>
              <Button type="button" variant="secondary" onClick={close}>
                Fermer
              </Button>
            </div>
          ) : (
            <form onSubmit={submit} noValidate className="space-y-4">
              <TextField
                label="Sujet"
                value={sujet}
                onChange={(e) => setSujet(e.target.value)}
                placeholder="Exemple : modification de l’objet social"
                required
              />
              <ZoneTexte
                label="Description (facultatif)"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={3}
                placeholder="Décrivez votre demande en détail (facultatif)…"
              />
              {error && <Message ton="erreur">{error}</Message>}
              <div className="flex flex-wrap items-center gap-2">
                <Button type="submit" icon={<Send />} loading={submitting} disabled={!sujet.trim()}>
                  Envoyer
                </Button>
                <Button type="button" variant="ghost" onClick={close}>
                  Annuler
                </Button>
              </div>
            </form>
          )}
        </Card>
      )}

      <Card
        padding={false}
        titleAs="h3"
        title="Historique des demandes"
        actions={
          <div className="flex flex-wrap items-center gap-1.5">
            <Badge variant="warning">{counts.NON_TRAITEE} non traitée{counts.NON_TRAITEE > 1 ? 's' : ''}</Badge>
            <Badge variant="info">{counts.EN_COURS} en cours</Badge>
            <Badge variant="success">{counts.TRAITEE} traitée{counts.TRAITEE > 1 ? 's' : ''}</Badge>
          </div>
        }
      >
        {demandes.length === 0 ? (
          <EmptyState
            compact
            icon={<MessageSquare />}
            title="Vous n’avez pas encore envoyé de demande"
            description="Cliquez sur « Nouvelle demande » pour en adresser une à votre conseiller."
          />
        ) : (
          <ul className="divide-y divide-border">
            {demandes.map((dem) => (
              <li key={dem.id} className="px-5 py-3">
                <div className="mb-1 flex flex-wrap items-center gap-2">
                  <p className="text-sm font-medium text-fg">{dem.sujet}</p>
                  <Badge variant={STATUT_VARIANTE[dem.statut]} dot>
                    {STATUT_LABEL[dem.statut]}
                  </Badge>
                </div>
                {dem.description && (
                  <p className="whitespace-pre-wrap text-sm text-fg-muted">{dem.description}</p>
                )}
                <p className="mt-1 text-xs text-fg-muted">
                  Envoyée le {new Date(dem.createdAt).toLocaleDateString('fr-FR')}
                  {dem.traiteAt ? ` · traitée le ${new Date(dem.traiteAt).toLocaleDateString('fr-FR')}` : ''}
                </p>
              </li>
            ))}
          </ul>
        )}
        <p className="border-t border-border px-5 py-3 text-center text-xs text-fg-muted">
          {demandes.length} demande{demandes.length > 1 ? 's' : ''} au total
        </p>
      </Card>
    </div>
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
  const [avis, setAvis] = useState<string | null>(null);
  const [aSupprimer, setASupprimer] = useState<DepotSummary | null>(null);
  const [suppression, setSuppression] = useState(false);
  const inputRef = useRef<HTMLInputElement | null>(null);
  const toast = useContext(ToastContext);

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
    // Chargement par promesse : aucun etat pose de facon synchrone dans l'effet.
    void Promise.resolve().then(reload);
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
        const texte = list.length > 1 ? `${list.length} fichiers déposés` : 'Fichier déposé';
        if (toast) toast.enregistre(texte);
        else {
          setAvis(texte);
          setTimeout(() => setAvis(null), 3000);
        }
        await reload();
      } catch (err) {
        setError(extractError(err).message);
      } finally {
        setUploading(false);
      }
    },
    [canDepot, dossierId, reload, toast],
  );

  async function remove(id: string) {
    setSuppression(true);
    try {
      await dataroomService.deleteDepot(id);
      setASupprimer(null);
      await reload();
    } catch (err) {
      setASupprimer(null);
      setError(extractError(err).message);
    } finally {
      setSuppression(false);
    }
  }

  return (
    <div className="space-y-4">
      {avis && <Message ton="succes">{avis}</Message>}

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
            if (e.dataTransfer.files?.length) void uploadFiles(e.dataTransfer.files);
          }}
          className={`rounded-xl border-2 border-dashed p-6 text-center transition-colors duration-150 ${
            dragOver ? 'border-accent bg-accent-soft' : 'border-border-input bg-bg-raised'
          }`}
        >
          <Upload aria-hidden="true" className="mx-auto mb-2 h-7 w-7 text-fg-muted" />
          <p className="mb-1 text-sm font-medium text-fg">Déposez vos fichiers ici</p>
          <p className="mb-3 text-xs text-fg-muted">
            PDF, image ou tout autre document (10 Mo au maximum par fichier)
          </p>
          <input
            ref={inputRef}
            type="file"
            multiple
            className="hidden"
            onChange={(e) => {
              if (e.target.files?.length) void uploadFiles(e.target.files);
              e.target.value = '';
            }}
          />
          <Button
            type="button"
            variant="secondary"
            icon={<Upload />}
            onClick={() => inputRef.current?.click()}
            loading={uploading}
          >
            {uploading ? 'Dépôt en cours…' : 'Choisir des fichiers'}
          </Button>
        </div>
      ) : (
        <Message ton="attention">
          Votre cabinet n’a pas ouvert le dépôt de documents sur cet espace. Pour lui transmettre une pièce,
          contactez votre conseiller.
        </Message>
      )}

      {error && <Message ton="erreur">{error}</Message>}

      <Card padding={false} titleAs="h3" title="Vos dépôts">
        {loading ? (
          <div className="space-y-2 px-5 py-4" aria-busy="true" aria-label="Chargement de vos dépôts">
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
          </div>
        ) : depots.length === 0 ? (
          <EmptyState
            compact
            icon={<Upload />}
            title="Vous n’avez encore rien déposé"
            description={
              canDepot
                ? 'Glissez vos fichiers dans le cadre ci-dessus, ou cliquez sur « Choisir des fichiers ».'
                : 'Les pièces que vous déposerez apparaîtront ici.'
            }
          />
        ) : (
          <ul className="divide-y divide-border">
            {depots.map((dep) => (
              <ClientDepotRow key={dep.id} depot={dep} onDelete={() => setASupprimer(dep)} />
            ))}
          </ul>
        )}
        <p className="border-t border-border px-5 py-3 text-center text-xs text-fg-muted">
          {depots.length} dépôt{depots.length > 1 ? 's' : ''}
        </p>
      </Card>

      <ConfirmDialog
        open={aSupprimer !== null}
        onOpenChange={(o) => {
          if (!o) setASupprimer(null);
        }}
        title="Supprimer ce dépôt"
        description={aSupprimer ? `Supprimer « ${aSupprimer.title} » ? Votre cabinet ne le verra plus.` : ''}
        variant="danger"
        confirmLabel="Supprimer"
        loading={suppression}
        onConfirm={() => {
          if (aSupprimer) void remove(aSupprimer.id);
        }}
      />
    </div>
  );
}

function ClientDepotRow({
  depot,
  onDelete,
}: {
  depot: DepotSummary;
  onDelete: () => void;
}) {
  const [downloading, setDownloading] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  // Decision du 2026-10-14 (RG-ESS-05) : aucun telechargement propose pendant l'essai.
  const enEssai = useEnEssai();

  async function download() {
    setDownloading(true);
    try {
      await dataroomService.downloadDepot(depot.id, depot.filename);
    } finally {
      setDownloading(false);
    }
  }

  return (
    <li className="flex flex-wrap items-center gap-3 px-5 py-3">
      <div aria-hidden="true" className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-bg-overlay">
        <FileText className="h-4 w-4 text-fg-muted" />
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-fg">{depot.title}</p>
        <p className="mt-0.5 text-xs text-fg-muted">
          {formatBytes(depot.sizeBytes)} · {new Date(depot.createdAt).toLocaleDateString('fr-FR')}
        </p>
      </div>
      <div className="ml-auto flex shrink-0 items-center gap-1">
        {/* Voir + Telecharger : inconditionnels (c'est le fichier du client), hors essai. */}
        <Button type="button" size="sm" variant="ghost" icon={<Eye />} onClick={() => setPreviewOpen(true)} title="Voir le document">
          Voir
        </Button>
        {enEssai !== false ? (
          <InfoBulle libelle="Téléchargement indisponible" texte={ESSAI_TELECHARGEMENT} />
        ) : (
          <Button type="button" size="sm" variant="secondary" icon={<Download />} onClick={download} loading={downloading}>
            Télécharger
          </Button>
        )}
        <Button
          type="button"
          size="sm"
          variant="ghost"
          iconOnly
          icon={<Trash2 className="text-danger" />}
          onClick={onDelete}
          title="Supprimer ce dépôt"
          aria-label="Supprimer ce dépôt"
        />
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
        fetchApercu={dataroomService.apercuDepot}
      />
    </li>
  );
}
