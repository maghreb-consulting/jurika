import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Download,
  Eye,
  FileDown,
  FileText,
  Filter,
  ChevronDown,
  ChevronRight,
  History,
  IdCard,
  Loader2,
  Lock,
  Search,
  Trash2,
  Upload,
  X,
} from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { Highlight } from '../../components/ui/Highlight';
import { PdfPreviewModal } from '../../components/ui/PdfPreviewModal';
import { dataroomService } from '../../services/dataroom.service';
import { extractError } from '../../lib/api';
import {
  archivedStatutLabel,
  dataroomReadOnlyReason,
} from '../../lib/dossierArchive';
import type {
  DocumentSummary,
  DossierJuridiqueView,
  DossierTicket,
  GroupeDocuments,
} from '../../types/dataroom';
import { DOCUMENT_TYPE_LABELS } from '../../types/dataroom';

import type { Role } from '../../types/auth';
import { STATUT_LABELS_COURTS } from '../../types/ticket';
import type { TicketStatut } from '../../types/ticket';
import {
  DEFAULT_FILTERS,
  JuridiqueSearchBar,
  filtersToApiParams,
  type SearchFilters,
} from './components/JuridiqueSearchBar';
import { OperationsTimeline } from './components/OperationsTimeline';
import {
  countActiveTimelineFilters,
  DEFAULT_TIMELINE_FILTERS,
  TimelineFiltersDrawer,
  timelineFiltersToApiParams,
  type TimelineFilters,
} from './components/TimelineFiltersDrawer';
import { UploadDocumentDialog } from './components/UploadDocumentDialog';
import { MultiUploadDrawer } from './components/MultiUploadDrawer';
import { DocumentVersionsDropdown } from './components/DocumentVersionsDropdown';
import { IdentifiantsDrawer } from './components/IdentifiantsDrawer';
import { FichePreflightModal } from './components/FichePreflightModal';
import { computeMissingIdentity } from './components/ficheClientPreflight';


interface Props {
  dossierId: string;
  role: Role | null;
  /**
   * Lot DIVERS §A (2026-08-13) — societe dissoute / liquidee / radiee : Data Room
   * en lecture seule. Neutralise upload / versioning / suppression. Le backend
   * (`DossierArchiveGuard`) reste l'autorite : ce drapeau evite juste de proposer
   * une action qui serait refusee.
   */
  readOnly?: boolean;
  /** Statut de la societe, pour le libelle de la raison (tooltip / encart). */
  readOnlyStatut?: string | null;
}

