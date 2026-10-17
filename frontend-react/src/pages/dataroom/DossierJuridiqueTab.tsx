import { useCallback, useEffect, useMemo, useState, createContext, useContext } from 'react';
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
  Lock,
  Search,
  Trash2,
  Upload,
  X,
} from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Message } from '../../components/ui/Message';
import { EmptyState } from '../../components/ui/EmptyState';
import { Skeleton } from '../../components/ui/LoadingSkeleton';
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
import type { DroitsDossier } from '../../types/dataroom';
import { InfoBulle } from '../../components/ui/Aide';
import { useEnEssai } from '../../store/essaiStore';
import { AideApercuEssai } from '../../components/document/ApercuPages';
import { STATUT_LABELS_COURTS } from '../../types/ticket';
import type { TicketStatut } from '../../types/ticket';
import { JuridiqueSearchBar } from './components/JuridiqueSearchBar';
import { DEFAULT_FILTERS, filtersToApiParams, type SearchFilters } from './components/juridiqueFiltres';
import { OperationsTimeline } from './components/OperationsTimeline';
import { TimelineFiltersDrawer } from './components/TimelineFiltersDrawer';
import {
  countActiveTimelineFilters,
  DEFAULT_TIMELINE_FILTERS,
  timelineFiltersToApiParams,
  type TimelineFilters,
} from './components/timelineFiltres';
import { UploadDocumentDialog } from './components/UploadDocumentDialog';
import { MultiUploadDrawer } from './components/MultiUploadDrawer';
import { DocumentVersionsDropdown } from './components/DocumentVersionsDropdown';
import { IdentifiantsDrawer } from './components/IdentifiantsDrawer';
import { FicheSocieteDrawer, type TypeFiche } from './components/FichesSocieteDrawer';
import { computeMissingIdentity } from './components/ficheClientPreflight';
import { nomFichierDocument, referenceDocument } from '../../lib/referencesLisibles';


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

  // Lot L5 (E7) : fiche client et fiche dossier (ecran, provenance ; PDF conserve depuis L5b E3).
  // Lot L5b (E3, RG-FIC-05, 07) : fiche client et fiche dossier, deux panneaux distincts.
  const [ficheOuverte, setFicheOuverte] = useState<TypeFiche | null>(null);
  const [identifiantsOpen, setIdentifiantsOpen] = useState(false);
  const [versionIdentifiants, setVersionIdentifiants] = useState(0);

  const canFiche =
    role === 'EMPLOYE' || role === 'SUPERVISEUR' || role === 'SUPER_ADMIN';
  const missingIdentity = view ? computeMissingIdentity(view) : [];

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
    // Chargement par promesse : aucun etat pose de facon synchrone dans l'effet.
    void Promise.resolve().then(load);
  }, [load]);

  // Effet recherche : se declenche a chaque changement filtres si recherche active.
  // Les etats sont poses dans la promesse : aucun etat pose de facon synchrone dans l'effet.
  useEffect(() => {
    let cancelled = false;
    if (!isSearchActive) {
      void Promise.resolve().then(() => {
        if (cancelled) return;
        setSearchResults(null);
        setSearchTotal(0);
      });
      return () => {
        cancelled = true;
      };
    }
    const params = filtersToApiParams(filters);
    void Promise.resolve()
      .then(() => {
        if (!cancelled) setSearching(true);
        return dataroomService.searchJuridique(dossierId, params);
      })
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

  // Lot L1 (RG-DR-06) : la suppression d'un document exige le droit accorde par le
  // superviseur ; sans lui, le bouton est masque et l'ecran dit pourquoi.
  const [droits, setDroits] = useState<DroitsDossier | null>(null);
  useEffect(() => {
    if (role !== 'EMPLOYE') return undefined;
    let actif = true;
    dataroomService
      .mesDroits(dossierId)
      .then((d) => actif && setDroits(d))
      .catch(() => actif && setDroits({ peutSupprimerDocuments: false, motif: null, peutReglerAccesClient: false }));
    return () => {
      actif = false;
    };
  }, [dossierId, role]);

  // §A — la lecture seule prime sur le role : meme un EMPLOYE ne peut plus
  // deposer, versionner ni supprimer sur une societe archivee.
  const canUpload = role === 'EMPLOYE' && !readOnly;
  const canRemove = canUpload && droits?.peutSupprimerDocuments === true;
  // Decision du 2026-10-14 (RG-ESS-05) : pendant l'essai, ni telechargement ni archive ZIP ;
  // les documents se consultent en apercu (pages filigranees).
  const enEssai = useEnEssai();

  // Archive ZIP : POST .../export-zip, refuse au super-administrateur par le serveur ; jamais
  // pendant l'essai (RG-ESS-05). La selection n'apparait que si une action groupee existe.
  const peutZip = enEssai === false && role !== 'SUPER_ADMIN' && role !== null;
  const selectionPossible = peutZip || canRemove;

  if (loading && !view) {
    return (
      <div className="space-y-4" aria-busy="true" aria-label="Chargement du dossier juridique">
        <Card className="p-4">
          <Skeleton className="h-9 w-full" />
        </Card>
        <Card className="p-5">
          <div className="space-y-3">
            <Skeleton className="h-5 w-48" />
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
          </div>
        </Card>
      </div>
    );
  }

  if (error) {
    return (
      <Message
        ton="erreur"
        titre="Le dossier juridique n’a pas pu être chargé."
        action={
          <Button type="button" size="sm" variant="secondary" onClick={() => void load()}>
            Réessayer
          </Button>
        }
      >
        {error}
      </Message>
    );
  }

  if (!view) return null;

  return (
    <SocieteDuDossier.Provider value={view.raisonSociale ?? ''}>
    <div className="space-y-4">
      {enEssai && <AideApercuEssai />}
      <Card className="p-4">
        <div className="space-y-2">
          <JuridiqueSearchBar
            filters={filters}
            onChange={setFilters}
            totalResults={searchTotal}
            loading={searching}
          />
        </div>
      </Card>

      {/* Sprint 7 / TASK 4 -- barre contextuelle des actions groupees */}
      {selectionPossible && selectedIds.size > 0 && (
        <div className="sticky top-[calc(var(--hauteur-entete)+0.5rem)] z-30 flex flex-wrap items-center justify-between gap-2 rounded-xl border border-border bg-bg-raised px-4 py-2.5 shadow-card-lifted">
          <p className="flex items-center gap-2 text-sm font-medium text-fg">
            <Badge variant="default">{selectedIds.size}</Badge>
            document{selectedIds.size > 1 ? 's' : ''} sélectionné{selectedIds.size > 1 ? 's' : ''}
          </p>
          <div className="flex flex-wrap items-center gap-2">
            {peutZip && (
              <Button
                type="button"
                size="sm"
                variant="secondary"
                icon={<Download />}
                onClick={bulkExportZip}
                loading={bulkBusy}
              >
                Télécharger (ZIP)
              </Button>
            )}
            {canRemove && (
              <Button
                type="button"
                size="sm"
                variant="danger"
                icon={<Trash2 />}
                onClick={() => setBulkDeleteOpen(true)}
                loading={bulkBusy}
              >
                Supprimer
              </Button>
            )}
            <Button type="button" size="sm" variant="ghost" icon={<X />} onClick={clearSelection}>
              Tout désélectionner
            </Button>
          </div>
        </div>
      )}

      {isSearchActive && (
        <Card
          padding={false}
          titleAs="h3"
          title={
            <span className="flex items-center gap-2">
              <Search aria-hidden="true" className="h-4 w-4 text-fg-muted" />
              Résultats de la recherche
              <Badge variant="neutral">{searchTotal}</Badge>
            </span>
          }
        >
          {searching ? (
            <div className="space-y-3 px-5 py-4" aria-busy="true" aria-label="Recherche en cours">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          ) : (searchResults?.length ?? 0) === 0 ? (
            <EmptyState
              compact
              icon={<Search />}
              title="Aucun document ne correspond à la recherche"
              description="Essayez un autre mot, ou retirez des filtres."
            />
          ) : (
            <ul className="divide-y divide-border">
              {searchResults?.map((doc) => (
                <DocumentRow
                  key={doc.id}
                  doc={doc}
                  canDelete={canUpload}
                  canRemove={canRemove}
                  onDeleted={load}
                  highlightQuery={filters.q}
                  selectable={selectionPossible}
                  selected={selectedIds.has(doc.id)}
                  onToggleSelect={() => toggleSelect(doc.id)}
                />
              ))}
            </ul>
          )}
        </Card>
      )}

      {/* Lot L1 (RG-DR-06) : sans le droit, le bouton est masque et l'ecran dit pourquoi. */}
      {canUpload && droits && !droits.peutSupprimerDocuments && (
        <p role="status" className="rounded-lg border border-border bg-bg-overlay px-4 py-2.5 text-sm text-fg-muted">
          Vous pouvez ajouter des documents à ce dossier, mais pas en supprimer.{' '}
          {droits.motif ?? 'Demandez le droit de suppression à votre superviseur.'}
        </p>
      )}

      {!isSearchActive && (
        <Card
          padding={false}
          titleAs="h3"
          title={
            <span className="flex items-center gap-2">
              <FileText aria-hidden="true" className="h-4 w-4 text-fg-muted" />
              Documents en vigueur
              <Badge variant="neutral">{view.documentsEnVigueur.length}</Badge>
              {canUpload && droits && (
                <InfoBulle
                  libelle="Qui peut supprimer un document ?"
                  texte="L’employé responsable du dossier, si le superviseur lui en a accordé le droit. Un document supprimé reste en archive et la suppression est tracée."
                />
              )}
            </span>
          }
          actions={
            <div className="flex flex-wrap items-center justify-end gap-2">
              {/*
                Identifiants post-immatriculation (RC, IF, patente, CNSS…) : ils sont
                obtenus APRES la creation de la societe, donc jamais saisissables dans le
                workflow de Creation. Lecture seule hors EMPLOYE responsable (le
                backend re-verrouille — RG-U02-04).
              */}
              {canFiche && (
                <Button
                  type="button"
                  size="sm"
                  variant="secondary"
                  icon={<IdCard />}
                  onClick={() => setIdentifiantsOpen(true)}
                  data-testid="open-identifiants"
                  title="Saisir les identifiants obtenus après l’immatriculation (RC, IF, taxe professionnelle, CNSS)"
                >
                  Identifiants
                  {missingIdentity.length > 0 && role === 'EMPLOYE' && (
                    <Badge variant="warning" title={`${missingIdentity.length} identifiant(s) à compléter`}>
                      {missingIdentity.length}
                    </Badge>
                  )}
                </Button>
              )}
              {canFiche && (
                <>
                  <Button
                    type="button"
                    size="sm"
                    variant="secondary"
                    icon={<FileDown />}
                    onClick={() => setFicheOuverte('client')}
                    data-testid="open-fiche-client"
                    title="Fiche client : identifiants de la société, à consulter, corriger et télécharger en PDF"
                  >
                    Fiche client
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    variant="secondary"
                    icon={<FileDown />}
                    onClick={() => setFicheOuverte('dossier')}
                    data-testid="open-fiche-dossier"
                    title="Fiche dossier : identifiants et opérations juridiques du dossier, à télécharger en PDF"
                  >
                    Fiche dossier
                  </Button>
                  <InfoBulle
                    libelle="Fiche client ou fiche dossier ?"
                    texte="La fiche client ne présente que les identifiants de la société (aucun historique). La fiche dossier y ajoute toutes les opérations juridiques du dossier. Ce sont deux documents distincts, chacun téléchargeable en PDF."
                  />
                </>
              )}
              {canUpload && (
                <>
                  <Button type="button" size="sm" variant="secondary" icon={<Upload />} onClick={() => setUploadOpen(true)}>
                    Ajouter plusieurs documents
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    icon={<Upload />}
                    onClick={() => setVersionedDialogOpen(true)}
                    data-testid="open-versioned-upload"
                  >
                    Ajouter un document
                  </Button>
                </>
              )}
              {/* §A — a la place des boutons : la raison, la ou l'utilisateur
                  cherche l'action manquante. */}
              {readOnly && role === 'EMPLOYE' && (
                <span
                  className="inline-flex items-center gap-1.5 rounded-lg border border-warning/40 bg-warning-soft px-2.5 py-1.5 text-xs text-fg"
                  title={dataroomReadOnlyReason(readOnlyStatut)}
                  data-testid="juridique-readonly-hint"
                >
                  <Lock aria-hidden="true" className="h-3.5 w-3.5 text-warning" />
                  {archivedStatutLabel(readOnlyStatut)} — lecture seule
                </span>
              )}
            </div>
          }
        >
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
            <EmptyState
              compact
              icon={<FileText />}
              title="Aucun document dans le dossier juridique"
              description={
                canUpload
                  ? 'Les actes produits par les parcours s’y rangent seuls. Pour verser une pièce reçue, cliquez sur « Ajouter un document ».'
                  : 'Les actes produits par les parcours du dossier s’y rangeront, opération par opération.'
              }
            />
          ) : (
            view.dossiersParTicket.map((dossier) => (
              <DossierDeTicket
                key={dossier.ticketId ?? 'hors-ticket'}
                dossier={dossier}
                canDelete={canUpload}
                canRemove={canRemove}
                onDeleted={load}
                selectable={selectionPossible}
                selectedIds={selectedIds}
                onToggleSelect={toggleSelect}
              />
            ))
          )}
        </Card>
      )}

      <Card
        padding={false}
        titleAs="h3"
        title={
          <span className="flex items-center gap-2">
            <History aria-hidden="true" className="h-4 w-4 text-fg-muted" />
            Historique des opérations
            <Badge variant="neutral">{view.historiqueOperations.length}</Badge>
          </span>
        }
        actions={
          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="secondary"
              size="sm"
              icon={<Filter />}
              onClick={() => setTimelineFiltersOpen(true)}
            >
              Filtres
              {timelineFiltersCount > 0 && <Badge variant="default">{timelineFiltersCount}</Badge>}
            </Button>
            {timelineFiltersCount > 0 && (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                iconOnly
                icon={<X />}
                onClick={() => setTimelineFilters(DEFAULT_TIMELINE_FILTERS)}
                title="Réinitialiser les filtres"
                aria-label="Réinitialiser les filtres"
              />
            )}
          </div>
        }
      >
        <OperationsTimeline
          entries={view.historiqueOperations}
          renderDocumentRow={(doc) => <DocumentRow key={doc.id} doc={doc} />}
          emptyTitle={
            timelineFiltersCount > 0
              ? 'Aucune opération ne correspond aux filtres'
              : 'Aucune opération clôturée à ce jour'
          }
          emptyHint={
            timelineFiltersCount > 0
              ? "Élargissez la période ou retirez un type d’opération."
              : 'Les opérations clôturées apparaîtront ici, groupées par mois.'
          }
        />
      </Card>

      <ConfirmDialog
        open={bulkDeleteOpen}
        onOpenChange={setBulkDeleteOpen}
        title="Supprimer les documents"
        description={`Supprimer ${selectedIds.size} document(s) sélectionné(s) ? Ils restent en archive et la suppression est tracée.`}
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

      {/* Lot L5 (E7) : fiche client et fiche dossier, formulaire des identifiants */}
      {view && (
        <>
          <FicheSocieteDrawer
            key={ficheOuverte ?? 'fermee'}
            open={ficheOuverte !== null}
            type={ficheOuverte ?? 'client'}
            dossierId={dossierId}
            raisonSociale={view.raisonSociale}
            onClose={() => setFicheOuverte(null)}
            onModifier={() => setIdentifiantsOpen(true)}
            versionDonnees={versionIdentifiants}
          />
          <IdentifiantsDrawer
            open={identifiantsOpen}
            view={view}
            readOnly={role !== 'EMPLOYE'}
            onClose={() => setIdentifiantsOpen(false)}
            onSaved={async () => {
              setVersionIdentifiants((v) => v + 1);
              await load();
            }}
          />
        </>
      )}
    </div>
    </SocieteDuDossier.Provider>
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
  canRemove,
  onDeleted,
  selectable,
  selectedIds,
  onToggleSelect,
}: {
  dossier: DossierTicket;
  canDelete: boolean;
  /** Lot L1 : bouton de suppression (droit accorde par le superviseur). */
  canRemove: boolean;
  onDeleted: () => void;
  /** Lot L6b : cases de selection seulement si une action groupee existe. */
  selectable: boolean;
  selectedIds: Set<string>;
  onToggleSelect: (id: string) => void;
}) {
  const [ouvert, setOuvert] = useState(true);
  const horsTicket = dossier.ticketId === null;

  return (
    <section data-testid={`dossier-ticket-${dossier.ticketId ?? 'hors-ticket'}`} className="border-b border-border last:border-b-0">
      <button
        type="button"
        onClick={() => setOuvert((v) => !v)}
        aria-expanded={ouvert}
        className="flex w-full items-center gap-2 bg-bg-overlay px-5 py-2.5 text-left transition-colors duration-150 hover:bg-border/40"
      >
        {ouvert ? (
          <ChevronDown aria-hidden="true" className="h-4 w-4 shrink-0 text-fg-subtle" />
        ) : (
          <ChevronRight aria-hidden="true" className="h-4 w-4 shrink-0 text-fg-subtle" />
        )}
        <span className={`min-w-0 truncate text-sm font-semibold ${horsTicket ? 'text-fg-muted' : 'text-fg'}`}>
          {dossier.libelle}
        </span>
        {dossier.statut && (
          <Badge variant={dossier.statut === 'CLOTURE_DOSSIER' ? 'success' : 'info'}>
            {STATUT_LABELS_COURTS[dossier.statut as TicketStatut] ?? dossier.statut}
          </Badge>
        )}
        <Badge variant="neutral" className="ml-auto" title="Nombre de documents">
          {dossier.totalDocuments}
        </Badge>
      </button>

      {ouvert && horsTicket && (
        <p className="border-t border-border px-5 py-2 text-xs text-fg-muted">
          Documents antérieurs au classement par ticket, ou déposés en dehors d’un parcours.
          Ils restent consultables et téléchargeables.
        </p>
      )}

      {ouvert &&
        dossier.groupes.map((groupe: GroupeDocuments) => (
          <div key={groupe.code} data-testid={`groupe-${groupe.code}`} className="border-t border-border">
            <h4 className="flex items-center gap-2 px-5 pb-1 pt-3 text-xs font-semibold text-fg-muted">
              {groupe.libelle}
              <span className="tabular-nums text-fg-subtle">({groupe.documents.length})</span>
            </h4>
            <ul className="divide-y divide-border">
              {groupe.documents.map((doc: DocumentSummary) => (
                <DocumentRow
                  key={doc.id}
                  doc={doc}
                  canDelete={canDelete}
                  canRemove={canRemove}
                  onDeleted={onDeleted}
                  selectable={selectable}
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

/** Lot L4d (E6) : la societe du dossier, pour la reference lisible de chaque document. */
const SocieteDuDossier = createContext<string>('');

function DocumentRow({
  doc,
  canDelete,
  canRemove = false,
  onDeleted,
  highlightQuery,
  canDownload = true,
  canPrint = true,
  selectable = false,
  selected = false,
  onToggleSelect,
}: {
  doc: DocumentSummary;
  /** Versionner et restaurer (employe responsable, dossier non archive). */
  canDelete?: boolean;
  /** Lot L1 (RG-DR-06) : supprimer, seulement avec le droit accorde par le superviseur. */
  canRemove?: boolean;
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
  // Decision du 2026-10-14 (RG-ESS-05) : aucun telechargement ni impression pendant l'essai.
  const enEssai = useEnEssai();
  const peutTelecharger = canDownload && enEssai === false;
  const peutImprimer = canPrint && enEssai === false;
  // Confirmation de suppression (remplace confirm() natif).
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false);
  const type = String(doc.documentType);
  const label =
    (DOCUMENT_TYPE_LABELS as Record<string, string>)[type] ?? type;
  const societe = useContext(SocieteDuDossier);
  const reference = referenceDocument(label, societe, doc.version);

  async function download() {
    setDownloading(true);
    try {
      // Lot L4d (E6) : le fichier telecharge porte la reference lisible du document.
      await dataroomService.downloadDocument(doc.id, nomFichierDocument(reference, doc.filename));
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
    <li className={`flex flex-col gap-1 px-5 py-3 transition-colors duration-150 ${selected ? 'bg-accent-soft' : ''}`}>
      <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-2">
        <div className="flex min-w-0 items-center gap-3">
          {selectable && (
            <input
              type="checkbox"
              checked={selected}
              onChange={onToggleSelect}
              onClick={(e) => e.stopPropagation()}
              className="h-4 w-4 shrink-0 cursor-pointer rounded-sm accent-[var(--color-accent)]"
              aria-label={`Sélectionner ${doc.title}`}
            />
          )}
          <div aria-hidden="true" className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-bg-overlay">
            <FileText className="h-4 w-4 text-fg-muted" />
          </div>
          <div className="min-w-0">
            <p className="truncate text-sm font-medium text-fg">
              <Highlight text={doc.title} query={highlightQuery} />
            </p>
            <p className="text-xs text-fg-muted">
              <span data-testid="reference-document">{reference}</span> ·{' '}
              {new Date(doc.createdAt).toLocaleDateString('fr-FR')}
              {highlightQuery && doc.filename && doc.filename !== doc.title && (
                <>
                  {' · '}
                  <Highlight text={doc.filename} query={highlightQuery} />
                </>
              )}
            </p>
          </div>
        </div>
        <div className="ml-auto flex shrink-0 items-center gap-1">
          {isPreviewable && (
            <Button type="button" size="sm" variant="ghost" icon={<Eye />} onClick={() => setPreviewOpen(true)}>
              Aperçu
            </Button>
          )}
          {peutTelecharger && (
            <Button type="button" size="sm" variant="secondary" icon={<Download />} onClick={download} loading={downloading}>
              Télécharger
            </Button>
          )}
          {canDelete && canRemove && (
            <Button
              type="button"
              size="sm"
              variant="ghost"
              iconOnly
              icon={<Trash2 className="text-danger" />}
              onClick={() => setConfirmDeleteOpen(true)}
              disabled={deleting}
              aria-label="Supprimer le document"
              title="Supprimer le document"
            />
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
        canDownload={peutTelecharger}
        canPrint={peutImprimer}
        onClose={() => setPreviewOpen(false)}
        onDownload={download}
      />
      <ConfirmDialog
        open={confirmDeleteOpen}
        onOpenChange={setConfirmDeleteOpen}
        title="Supprimer le document"
        description={`Supprimer « ${doc.title} » ? Le document reste en archive et la suppression est tracée.`}
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
