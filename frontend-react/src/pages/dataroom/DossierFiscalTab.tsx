import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  Building,
  Calendar,
  Download,
  Eye,
  FileCheck,
  Folder,
  Landmark,
  Loader2,
  Lock,
  LockOpen,
  Percent,
  Plus,
  Receipt,
  Trash2,
  Wallet,
} from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { PdfPreviewModal } from '../../components/ui/PdfPreviewModal';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { PromptDialog } from '../../components/ui/PromptDialog';
import { useToast } from '../../components/ui/Toast';
import { dataroomService } from '../../services/dataroom.service';
import { extractError } from '../../lib/api';
import {
  CATEGORIES_FISCALES_ORDER,
  CATEGORIE_CGI_LABELS,
  SOUS_CLASSIFICATION_LABELS,
  type CategorieFiscale,
  type DossierFiscalDetailedView,
  type FiscalDocumentSummary,
} from '../../types/dataroom';
import type { Role } from '../../types/auth';
import { FiscalUploadDrawer } from './FiscalUploadDrawer';
import { EcheancesPanel } from './EcheancesPanel';
import { DataroomReadOnlyHint } from './components/DataroomReadOnlyHint';

interface Props {
  dossierId: string;
  role: Role | null;
  /**
   * Lot AA (2026-07-05) — Permissions transmises au PdfPreviewModal. Defaut true
   * (EMPLOYE). Le bouton « Voir » reste TOUJOURS actif ; le download reste gate
   * par perm_download cote backend.
   */
  canDownload?: boolean;
  canPrint?: boolean;
  /**
   * Lot DIVERS §A (2026-08-13) — societe dissoute / liquidee / radiee : archive
   * legale en lecture seule (RG-DF18). Neutralise depot, suppression ET ouverture
   * d'un nouvel exercice (donc plus aucune nouvelle echeance DGI). Les echeances
   * deja planifiees ne sont plus depilees par le scheduler.
   */
  readOnly?: boolean;
  /** Statut de la societe, pour le libelle de l'encart. */
  readOnlyStatut?: string | null;
}

const CATEGORY_ICONS: Record<CategorieFiscale, typeof Receipt> = {
  TVA: Receipt,
  IS: Building,
  IR: Wallet,
  TP_TSC: Landmark,
  RAS: Percent,
  ATTESTATIONS: FileCheck,
  CONTENTIEUX: AlertTriangle,
  // Prompt G (2026-06-23) — fourre-tout import.
  AUTRE: Folder,
};