export function DossierJuridiqueTab({
  dossierId,
  role,
  readOnly = false,
  readOnlyStatut = null,
}: Props) {
  const [view, setView] = useState<DossierJuridiqueView | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [uploadOpen, setUploadOpen] = useState(false);
  // Sprint 2026-06-23 — nouvelle modale d'upload avec versioning explicite.
  const [versionedDialogOpen, setVersionedDialogOpen] = useState(false);

  // Sprint 7 / TASK 1.5 -- recherche FTS + filtres avances
  const [filters, setFilters] = useState<SearchFilters>(DEFAULT_FILTERS);
  const [searchResults, setSearchResults] = useState<DocumentSummary[] | null>(
    null,
  );
  const [searchTotal, setSearchTotal] = useState<number>(0);
  const [searching, setSearching] = useState(false);

  // Sprint 7 / TASK 2 -- timeline historique : filtres types/periode
  const [timelineFilters, setTimelineFilters] = useState<TimelineFilters>(
    DEFAULT_TIMELINE_FILTERS,
  );
  const [timelineFiltersOpen, setTimelineFiltersOpen] = useState(false);
  const timelineFiltersCount = countActiveTimelineFilters(timelineFilters);

  // Sprint 7 / TASK 4 -- bulk selection + export PDF historique
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [bulkBusy, setBulkBusy] = useState(false);
  // Confirmation de suppression groupee (remplace confirm() natif).
  const [bulkDeleteOpen, setBulkDeleteOpen] = useState(false);

  function toggleSelect(id: string) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function clearSelection() {
    setSelectedIds(new Set());
  }

  async function confirmBulkDelete() {
    if (selectedIds.size === 0) return;
    setBulkBusy(true);
    try {
      await dataroomService.deleteJuridiqueBulk(Array.from(selectedIds));
      setBulkDeleteOpen(false);
      clearSelection();
      await load();
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setBulkBusy(false);
    }
  }

  async function bulkExportZip() {
    if (selectedIds.size === 0) return;
    setBulkBusy(true);
    try {
      await dataroomService.exportJuridiqueZip(
        dossierId,
        Array.from(selectedIds),
        false,
      );
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setBulkBusy(false);
    }
  }

  // Fiche client (2026-07-14) — remplace « Exporter rapport PDF ».
  const [fichePreflightOpen, setFichePreflightOpen] = useState(false);
  const [identifiantsOpen, setIdentifiantsOpen] = useState(false);
  const [ficheBusy, setFicheBusy] = useState(false);

  const canFiche =
    role === 'EMPLOYE' || role === 'SUPERVISEUR' || role === 'SUPER_ADMIN';
  const missingIdentity = view ? computeMissingIdentity(view) : [];

  async function generateFiche() {
    if (!view) return;
    setFicheBusy(true);
    try {
      await dataroomService.exportFicheClientPdf(dossierId, view.raisonSociale);
      setFichePreflightOpen(false);
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setFicheBusy(false);
    }
  }

  function onFicheClick() {
    if (!view) return;
    if (missingIdentity.length > 0) {
      setFichePreflightOpen(true);
    } else {
      void generateFiche();
    }
  }

  const isSearchActive = useMemo(() => {
    return (
      filters.q.trim() !== '' ||
      filters.types.length > 0 ||
      filters.from !== '' ||
      filters.to !== '' ||
      filters.versionScope !== 'CURRENT'
    );
  }, [filters]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await dataroomService.getJuridique(
        dossierId,
        timelineFiltersToApiParams(timelineFilters),
      );
      setView(data);
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }, [dossierId, timelineFilters]);

  useEffect(() => {
    load();
  }, [load]);

  // Effet recherche : se declenche a chaque changement filtres si recherche active.
  useEffect(() => {
    if (!isSearchActive) {
      setSearchResults(null);
      setSearchTotal(0);
      return;
    }
    const params = filtersToApiParams(filters);
    let cancelled = false;
    setSearching(true);
    dataroomService
      .searchJuridique(dossierId, params)
      .then((res) => {
        if (cancelled) return;
        setSearchResults(res.items);
        setSearchTotal(res.total);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(extractError(err).message);
        setSearchResults([]);
        setSearchTotal(0);
      })
      .finally(() => {
        if (!cancelled) setSearching(false);
      });
    return () => {
      cancelled = true;
    };
  }, [dossierId, filters, isSearchActive]);

  // §A — la lecture seule prime sur le role : meme un EMPLOYE ne peut plus
  // deposer, versionner ni supprimer sur une societe archivee.
  const canUpload = role === 'EMPLOYE' && !readOnly;

  if (loading && !view) {
    return (
      <Card className="flex h-48 items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-border border-t-indigo-600" />
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

  return (
    <div className="space-y-4">
      <Card className="space-y-3 p-4">
        <JuridiqueSearchBar
          filters={filters}
          onChange={setFilters}
          totalResults={searchTotal}
          loading={searching}
        />
      </Card>

      {/* Sprint 7 / TASK 4 -- toolbar contextuelle bulk actions */}
      {selectedIds.size > 0 && (
        <div className="sticky top-0 z-10 flex items-center justify-between rounded-xl border-2 border-accent/40 bg-accent/10 px-4 py-2.5 shadow-sm">
          <div className="flex items-center gap-2">
            <span className="inline-flex h-6 min-w-[24px] items-center justify-center rounded-full bg-accent px-1.5 text-xs font-bold text-bg-raised">
              {selectedIds.size}
            </span>
            <span className="text-sm font-medium text-accent">
              document{selectedIds.size > 1 ? 's' : ''} selectionne{selectedIds.size > 1 ? 's' : ''}
            </span>
          </div>
          <div className="flex items-center gap-2">
            <Button
              size="sm"
              variant="secondary"
              onClick={bulkExportZip}
              loading={bulkBusy}
            >
              <Download className="mr-1 h-4 w-4" />
              Telecharger ZIP
            </Button>
            {canUpload && (
              <Button
                size="sm"
                variant="secondary"
                onClick={() => setBulkDeleteOpen(true)}
                loading={bulkBusy}
                className="border-danger/40 text-danger hover:bg-danger/10"
              >
                <Trash2 className="mr-1 h-4 w-4" />
                Supprimer
              </Button>
            )}
            <Button size="sm" variant="ghost" onClick={clearSelection}>
              <X className="mr-1 h-4 w-4" />
              Deselectionner tout
            </Button>
          </div>
        </div>
      )}

      {isSearchActive && (
        <Card>
          <header className="flex items-center justify-between border-b border-border px-5 py-3">
            <div className="flex items-center gap-2">
              <Search className="h-4 w-4 text-accent" />
              <h3 className="text-sm font-semibold text-fg">
                Resultats de recherche
              </h3>
              <Badge variant="neutral">{searchTotal}</Badge>
            </div>
          </header>
          <ul className="divide-y divide-border">
            {searching && (
              <li className="px-5 py-8 text-center text-sm text-fg-subtle">
                Recherche en cours...
              </li>
            )}
            {!searching && (searchResults?.length ?? 0) === 0 && (
              <li className="px-5 py-8 text-center text-sm text-fg-subtle">
                Aucun document ne correspond aux criteres.
              </li>
            )}
            {!searching &&
              searchResults?.map((doc) => (
                <DocumentRow
                  key={doc.id}
                  doc={doc}
                  canDelete={canUpload}
                  onDeleted={load}
                  highlightQuery={filters.q}
                  selectable
                  selected={selectedIds.has(doc.id)}
                  onToggleSelect={() => toggleSelect(doc.id)}
                />
              ))}
          </ul>
        </Card>
      )}

      {!isSearchActive && (
        <Card>
          <header className="flex items-center justify-between border-b border-border px-5 py-3">
            <div className="flex items-center gap-2">
              <FileText className="h-4 w-4 text-accent" />
              <h3 className="text-sm font-semibold text-fg">
                Documents en vigueur
              </h3>
              <Badge variant="neutral">{view.documentsEnVigueur.length}</Badge>
            </div>
            <div className="flex items-center gap-2">
              {/*
                Identifiants post-immatriculation (RC, IF, patente, CNSS…) : ils sont
                obtenus APRES la creation de la societe, donc jamais saisissables dans le
                workflow de Creation. Le formulaire existait deja mais n'etait atteignable
                que via le pre-vol de la Fiche client : en pratique introuvable. Ce bouton
                lui donne un acces direct. Lecture seule hors EMPLOYE responsable (le
                backend re-verrouille — RG-U02-04).
              */}
              {canFiche && (
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={() => setIdentifiantsOpen(true)}
                  data-testid="open-identifiants"
                  title="Saisir les identifiants obtenus apres immatriculation (RC, IF, patente, CNSS)"
                >
                  <IdCard className="mr-1 h-4 w-4" />
                  Identifiants
                  {missingIdentity.length > 0 && role === 'EMPLOYE' && (
                    <span
                      className="ml-1.5 rounded-full bg-warning/20 px-1.5 py-0.5 text-[10px] font-semibold text-amber-700"
                      title={`${missingIdentity.length} identifiant(s) a completer`}
                    >
                      {missingIdentity.length}
                    </span>
                  )}
                </Button>
              )}
              {canFiche && (
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={onFicheClick}
                  loading={ficheBusy}
                  title="Générer la Fiche client (carte d'identité juridique)"
                >
                  {ficheBusy ? (
                    <Loader2 className="mr-1 h-4 w-4 animate-spin" />
                  ) : (
                    <FileDown className="mr-1 h-4 w-4" />
                  )}
                  Fiche client
                </Button>
              )}
              {canUpload && (
                <>
                  <Button
                    size="sm"
                    onClick={() => setVersionedDialogOpen(true)}
                    data-testid="open-versioned-upload"
                  >
                    <Upload className="mr-1 h-4 w-4" />
                    Uploader document
                  </Button>
                  <Button size="sm" variant="secondary" onClick={() => setUploadOpen(true)}>
                    <Upload className="mr-1 h-4 w-4" />
                    Upload multiple
                  </Button>
                </>
              )}
              {/* §A — a la place des boutons : la raison, la ou l'utilisateur
                  cherche l'action manquante. */}
              {readOnly && role === 'EMPLOYE' && (
                <span
                  className="inline-flex items-center gap-1.5 rounded-lg border border-warning/40 bg-warning/10 px-2.5 py-1.5 text-xs text-fg-subtle"
                  title={dataroomReadOnlyReason(readOnlyStatut)}
                  data-testid="juridique-readonly-hint"
                >
                  <Lock className="h-3.5 w-3.5 text-warning" />
                  {archivedStatutLabel(readOnlyStatut)} — lecture seule
                </span>
              )}
            </div>
          </header>
          {/*
            Lot 1 (2026-09-04) — LE DOSSIER JURIDIQUE S'ORGANISE PAR TICKET.

            Chaque opération forme un dossier, portant un libellé calculé côté
            serveur (« Création — T-2026-00841 — 15/06/2026 »), et contenant ses
            documents rangés en trois groupes : actes générés, justificatifs
            administratifs, pièces client.

            Deux règles, toutes deux destinées à ce qu'aucun document ne se perde :
            les versions historiques restent visibles dans le ticket qui les a
            produites (un ticket clos EST l'archive), et les documents rattachés à
            aucun ticket rejoignent un dossier « Hors ticket » au lieu d'être
            masqués.

            La recherche plein texte garde sa liste à plat : elle répond à une
            question ponctuelle, pas à un besoin de classement.
          */}
          {view.dossiersParTicket.length === 0 ? (
            <ul className="divide-y divide-border">
              <li className="px-5 py-8 text-center text-sm text-fg-subtle">
                Aucun document dans le dossier juridique.
              </li>
            </ul>
          ) : (
            view.dossiersParTicket.map((dossier) => (
              <DossierDeTicket
                key={dossier.ticketId ?? 'hors-ticket'}
                dossier={dossier}
                canDelete={canUpload}
                onDeleted={load}
                selectedIds={selectedIds}
                onToggleSelect={toggleSelect}
              />
            ))
          )}
        </Card>
      )}

      <Card>
        <header className="flex items-center justify-between border-b border-border px-5 py-3">
          <div className="flex items-center gap-2">
            <History className="h-4 w-4 text-accent" />
            <h3 className="text-sm font-semibold text-fg">
              Historique des operations
            </h3>
            <Badge variant="neutral">{view.historiqueOperations.length}</Badge>
          </div>
          <div className="flex items-center gap-2">
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setTimelineFiltersOpen(true)}
              className="relative"
            >
              <Filter className="mr-1 h-4 w-4" />
              Filtres
              {timelineFiltersCount > 0 && (
                <span className="ml-2 inline-flex h-5 min-w-[20px] items-center justify-center rounded-full bg-accent px-1.5 text-[10px] font-bold text-bg-raised">
                  {timelineFiltersCount}
                </span>
              )}
            </Button>
            {timelineFiltersCount > 0 && (
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setTimelineFilters(DEFAULT_TIMELINE_FILTERS)}
                title="Reinitialiser les filtres"
              >
                <X className="h-4 w-4" />
              </Button>
            )}
          </div>
        </header>
        <OperationsTimeline
          entries={view.historiqueOperations}
          renderDocumentRow={(doc) => <DocumentRow key={doc.id} doc={doc} />}
          emptyTitle={
            timelineFiltersCount > 0
              ? 'Aucune operation ne correspond aux filtres'
              : 'Aucune operation cloturee a ce jour'
          }
          emptyHint={
            timelineFiltersCount > 0
              ? "Essayez de relacher la periode ou retirer un type d'operation."
              : 'Les operations cloturees apparaitront ici, groupees par mois.'
          }
        />
      </Card>

      <ConfirmDialog
        open={bulkDeleteOpen}
        onOpenChange={setBulkDeleteOpen}
        title="Supprimer les documents"
        description={`Supprimer ${selectedIds.size} document(s) selectionne(s) ?`}
        variant="danger"
        confirmLabel="Supprimer"
        loading={bulkBusy}
        onConfirm={confirmBulkDelete}
      />

      <TimelineFiltersDrawer
        open={timelineFiltersOpen}
        filters={timelineFilters}
        onClose={() => setTimelineFiltersOpen(false)}
        onApply={setTimelineFilters}
      />

      <MultiUploadDrawer
        open={uploadOpen}
        dossierId={dossierId}
        activeDocuments={view?.documentsEnVigueur ?? []}
        onClose={() => setUploadOpen(false)}
        onUploaded={async () => {
          await load();
        }}
      />

      <UploadDocumentDialog
        open={versionedDialogOpen}
        dossierId={dossierId}
        activeDocuments={view?.documentsEnVigueur ?? []}
        onClose={() => setVersionedDialogOpen(false)}
        onUploaded={async () => {
          await load();
        }}
      />

      {/* Fiche client — preflight identifiants + formulaire d'édition */}
      {view && (
        <>
          <FichePreflightModal
            open={fichePreflightOpen}
            onOpenChange={setFichePreflightOpen}
            missing={missingIdentity}
            generating={ficheBusy}
            onComplete={() => {
              setFichePreflightOpen(false);
              setIdentifiantsOpen(true);
            }}
            onGenerateAnyway={() => void generateFiche()}
          />
          <IdentifiantsDrawer
            open={identifiantsOpen}
            view={view}
            readOnly={role !== 'EMPLOYE'}
            onClose={() => setIdentifiantsOpen(false)}
            onSaved={async () => {
              await load();
            }}
          />
        </>
      )}
    </div>
  );
}

/**
 * Le dossier d'un ticket : un libellé calculé, puis les documents rangés en
 * trois groupes. Replié par défaut au-delà du premier, pour qu'un dossier ayant
 * vécu dix opérations reste lisible.
 */
function DossierDeTicket({
  dossier,
  canDelete,
  onDeleted,
  selectedIds,
  onToggleSelect,
}: {
  dossier: DossierTicket;
  canDelete: boolean;
  onDeleted: () => void;
  selectedIds: Set<string>;
  onToggleSelect: (id: string) => void;
}) {
  const [ouvert, setOuvert] = useState(true);
  const horsTicket = dossier.ticketId === null;

  return (
    <section data-testid={`dossier-ticket-${dossier.ticketId ?? 'hors-ticket'}`}>
      <button
        type="button"
        onClick={() => setOuvert((v) => !v)}
        aria-expanded={ouvert}
        className="flex w-full items-center gap-2 border-b border-border bg-bg-overlay/60 px-5 py-2 text-left transition hover:bg-bg-overlay"
      >
        {ouvert ? (
          <ChevronDown className="h-4 w-4 shrink-0 text-fg-subtle" />
        ) : (
          <ChevronRight className="h-4 w-4 shrink-0 text-fg-subtle" />
        )}
        <span className={`text-sm font-semibold ${horsTicket ? 'text-fg-subtle' : 'text-fg'}`}>
          {dossier.libelle}
        </span>
        {dossier.statut && (
          <Badge variant={dossier.statut === 'CLOTURE_DOSSIER' ? 'success' : 'info'}>
            {STATUT_LABELS_COURTS[dossier.statut as TicketStatut] ?? dossier.statut}
          </Badge>
        )}
        <span className="ml-auto rounded-full bg-bg-raised px-2 py-0.5 text-[11px] font-bold text-fg-subtle">
          {dossier.totalDocuments}
        </span>
      </button>

      {ouvert && horsTicket && (
        <p className="border-b border-border bg-bg-overlay/30 px-5 py-2 text-xs text-fg-subtle">
          Documents antérieurs au classement par ticket, ou déposés hors workflow.
          Ils restent consultables et téléchargeables.
        </p>
      )}

      {ouvert &&
        dossier.groupes.map((groupe: GroupeDocuments) => (
          <div key={groupe.code} data-testid={`groupe-${groupe.code}`}>
            <h4 className="flex items-center gap-2 border-b border-border px-8 py-1.5 text-xs font-semibold uppercase tracking-wide text-fg-subtle">
              {groupe.libelle}
              <span className="rounded-full bg-bg-raised px-1.5 text-[10px] font-bold text-fg-subtle">
                {groupe.documents.length}
              </span>
            </h4>
            <ul className="divide-y divide-border">
              {groupe.documents.map((doc: DocumentSummary) => (
                <DocumentRow
                  key={doc.id}
                  doc={doc}
                  canDelete={canDelete}
                  onDeleted={onDeleted}
                  selectable
                  selected={selectedIds.has(doc.id)}
                  onToggleSelect={() => onToggleSelect(doc.id)}
                />
              ))}
            </ul>
          </div>
        ))}
    </section>
  );
}