export function DossierFiscalTab({
  dossierId,
  role,
  canDownload = true,
  canPrint = true,
  readOnly = false,
  readOnlyStatut = null,
}: Props) {
  const toast = useToast();
  const [view, setView] = useState<DossierFiscalDetailedView | null>(null);
  const [loading, setLoading] = useState(false);
  // Lot AA — document en cours d'apercu (null = modal fermee).
  const [previewDoc, setPreviewDoc] = useState<FiscalDocumentSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selectedExerciceId, setSelectedExerciceId] = useState<string | undefined>(undefined);
  const [activeCategory, setActiveCategory] = useState<CategorieFiscale | null>(null);
  const [documents, setDocuments] = useState<FiscalDocumentSummary[]>([]);
  const [docsLoading, setDocsLoading] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);

  // --- Dialogs stylés (remplacent window.confirm / window.prompt / alert) ---
  const [cloturerOpen, setCloturerOpen] = useState(false);
  const [verrouillerOpen, setVerrouillerOpen] = useState(false);
  const [deverrouillerOpen, setDeverrouillerOpen] = useState(false);
  const [deleteDocId, setDeleteDocId] = useState<string | null>(null);
  const [actionBusy, setActionBusy] = useState(false);
  // Flux « nouvel exercice » (prompt annee -> confirm comptable manquant ->
  // prompt regime TVA -> ouverture). Chaque etape porte ses donnees.
  const [anneePromptOpen, setAnneePromptOpen] = useState(false);
  const [comptableConfirm, setComptableConfirm] = useState<{ annee: number; dispo: string } | null>(null);
  const [regimePrompt, setRegimePrompt] = useState<{ annee: number; autoCreateComptable: boolean } | null>(null);
  const [exerciceBusy, setExerciceBusy] = useState(false);

  // §A — societe archivee : plus d'ouverture d'exercice (donc plus de generation
  // d'echeances DGI), plus de depot ni de suppression.
  const canManageExercice = role === 'EMPLOYE' && !readOnly;
  // 2026-06-30 — Dossier Fiscal en LECTURE SEULE pour le CLIENT (list + download).
  // Seul l'EMPLOYE depose/supprime du fiscal (aligne sur le backend : POST/DELETE
  // fiscal interdits au CLIENT).
  const canUpload = role === 'EMPLOYE' && !readOnly;

  const load = useCallback(
    async (exerciceId?: string) => {
      setLoading(true);
      setError(null);
      try {
        const data = await dataroomService.getFiscalDetail(dossierId, exerciceId);
        setView(data);
        if (!exerciceId && data.exerciceCourant) {
          setSelectedExerciceId(data.exerciceCourant);
        }
      } catch (err) {
        setError(extractError(err).message);
      } finally {
        setLoading(false);
      }
    },
    [dossierId],
  );

  useEffect(() => {
    load();
  }, [load]);

  const currentExercice = useMemo(() => {
    if (!view) return null;
    const targetId = selectedExerciceId ?? view.exerciceCourant;
    return view.exercices.find((e) => e.id === targetId) ?? view.exercices[0] ?? null;
  }, [view, selectedExerciceId]);

  const loadDocuments = useCallback(
    async (categorie: CategorieFiscale) => {
      if (!currentExercice) return;
      setDocsLoading(true);
      try {
        const docs = await dataroomService.listFiscalDocuments(
          dossierId,
          currentExercice.id,
          categorie,
        );
        setDocuments(docs);
      } catch (err) {
        setError(extractError(err).message);
      } finally {
        setDocsLoading(false);
      }
    },
    [dossierId, currentExercice],
  );

  useEffect(() => {
    if (activeCategory) loadDocuments(activeCategory);
  }, [activeCategory, loadDocuments]);

  async function handleExerciceChange(e: React.ChangeEvent<HTMLSelectElement>) {
    const next = e.target.value || undefined;
    setSelectedExerciceId(next);
    setActiveCategory(null);
    setDocuments([]);
    await load(next);
  }

  async function confirmCloturer() {
    if (!currentExercice) return;
    setActionBusy(true);
    try {
      await dataroomService.cloturerExercice(currentExercice.id);
      setCloturerOpen(false);
      await load(currentExercice.id);
    } catch (err) {
      toast.error(extractError(err).message);
    } finally {
      setActionBusy(false);
    }
  }

  async function confirmVerrouiller() {
    if (!currentExercice) return;
    setActionBusy(true);
    try {
      await dataroomService.verrouillerExercice(currentExercice.id);
      setVerrouillerOpen(false);
      await load(currentExercice.id);
    } catch (err) {
      toast.error(extractError(err).message);
    } finally {
      setActionBusy(false);
    }
  }

  async function confirmDeverrouiller(motif: string) {
    if (!currentExercice) return;
    setActionBusy(true);
    try {
      await dataroomService.deverrouillerExercice(currentExercice.id, { motif });
      setDeverrouillerOpen(false);
      await load(currentExercice.id);
    } catch (err) {
      toast.error(extractError(err).message);
    } finally {
      setActionBusy(false);
    }
  }

  /**
   * RG-DF03 (2026-06-24) — Le COMPTABLE est la timeline maitresse. Avant
   * d'ouvrir un exercice fiscal, on verifie que l'annee est tenue en comptabilite.
   * - Si oui : ouverture conforme (le backend reprend les dates de l'exercice
   *   comptable et genere les echeances DGI).
   * - Si non : on NE cree PAS un fiscal orphelin. On propose de creer d'abord
   *   l'annee comptable Y (dates alignees) PUIS l'exercice fiscal, en une seule
   *   action conforme (autoCreateComptable).
   */
  // Etape 1 -> 2 : l'annee saisie est validee (integer 2000-2100 via le
  // PromptDialog), puis on recupere les annees comptables (timeline maitresse).
  // Si l'annee n'est pas tenue en comptabilite -> confirmation autoCreate ;
  // sinon on passe directement au choix du regime TVA.
  async function submitNewExerciceAnnee(anneeStr: string) {
    const annee = Number(anneeStr);
    setExerciceBusy(true);
    let anneesComptables: number[] = [];
    try {
      const comptable = await dataroomService.getComptable(dossierId);
      anneesComptables = comptable.annees ?? [];
    } catch (err) {
      toast.error(extractError(err).message);
      setExerciceBusy(false);
      setAnneePromptOpen(false);
      return;
    }
    setExerciceBusy(false);
    setAnneePromptOpen(false);
    if (!anneesComptables.includes(annee)) {
      const dispo = anneesComptables.length > 0 ? anneesComptables.join(', ') : 'aucune';
      setComptableConfirm({ annee, dispo });
    } else {
      setRegimePrompt({ annee, autoCreateComptable: false });
    }
  }

  // Etape 3 : ouverture effective de l'exercice avec le regime TVA choisi.
  async function submitNewExerciceRegime(regimeStr: string) {
    if (!regimePrompt) return;
    const regimeTvaMensuel = (regimeStr || 'M').toUpperCase().startsWith('M');
    const { annee, autoCreateComptable } = regimePrompt;
    setExerciceBusy(true);
    try {
      const created = await dataroomService.openExercice(dossierId, {
        annee,
        regimeTvaMensuel,
        autoCreateComptable,
      });
      setRegimePrompt(null);
      await load(created.id);
    } catch (err) {
      toast.error(extractError(err).message);
    } finally {
      setExerciceBusy(false);
    }
  }

  async function confirmDeleteDoc() {
    if (!deleteDocId) return;
    setActionBusy(true);
    try {
      await dataroomService.deleteFiscal(deleteDocId);
      setDeleteDocId(null);
      if (activeCategory) await loadDocuments(activeCategory);
      if (currentExercice) await load(currentExercice.id);
    } catch (err) {
      toast.error(extractError(err).message);
    } finally {
      setActionBusy(false);
    }
  }

  async function handleExportZip() {
    if (!currentExercice) return;
    try {
      await dataroomService.exportFiscalExerciceZip(dossierId, currentExercice.id);
    } catch (err) {
      toast.error(extractError(err).message);
    }
  }

  if (loading && !view) {
    return (
      <Card className="flex h-48 items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-warning" />
      </Card>
    );
  }

  if (error) {
    return (
      <Card className="p-4">
        <p className="text-sm text-danger">{error}</p>
      </Card>
    );
  }

  if (!view) return null;

  const statutColor =
    currentExercice?.statut === 'OUVERT'
      ? 'bg-emerald-100 text-emerald-700'
      : currentExercice?.statut === 'CLOTURE'
        ? 'bg-amber-100 text-amber-700'
        : 'bg-danger/20 text-danger';

  const compteursMap = new Map(view.compteurs.map((c) => [c.categorie, c.total]));
  const isLocked = currentExercice?.statut === 'VERROUILLE';

  return (
    <div className="space-y-4">
      {readOnly && (
        <DataroomReadOnlyHint
          statut={readOnlyStatut}
          testId="fiscal-readonly-hint"
        />
      )}
      {/* Header : selecteur exercice + actions */}
      <Card>
        <header className="flex flex-col gap-3 border-b border-border px-5 py-3 md:flex-row md:items-center md:justify-between">
          <div className="flex items-center gap-2">
            <Landmark className="h-4 w-4 text-warning" />
            <h3 className="text-sm font-semibold text-fg">Dossier Fiscal</h3>
            {currentExercice && (
              <span className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${statutColor}`}>
                {currentExercice.statut}
              </span>
            )}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Calendar className="h-4 w-4 text-fg-subtle" />
            <select
              value={selectedExerciceId ?? view.exerciceCourant ?? ''}
              onChange={handleExerciceChange}
              className="w-44 rounded-lg border border-border-hi bg-bg-raised px-3 py-2 text-sm text-fg focus:border-amber-500 focus:outline-none focus:ring-2 focus:ring-amber-200"
            >
              {view.exercices.length === 0 && <option value="">Aucun exercice</option>}
              {view.exercices.map((ex) => (
                <option key={ex.id} value={ex.id}>
                  Exercice {ex.annee} ({ex.statut})
                </option>
              ))}
            </select>
            {canManageExercice && (
              <>
                <button
                  type="button"
                  onClick={() => setAnneePromptOpen(true)}
                  className="inline-flex items-center gap-1 rounded-lg border border-border-hi bg-bg-raised px-3 py-2 text-xs font-medium text-fg-muted hover:bg-bg-overlay"
                >
                  <Plus className="h-3.5 w-3.5" /> Nouvel exercice
                </button>
                {currentExercice?.statut === 'OUVERT' && (
                  <button
                    type="button"
                    onClick={() => setCloturerOpen(true)}
                    className="rounded-lg border border-amber-300 bg-warning/10 px-3 py-2 text-xs font-medium text-warning hover:bg-amber-100"
                  >
                    Cloturer
                  </button>
                )}
                {(currentExercice?.statut === 'OUVERT' ||
                  currentExercice?.statut === 'CLOTURE') && (
                  <button
                    type="button"
                    onClick={() => setVerrouillerOpen(true)}
                    className="inline-flex items-center gap-1 rounded-lg border border-rose-300 bg-danger/10 px-3 py-2 text-xs font-medium text-rose-800 hover:bg-danger/20"
                  >
                    <Lock className="h-3.5 w-3.5" /> Verrouiller
                  </button>
                )}
              </>
            )}
            {role === 'SUPERVISEUR' && isLocked && (
              <button
                type="button"
                onClick={() => setDeverrouillerOpen(true)}
                className="inline-flex items-center gap-1 rounded-lg border border-border-hi bg-bg-raised px-3 py-2 text-xs font-medium text-fg-muted hover:bg-bg-overlay"
              >
                <LockOpen className="h-3.5 w-3.5" /> Deverrouiller
              </button>
            )}
            {currentExercice && (
              <button
                type="button"
                onClick={handleExportZip}
                className="inline-flex items-center gap-1 rounded-lg border border-border-hi bg-bg-raised px-3 py-2 text-xs font-medium text-fg-muted hover:bg-bg-overlay"
              >
                <Download className="h-3.5 w-3.5" /> Export ZIP
              </button>
            )}
          </div>
        </header>
        {currentExercice && (
          <div className="px-5 py-3 text-xs text-fg-subtle">
            Exercice {currentExercice.annee} : {currentExercice.dateDebut} → {currentExercice.dateFin}
            {currentExercice.dateCloture && (
              <> · cloture le {new Date(currentExercice.dateCloture).toLocaleDateString('fr-FR')}</>
            )}
            {isLocked && <> · upload interdit (controle fiscal DGI)</>}
          </div>
        )}
      </Card>

      {/* Grille 7 categories CGI */}
      <Card>
        <header className="border-b border-border px-5 py-3">
          <h4 className="text-sm font-semibold text-fg">7 categories CGI Maroc</h4>
          <p className="text-xs text-fg-subtle">CGI Art. 211 — retention 10 ans</p>
        </header>
        <div className="grid grid-cols-1 gap-3 p-5 md:grid-cols-2 lg:grid-cols-3">
          {CATEGORIES_FISCALES_ORDER.filter((c) => c !== 'CONTENTIEUX').map((cat) => {
            const Icon = CATEGORY_ICONS[cat];
            const count = compteursMap.get(cat) ?? 0;
            const isActive = activeCategory === cat;
            return (
              <button
                key={cat}
                type="button"
                onClick={() => setActiveCategory(isActive ? null : cat)}
                className={`flex items-start gap-3 rounded-lg border px-4 py-3 text-left transition ${
                  isActive
                    ? 'border-amber-400 bg-warning/10'
                    : 'border-border bg-bg-raised hover:border-amber-300 hover:bg-warning/10/50'
                }`}
              >
                <div className="rounded-lg bg-bg-overlay p-2">
                  <Icon className="h-4 w-4 text-fg-muted" />
                </div>
                <div className="flex-1">
                  <p className="text-sm font-medium text-fg">
                    {CATEGORIE_CGI_LABELS[cat] ?? cat}
                  </p>
                  <p className="text-[10px] uppercase tracking-wider text-fg-subtle">{cat}</p>
                </div>
                <span className="rounded-full bg-bg-overlay px-2 py-0.5 text-[10px] font-bold text-fg-muted">
                  {count}
                </span>
              </button>
            );
          })}
        </div>
        {/* CONTENTIEUX en pleine largeur (fond rouge leger) */}
        <div className="border-t border-danger/40 bg-danger/10/50 p-5">
          <button
            type="button"
            onClick={() =>
              setActiveCategory(activeCategory === 'CONTENTIEUX' ? null : 'CONTENTIEUX')
            }
            className={`flex w-full items-center gap-3 rounded-lg border px-4 py-3 text-left transition ${
              activeCategory === 'CONTENTIEUX'
                ? 'border-rose-400 bg-danger/20'
                : 'border-rose-300 bg-bg-raised hover:bg-danger/10'
            }`}
          >
            <div className="rounded-lg bg-danger/20 p-2">
              <AlertTriangle className="h-4 w-4 text-danger" />
            </div>
            <div className="flex-1">
              <p className="text-sm font-semibold text-rose-900">Contentieux fiscal</p>
              <p className="text-xs text-danger">
                CGI Art. 220-242 — non supprimable tant que l’exercice n’est pas CLOTURE
                (RG-DF12). Upload reserve aux employes du cabinet (RG-DF04).
              </p>
            </div>
            <span className="rounded-full bg-rose-200 px-2 py-0.5 text-[10px] font-bold text-rose-800">
              {compteursMap.get('CONTENTIEUX') ?? 0}
            </span>
          </button>
        </div>
      </Card>

      {/* Liste documents categorie active */}
      {activeCategory && currentExercice && (
        <Card>
          <header className="flex items-center justify-between border-b border-border px-5 py-3">
            <h4 className="text-sm font-semibold text-fg">
              {CATEGORIE_CGI_LABELS[activeCategory] ?? activeCategory} — documents
            </h4>
            {canUpload && !isLocked && (
              <button
                type="button"
                onClick={() => setUploadOpen(true)}
                className="inline-flex items-center gap-1 rounded-lg bg-warning px-3 py-2 text-xs font-semibold text-bg-raised hover:bg-warning/85"
              >
                <Plus className="h-3.5 w-3.5" /> Ajouter
              </button>
            )}
          </header>
          <div className="p-5">
            {docsLoading ? (
              <Loader2 className="h-5 w-5 animate-spin text-fg-subtle" />
            ) : documents.length === 0 ? (
              <p className="text-sm text-fg-subtle">Aucun document.</p>
            ) : (
              <ul className="divide-y divide-border">
                {documents.map((d) => (
                  <li key={d.id} className="flex items-center justify-between gap-3 py-3">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-fg">{d.title}</p>
                      <p className="text-xs text-fg-subtle">
                        {SOUS_CLASSIFICATION_LABELS[d.sousClassification] ?? d.sousClassification}
                        {d.periodeDeclaree && ` · ${d.periodeDeclaree}`}
                        {d.comptableDocSourceTitle && ` · 🔗 ${d.comptableDocSourceTitle}`}
                      </p>
                    </div>
                    <div className="flex items-center gap-2">
                      {/* Lot AA — « Voir » TOUJOURS actif (visionnage inconditionnel). */}
                      <button
                        type="button"
                        onClick={() => setPreviewDoc(d)}
                        className="rounded-lg p-1.5 text-fg-subtle hover:bg-bg-overlay hover:text-fg-muted"
                        aria-label="Voir"
                        title="Voir"
                      >
                        <Eye className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() => dataroomService.downloadFiscal(d.id, d.filename)}
                        className="rounded-lg p-1.5 text-fg-subtle hover:bg-bg-overlay hover:text-fg-muted"
                        aria-label="Telecharger"
                        title="Telecharger"
                      >
                        <Download className="h-4 w-4" />
                      </button>
                      {canUpload && d.categorie !== 'CONTENTIEUX' && (
                        <button
                          type="button"
                          onClick={() => setDeleteDocId(d.id)}
                          className="rounded-lg p-1.5 text-danger hover:bg-danger/10"
                          title="Supprimer (CGI Art. 211: conserve 10 ans)"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </Card>
      )}

      {/* Panneau echeances */}
      <EcheancesPanel
        dossierId={dossierId}
        canManage={role === 'EMPLOYE' && !readOnly}
        suspended={readOnly}
      />

      {uploadOpen && activeCategory && currentExercice && (
        <FiscalUploadDrawer
          dossierId={dossierId}
          exerciceId={currentExercice.id}
          initialCategorie={activeCategory}
          role={role}
          onClose={() => setUploadOpen(false)}
          onUploaded={async () => {
            setUploadOpen(false);
            if (activeCategory) await loadDocuments(activeCategory);
            if (currentExercice) await load(currentExercice.id);
          }}
        />
      )}

      {/* Lot AA — Apercu inline (PDF ou image) du document fiscal. */}
      {previewDoc && (
        <PdfPreviewModal
          open
          documentId={previewDoc.id}
          filename={previewDoc.filename}
          canDownload={canDownload}
          canPrint={canPrint}
          onClose={() => setPreviewDoc(null)}
          onDownload={() => dataroomService.downloadFiscal(previewDoc.id, previewDoc.filename)}
          fetchPreview={dataroomService.previewFiscal}
        />
      )}

      {/* Cloture d'exercice */}
      <ConfirmDialog
        open={cloturerOpen}
        onOpenChange={(o) => !o && setCloturerOpen(false)}
        title="Cloturer l'exercice"
        description={`Cloturer l'exercice ${currentExercice?.annee} ?`}
        confirmLabel="Cloturer"
        loading={actionBusy}
        onConfirm={confirmCloturer}
      />

      {/* Verrouillage (controle fiscal DGI) */}
      <ConfirmDialog
        open={verrouillerOpen}
        onOpenChange={(o) => !o && setVerrouillerOpen(false)}
        title="Verrouiller l'exercice"
        description={`Verrouiller l'exercice ${currentExercice?.annee} (controle fiscal DGI) ?`}
        variant="danger"
        confirmLabel="Verrouiller"
        loading={actionBusy}
        onConfirm={confirmVerrouiller}
      />

      {/* Deverrouillage : motif >= 20 caracteres (RG-DF26) */}
      <PromptDialog
        open={deverrouillerOpen}
        onOpenChange={(o) => !o && setDeverrouillerOpen(false)}
        title="Deverrouiller l'exercice"
        label="Motif de deverrouillage (20 caracteres minimum, RG-DF26) :"
        placeholder="Justification du deverrouillage…"
        multiline
        confirmLabel="Deverrouiller"
        loading={actionBusy}
        validate={(v) =>
          !v || v.trim().length < 20 ? 'Motif manquant ou trop court.' : null
        }
        onConfirm={confirmDeverrouiller}
      />

      {/* Suppression d'un document fiscal */}
      <ConfirmDialog
        open={!!deleteDocId}
        onOpenChange={(o) => !o && setDeleteDocId(null)}
        title="Supprimer le document fiscal"
        description="Supprimer ce document fiscal ? (CGI Art. 211 -- fichier conserve 10 ans)"
        variant="danger"
        confirmLabel="Supprimer"
        loading={actionBusy}
        onConfirm={confirmDeleteDoc}
      />

      {/* Nouvel exercice — etape 1 : annee */}
      <PromptDialog
        open={anneePromptOpen}
        onOpenChange={(o) => !o && setAnneePromptOpen(false)}
        title="Nouvel exercice fiscal"
        label="Annee du nouvel exercice (ex: 2027) :"
        placeholder="2027"
        confirmLabel="Continuer"
        loading={exerciceBusy}
        validate={(v) => {
          const n = Number(v);
          return !Number.isInteger(n) || n < 2000 || n > 2100 ? 'Annee invalide' : null;
        }}
        onConfirm={submitNewExerciceAnnee}
      />

      {/* Nouvel exercice — etape 2 : annee comptable manquante */}
      <ConfirmDialog
        open={!!comptableConfirm}
        onOpenChange={(o) => !o && setComptableConfirm(null)}
        title="Annee non tenue en comptabilite"
        description={
          comptableConfirm
            ? `L'annee ${comptableConfirm.annee} n'est pas tenue en comptabilite (annees comptables : ${comptableConfirm.dispo}). Un exercice fiscal doit etre conforme au comptable. Voulez-vous creer l'annee comptable ${comptableConfirm.annee} puis ouvrir l'exercice fiscal (dates alignees) ?`
            : ''
        }
        confirmLabel="Creer et continuer"
        onConfirm={() => {
          if (!comptableConfirm) return;
          const annee = comptableConfirm.annee;
          setComptableConfirm(null);
          setRegimePrompt({ annee, autoCreateComptable: true });
        }}
      />

      {/* Nouvel exercice — etape 3 : regime TVA */}
      <PromptDialog
        open={!!regimePrompt}
        onOpenChange={(o) => !o && setRegimePrompt(null)}
        title="Regime TVA"
        label="Regime TVA (M = mensuelle, T = trimestrielle) ?"
        placeholder="M ou T"
        defaultValue="M"
        confirmLabel="Ouvrir l'exercice"
        loading={exerciceBusy}
        onConfirm={submitNewExerciceRegime}
      />
    </div>
  );
}