function DocumentRow({
  doc,
  canDelete,
  onDeleted,
  highlightQuery,
  canDownload = true,
  canPrint = true,
  selectable = false,
  selected = false,
  onToggleSelect,
}: {
  doc: DocumentSummary;
  canDelete?: boolean;
  onDeleted?: () => Promise<void> | void;
  highlightQuery?: string;
  /** Sprint 7 / TASK 3 -- permissions affichage des boutons (modal + ligne). */
  canDownload?: boolean;
  canPrint?: boolean;
  /** Sprint 7 / TASK 4 -- checkbox selection bulk (default off). */
  selectable?: boolean;
  selected?: boolean;
  onToggleSelect?: () => void;
}) {
  const [downloading, setDownloading] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  // Confirmation de suppression (remplace confirm() natif).
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false);
  const type = String(doc.documentType);
  const label =
    (DOCUMENT_TYPE_LABELS as Record<string, string>)[type] ?? type;

  async function download() {
    setDownloading(true);
    try {
      await dataroomService.downloadDocument(doc.id, doc.filename);
    } finally {
      setDownloading(false);
    }
  }

  async function remove() {
    setDeleting(true);
    try {
      await dataroomService.deleteJuridiqueDocument(doc.id);
      setConfirmDeleteOpen(false);
      await onDeleted?.();
    } catch {
      // erreur deja loggee par axios interceptor
    } finally {
      setDeleting(false);
    }
  }

  /*
   * Fix DR4 (2026-08-16) — l'apercu n'etait propose QUE pour les PDF.
   *
   * Or les actes produits par la voie directeur sont des `.docx` : le bouton
   * « Apercu » disparaissait donc précisément sur les documents que la plateforme
   * génère elle-même. Le backend sait convertir Office → PDF a la volee
   * (`OfficePreviewSupport`, deja utilise par les depots) : on propose donc
   * l'apercu pour PDF, images ET formats bureautiques. Les types restants gardent
   * le comportement historique (bouton masque, telechargement disponible).
   */
  const nomOuType = `${doc.contentType ?? ''} ${doc.filename ?? ''}`.toLowerCase();
  const isPreviewable =
    nomOuType.includes('pdf') ||
    nomOuType.includes('image/') ||
    /\.(pdf|png|jpe?g|gif|webp|docx?|xlsx?|pptx?|odt|ods|odp|rtf)$/.test(
      (doc.filename ?? '').toLowerCase(),
    );

  return (
    <li
      className={`flex flex-col gap-1 px-5 py-3 ${
        selected ? 'bg-accent/10/40' : ''
      }`}
    >
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          {selectable && (
            <input
              type="checkbox"
              checked={selected}
              onChange={onToggleSelect}
              onClick={(e) => e.stopPropagation()}
              className="h-4 w-4 cursor-pointer accent-indigo-600"
              aria-label={`Selectionner ${doc.title}`}
            />
          )}
          <div className="rounded-lg bg-bg-overlay p-2">
            <FileText className="h-4 w-4 text-fg-subtle" />
          </div>
          <div>
            <p className="text-sm font-medium text-fg">
              <Highlight text={doc.title} query={highlightQuery} />
            </p>
            <p className="text-xs text-fg-subtle">
              {label} • v{doc.version} •{' '}
              {new Date(doc.createdAt).toLocaleDateString('fr-FR')}
              {highlightQuery && doc.filename && doc.filename !== doc.title && (
                <>
                  {' • '}
                  <Highlight text={doc.filename} query={highlightQuery} />
                </>
              )}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-1">
          {isPreviewable && (
            <Button
              size="sm"
              variant="ghost"
              onClick={() => setPreviewOpen(true)}
              title="Apercu"
            >
              <Eye className="mr-1 h-3.5 w-3.5" />
              Apercu
            </Button>
          )}
          {canDownload && (
            <Button
              size="sm"
              variant="secondary"
              onClick={download}
              loading={downloading}
            >
              <Download className="mr-1 h-3.5 w-3.5" />
              Telecharger
            </Button>
          )}
          {canDelete && (
            <button
              type="button"
              onClick={() => setConfirmDeleteOpen(true)}
              disabled={deleting}
              className="rounded-lg p-2 text-danger hover:bg-danger/10 hover:text-danger disabled:opacity-50"
              aria-label="Supprimer"
              title="Supprimer"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          )}
        </div>
      </div>
      {/* Sprint 2026-06-23 — disclosure des anciennes versions du Document logique. */}
      {doc.current && (
        <DocumentVersionsDropdown
          documentId={doc.id}
          canRestore={canDelete}
          onChanged={onDeleted}
        />
      )}
      <PdfPreviewModal
        open={previewOpen}
        documentId={previewOpen ? doc.id : null}
        filename={doc.filename}
        canDownload={canDownload}
        canPrint={canPrint}
        onClose={() => setPreviewOpen(false)}
        onDownload={download}
      />
      <ConfirmDialog
        open={confirmDeleteOpen}
        onOpenChange={setConfirmDeleteOpen}
        title="Supprimer le document"
        description={`Supprimer "${doc.title}" ?`}
        variant="danger"
        confirmLabel="Supprimer"
        loading={deleting}
        onConfirm={remove}
      />
    </li>
  );
}

// Sprint 7 / TASK 2 -- L'ancien TicketHistoryRow (liste plate) est remplace
// par <OperationsTimeline /> (frise verticale groupee par mois).
// Conserve historiquement en git : commit precedent contient l'ancienne version.
//
// Sprint 2026-06-24 -- L'ancien UploadDocumentDrawer (upload multiple = tous
// nouveaux documents, sans versioning) est remplace par <MultiUploadDrawer />
// (cf. ./components/MultiUploadDrawer.tsx) : choix par fichier nouveau document
// vs nouvelle version + motif, via le helper lib/dataroomUpload.uploadOrReplace.
