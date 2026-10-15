import { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation, useSearchParams } from 'react-router-dom';
import {
  Activity,
  ArrowLeft,
  ChevronRight,
  FileText,
  FolderOpen,
  MessageSquare,
  PauseCircle,
  PlayCircle,
  Search,
  Send,
  Upload,
  UserCheck,
  UserMinus,
  UserPlus,
} from 'lucide-react';
import { Page } from '../../components/layout/Page';
import { Card } from '../../components/ui/Card';
import { TextField } from '../../components/ui/TextField';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { Message } from '../../components/ui/Message';
import { EmptyState } from '../../components/ui/EmptyState';
import { Onglets } from '../../components/ui/Onglets';
import { Skeleton } from '../../components/ui/LoadingSkeleton';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { HistoriqueResponsablesPanel } from './components/HistoriqueResponsablesPanel';
import { AccesClientPanel } from './components/AccesClientPanel';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { dataroomService } from '../../services/dataroom.service';
import { extractError } from '../../lib/api';
import {
  dataroomReadOnlyMessage,
  isDossierArchived,
} from '../../lib/dossierArchive';
import { useCurrentUser } from '../../store/authStore';
import type { DataroomSettings, DossierBrief, DroitsDossier } from '../../types/dataroom';
import { displayFormeJuridique } from '../../types/dataroom';
import type { DossierClient } from '../../types/auth';
import { DossierJuridiqueTab } from './DossierJuridiqueTab';
import { DemandesTab } from './DemandesTab';
import { RequetesAuClientSection } from './RequetesAuClientSection';
import { DepotsTab } from './DepotsTab';
import { ClientDataroomView } from './ClientDataroomView';
import { InviteClientDrawer } from './InviteClientDrawer';
import { ClientAccessLogDrawer } from './components/ClientAccessLogDrawer';
import { authService } from '../../services/auth.service';
import { TransferDossierButton } from '../../components/transfer/TransferDossierButton';
import { EntityActivityPanel } from '../../components/tracabilite/EntityActivityPanel';

/**
 * Lot 1 (2026-09-04) — la Data Room compte QUATRE sections, et quatre seulement :
 * le dossier juridique (organise par ticket), le depot libre du client, les
 * requetes de l'employe AU client, et les demandes du client AU cabinet.
 *
 * Les dossiers comptable et fiscal sont sortis du perimetre produit. Les pieces
 * de cette nature se deposent desormais dans « Depot client », sans traitement.
 *
 * Lot L6b (E6) : ecran migre sur la coquille commune (Page : titre, fil d'Ariane,
 * action principale au meme endroit) et sur la bibliotheque (onglets, badges, etats
 * vides, squelettes, messages). Logique et appels au serveur inchanges.
 */
type Tab = 'juridique' | 'depots' | 'requetes' | 'demandes';

const TABS: { value: Tab; label: string; icon: typeof FileText }[] = [
  // Lot L6 (E9) : libelles et ordre de RG-DR-01.
  { value: 'juridique', label: 'Dossier juridique', icon: FileText },
  { value: 'depots', label: 'Dépôts du client', icon: Upload },
  { value: 'demandes', label: 'Demandes du client', icon: MessageSquare },
  { value: 'requetes', label: 'Requêtes du cabinet', icon: Send },
];

/** Lot L6 (E9, CDC 15.4) : ce que contient chaque section et ce qu'on y fait. */
const AIDE_ONGLETS: Record<Tab, string> = {
  juridique:
    'Les documents juridiques de la société, rangés par ticket (une opération, un dossier). Ajoutez un document déposé ; consultez les versions remplacées ; téléchargez la fiche client ou la fiche dossier.',
  depots:
    'Les pièces que le client a déposées lui-même depuis son espace. Consultez-les et téléchargez-les ; rangez dans le dossier juridique celles qui servent une opération.',
  demandes:
    'Les demandes que le client adresse au cabinet. Prenez-les en charge, répondez, ou ouvrez un ticket quand une demande appelle une opération juridique.',
  requetes:
    'Ce que le cabinet attend du client : une pièce à fournir ou une réponse. Créez une requête ; le client la voit dans son espace et y répond.',
};

/** Forme juridique, ICE (ou pays) et ville, separes par un point median. */
function identiteCourte(d: DossierBrief): string {
  const parts = [
    displayFormeJuridique(d) ?? '—',
    d.origine === 'ETRANGERE' ? d.pays : d.ice ? `ICE ${d.ice}` : null,
    d.ville,
  ].filter(Boolean);
  return parts.join(' · ');
}

/**
 * Page d'entree Data Room.
 * - Si l'utilisateur est CLIENT, on rend la vue client (ClientDataroomView).
 * - Sinon (EMPLOYE / SUPERVISEUR / SUPER_ADMIN), on rend la vue de gestion.
 */
export function DataroomPage() {
  const user = useCurrentUser();

  if (user?.role === 'CLIENT') {
    return <ClientDataroomView />;
  }

  return <DataroomManagementView />;
}

// ============================================================
// Vue Management (EMPLOYE / SUPERVISEUR / SUPER_ADMIN)
// ============================================================

function DataroomManagementView() {
  const user = useCurrentUser();
  const isEmploye = user?.role === 'EMPLOYE';
  const isReadOnly = user?.role === 'SUPERVISEUR' || user?.role === 'SUPER_ADMIN';
  const titreListe = isReadOnly ? 'Toutes les Data Rooms' : 'Mes Data Rooms';

  const [dossiers, setDossiers] = useState<DossierBrief[]>([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [error, setError] = useState<string | null>(null);
  // Le dossier ouvert est rattache a l'entree d'historique courante : un retour par le
  // fil d'Ariane (lien vers /data-rooms) cree une nouvelle entree et ramene a la liste.
  const location = useLocation();
  const [selection, setSelection] = useState<{ dossier: DossierBrief; cle: string } | null>(null);
  const selected = selection && selection.cle === location.key ? selection.dossier : null;
  const setSelected = useCallback(
    (d: DossierBrief | null) => setSelection(d ? { dossier: d, cle: location.key } : null),
    [location.key],
  );
  // Fix 2026-06-08 — onglet de classement Actif / Suspendu (le SUSPENDED
  // est cote dataroom_settings.access_status, vs. statut metier du dossier).
  const [statusTab, setStatusTab] = useState<'ACTIVE' | 'SUSPENDED'>('ACTIVE');
  // 2026-07-04 — deep-link depuis le dashboard ("Demandes de mes clients") :
  // /data-rooms?dossier={id}&tab=demandes ouvre directement le dataroom du dossier
  // sur l'onglet voulu.
  const [searchParams, setSearchParams] = useSearchParams();
  const deepLinkDossier = searchParams.get('dossier');
  const deepLinkTab = searchParams.get('tab');

  const loadDossiers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await dataroomService.listDossiers();
      setDossiers(result);
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // Chargement par promesse : aucun etat pose de facon synchrone dans l'effet.
    void Promise.resolve().then(loadDossiers);
  }, [loadDossiers]);

  // Auto-selection du dossier passe en query (?dossier=...) une fois la liste chargee
  // (ajustee pendant le rendu : la selection pose converge en un tour).
  if (deepLinkDossier && !selected && dossiers.length > 0) {
    const match = dossiers.find((d) => d.id === deepLinkDossier);
    if (match) setSelected(match);
  }

  // Comptes par onglet (utiles pour les badges).
  const activeCount = useMemo(
    () => dossiers.filter((d) => (d.accessStatus ?? 'ACTIVE') !== 'SUSPENDED').length,
    [dossiers],
  );
  const suspendedCount = useMemo(
    () => dossiers.filter((d) => d.accessStatus === 'SUSPENDED').length,
    [dossiers],
  );

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const byStatus = dossiers.filter((d) =>
      statusTab === 'SUSPENDED'
        ? d.accessStatus === 'SUSPENDED'
        : (d.accessStatus ?? 'ACTIVE') !== 'SUSPENDED',
    );
    if (!q) return byStatus;
    return byStatus.filter((d) =>
      [d.raisonSociale, d.reference, displayFormeJuridique(d), d.ice, d.ville, d.pays]
        .filter(Boolean)
        .some((v) => String(v).toLowerCase().includes(q)),
    );
  }, [dossiers, query, statusTab]);

  if (selected) {
    return (
      <DataroomDetail
        dossier={selected}
        titreListe={titreListe}
        initialTab={deepLinkTab}
        onBack={() => {
          setSelected(null);
          // Nettoie le deep-link pour ne pas re-selectionner en revenant a la liste.
          if (deepLinkDossier || deepLinkTab) setSearchParams({}, { replace: true });
        }}
        canEdit={!!isEmploye}
      />
    );
  }

  const recherche = query.trim() !== '';

  return (
    <Page
      titre={titreListe}
      etat={isReadOnly ? <Badge variant="neutral">Consultation</Badge> : undefined}
      description={
        isReadOnly
          ? 'Les Data Rooms de toutes les sociétés du cabinet, en consultation.'
          : 'Les Data Rooms des sociétés dont vous êtes responsable : documents, dépôts, demandes et requêtes du client.'
      }
      aide={
        <TexteAide cle="data-rooms-liste" titre="Vos Data Rooms">
          <p>
            Une Data Room réunit tout ce qui concerne une société : ses documents juridiques, les
            pièces déposées par le client, ses demandes et vos requêtes. Elle est créée
            automatiquement à l’ouverture d’un ticket de création ou d’import de société. Ouvrez-en
            une pour travailler ; l’onglet « Suspendus » rassemble celles dont l’accès du client est
            coupé.
          </p>
        </TexteAide>
      }
    >
      <div className="space-y-4">
        <div className="flex flex-col gap-3 @2xl/page:flex-row @2xl/page:items-end @2xl/page:justify-between">
          <Onglets
            libelle="Classement des Data Rooms"
            actif={statusTab}
            onChange={(id) => setStatusTab(id as 'ACTIVE' | 'SUSPENDED')}
            className="min-w-0"
            onglets={[
              {
                id: 'ACTIVE',
                libelle: 'Actifs',
                badge: <Badge variant="neutral">{loading ? '…' : activeCount}</Badge>,
              },
              {
                id: 'SUSPENDED',
                libelle: 'Suspendus',
                badge: <Badge variant={suspendedCount > 0 ? 'danger' : 'neutral'}>{loading ? '…' : suspendedCount}</Badge>,
              },
            ]}
          />
          <div className="relative w-full @2xl/page:w-80">
            <Search
              aria-hidden="true"
              className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-fg-subtle"
            />
            <TextField
              type="search"
              placeholder="Rechercher une société, une référence, un ICE…"
              aria-label="Rechercher une Data Room"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              className="pl-9"
            />
          </div>
        </div>

        {error && (
          <Message
            ton="erreur"
            titre="La liste des Data Rooms n’a pas pu être chargée."
            action={
              <Button type="button" size="sm" variant="secondary" onClick={() => void loadDossiers()}>
                Réessayer
              </Button>
            }
          >
            {error}
          </Message>
        )}

        {loading && dossiers.length === 0 ? (
          <div className="space-y-3" aria-busy="true" aria-label="Chargement des Data Rooms">
            {[0, 1, 2].map((i) => (
              <Card key={i} padding={false} className="flex items-center gap-4 p-4">
                <Skeleton className="h-11 w-11 rounded-lg" />
                <div className="flex-1 space-y-2">
                  <Skeleton className="h-4 w-1/3" />
                  <Skeleton className="h-3 w-1/2" />
                </div>
              </Card>
            ))}
          </div>
        ) : error && dossiers.length === 0 ? null : filtered.length === 0 ? (
          <Card>
            <EmptyState
              icon={<FolderOpen />}
              title={
                recherche
                  ? 'Aucune Data Room ne correspond à la recherche'
                  : statusTab === 'SUSPENDED'
                    ? 'Aucune Data Room suspendue'
                    : 'Aucune Data Room active'
              }
              description={
                recherche
                  ? 'Essayez le nom de la société, sa référence ou son ICE, ou effacez la recherche.'
                  : statusTab === 'SUSPENDED'
                    ? 'Toutes les Data Rooms sont ouvertes à leur client. Une suspension se règle dans la Data Room, rubrique « Accès du client ».'
                    : 'Une Data Room est créée automatiquement à l’ouverture d’un ticket de création ou d’import de société.'
              }
              cta={
                recherche ? (
                  <Button type="button" variant="secondary" onClick={() => setQuery('')}>
                    Effacer la recherche
                  </Button>
                ) : undefined
              }
            />
          </Card>
        ) : (
          /**
           * VUE SUPERVISEUR : la maquette groupe les Data Rooms par employe assignee.
           * NOTE BACKEND : le DTO DossierBrief n'expose pas (encore) l'identite
           * de l'employe assignee a chaque dossier. Vue plate, badge "Consultation".
           */
          <ul className="space-y-3" aria-label={titreListe}>
            {filtered.map((d) => (
              <li key={d.id}>
                <DossierCard dossier={d} onOpen={() => setSelected(d)} />
              </li>
            ))}
          </ul>
        )}
      </div>
    </Page>
  );
}

// ============================================================
// Carte d'une Data Room dans la liste
// ============================================================

function DossierCard({
  dossier,
  onOpen,
}: {
  dossier: DossierBrief;
  onOpen: () => void;
}) {
  const user = useCurrentUser();
  // Suspendre / reactiver : PATCH .../settings/suspension (EMPLOYE, SUPERVISEUR cote serveur) ;
  // la liste le reserve a l'employe, comme avant (le superviseur le fait depuis la Data Room).
  const canEdit = user?.role === 'EMPLOYE';
  const [settings, setSettings] = useState<DataroomSettings | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    dataroomService
      .getSettings(dossier.id)
      .then(setSettings)
      .catch(() => setSettings(null));
  }, [dossier.id]);

  const suspended = settings?.accessStatus === 'SUSPENDED';

  async function handleToggleSuspend() {
    if (!settings) return;
    setBusy(true);
    try {
      const next = settings.accessStatus !== 'SUSPENDED';
      const updated = await dataroomService.toggleSuspension(dossier.id, next);
      setSettings(updated);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card
      interactive
      padding={false}
      onClick={onOpen}
      className="group relative flex cursor-pointer items-center gap-4 p-4"
    >
      <div
        aria-hidden="true"
        className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-lg ${
          suspended ? 'bg-danger-soft text-danger' : 'bg-bg-overlay text-fg-muted'
        }`}
      >
        <FolderOpen className="h-5 w-5" />
      </div>

      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="min-w-0 truncate text-base font-semibold text-heading">
            {/* Le nom est le bouton d'ouverture (clavier) ; la carte entiere reagit au clic. */}
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onOpen();
              }}
              className="rounded-sm text-left hover:underline"
              aria-label={`Ouvrir la Data Room de ${dossier.raisonSociale}`}
            >
              {dossier.raisonSociale}
            </button>
          </h2>
          {dossier.reference && (
            <span className="text-xs text-fg-muted" data-testid="reference-dossier">
              {dossier.reference}
            </span>
          )}
          {settings && (
            <Badge variant={suspended ? 'danger' : 'success'} dot>
              {suspended ? 'Suspendu' : 'Actif'}
            </Badge>
          )}
        </div>
        <p className="mt-0.5 truncate text-sm text-fg-muted">{identiteCourte(dossier)}</p>
      </div>

      <div className="hidden shrink-0 px-2 text-center @md:block">
        <p className="text-xs text-fg-muted">Accès du client</p>
        <p className="text-base font-semibold tabular-nums text-fg">{settings ? settings.accessCount : '—'}</p>
      </div>

      <div className="flex shrink-0 items-center gap-1" onClick={(e) => e.stopPropagation()}>
        {canEdit && settings && (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            iconOnly
            disabled={busy}
            onClick={() => void handleToggleSuspend()}
            title={suspended ? 'Réactiver l’accès du client' : 'Suspendre l’accès du client'}
            aria-label={
              suspended
                ? `Réactiver l’accès du client de ${dossier.raisonSociale}`
                : `Suspendre l’accès du client de ${dossier.raisonSociale}`
            }
            icon={suspended ? <PlayCircle /> : <PauseCircle />}
          />
        )}
        <ChevronRight aria-hidden="true" className="h-4 w-4 text-fg-subtle transition-colors duration-150 group-hover:text-fg" />
      </div>
    </Card>
  );
}

// ============================================================
// Vue detail (preservee — fonctionnalite identique a V1)
// ============================================================

function DataroomDetail({
  dossier,
  titreListe,
  onBack,
  canEdit,
  initialTab,
}: {
  dossier: DossierBrief;
  titreListe: string;
  onBack: () => void;
  canEdit: boolean;
  initialTab?: string | null;
}) {
  const user = useCurrentUser();
  const VALID_TABS: Tab[] = ['juridique', 'depots', 'requetes', 'demandes'];
  const [tab, setTab] = useState<Tab>(
    initialTab && VALID_TABS.includes(initialTab as Tab) ? (initialTab as Tab) : 'juridique',
  );
  const [settings, setSettings] = useState<DataroomSettings | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Lot L1 : le responsable du dossier et le superviseur reglent l'acces du client.
  const [droits, setDroits] = useState<DroitsDossier | null>(null);
  const [inviteOpen, setInviteOpen] = useState(false);
  // 2026-07-01 -- identite du CLIENT lie (section "Acces client")
  const [client, setClient] = useState<DossierClient | null>(null);
  const [loadingClient, setLoadingClient] = useState(true);
  // Sprint 7 / TASK 5 -- drawer "Activite client"
  const [accessLogOpen, setAccessLogOpen] = useState(false);
  // Lot L1 : plus de suppression d'une Data Room (absente du CDC, contraire a la
  // conservation de dix ans RG-DP-03) : bouton et dialogue retires.
  // Retrait de l'acces client : ConfirmDialog nominatif (remplace window.confirm).
  const [removeClientOpen, setRemoveClientOpen] = useState(false);
  const [removingClient, setRemovingClient] = useState(false);

  const loadSettings = useCallback(async () => {
    try {
      const s = await dataroomService.getSettings(dossier.id);
      setSettings(s);
    } catch (err) {
      setError(extractError(err).message);
    }
  }, [dossier.id]);

  const loadClient = useCallback(async () => {
    setLoadingClient(true);
    try {
      const c = await authService.getDossierClient(dossier.id);
      setClient(c);
    } catch {
      // Non bloquant : la section affichera "Aucun client".
      setClient(null);
    } finally {
      setLoadingClient(false);
    }
  }, [dossier.id]);

  useEffect(() => {
    // Chargement par promesse : aucun etat pose de facon synchrone dans l'effet.
    void Promise.resolve().then(() => {
      void loadSettings();
      void loadClient();
    });
  }, [loadSettings, loadClient]);

  useEffect(() => {
    if (user?.role !== 'EMPLOYE' && user?.role !== 'SUPERVISEUR') return undefined;
    let actif = true;
    dataroomService
      .mesDroits(dossier.id)
      .then((d) => actif && setDroits(d))
      .catch(() => actif && setDroits(null));
    return () => {
      actif = false;
    };
  }, [dossier.id, user?.role]);

  /**
   * Fix 2026-06-07 (BUG 6) — Retire DEFINITIVEMENT l'acces du client
   * au dossier (vs. suspension qui est reversible).
   *
   * Demande confirmation explicite : action irreversible cote liaison.
   * Le compte user du client n'est pas supprime (autres dossiers,
   * historique). Mais il perd l'acces a CE dossier jusqu'a re-invitation.
   */
  async function confirmRemoveClientAccess() {
    setRemovingClient(true);
    try {
      await authService.removeClientAccess(dossier.id);
      setRemoveClientOpen(false);
      await loadClient();
      await loadSettings();
    } catch (err) {
      setError(extractError(err).message);
      setRemoveClientOpen(false);
    } finally {
      setRemovingClient(false);
    }
  }

  const suspended = settings?.accessStatus === 'SUSPENDED';
  /**
   * Lot DIVERS §A (2026-08-13) — societe dissoute / liquidee / radiee : la Data
   * Room bascule automatiquement en LECTURE SEULE. A ne pas confondre avec la
   * suspension manuelle (`accessStatus`), qui ne concerne que l'acces CLIENT et
   * reste pilotee par l'employe. Ici la regle est derivee du statut de la societe
   * et s'applique a tout le monde ; le backend la re-verifie a chaque ecriture.
   */
  const archived = isDossierArchived(dossier.statut);
  // L'invitation est l'action principale de l'ecran tant qu'aucun client n'est rattache.
  const inviter = canEdit && !loadingClient && !client;

  return (
    <Page
      titre={dossier.raisonSociale}
      fil={[
        { libelle: 'Accueil', to: '/dashboard' },
        { libelle: titreListe, to: '/data-rooms' },
        { libelle: dossier.raisonSociale },
      ]}
      etat={
        settings ? (
          <Badge variant={suspended ? 'danger' : 'success'} dot>
            {suspended ? 'Accès du client suspendu' : 'Accès du client actif'}
          </Badge>
        ) : undefined
      }
      description={
        <div className="space-y-0.5">
          <p>{identiteCourte(dossier)}</p>
          {dossier.reference && (
            <p className="flex items-center gap-1 text-xs" data-testid="reference-dossier-entete">
              Dossier {dossier.reference}
              <InfoBulle
                libelle="Que signifie la référence du dossier ?"
                texte="La référence désigne le dossier dans le cabinet : année de création, numéro d’ordre dans l’année, puis la société. Elle figure sur les actes qui citent le dossier et ne change plus."
              />
            </p>
          )}
        </div>
      }
      actions={
        <>
          <Button type="button" variant="ghost" icon={<ArrowLeft />} onClick={onBack}>
            Retour aux Data Rooms
          </Button>
          <Button
            type="button"
            variant="secondary"
            icon={<Activity />}
            onClick={() => setAccessLogOpen(true)}
            title="Voir l’activité du client (consultations, téléchargements)"
          >
            Activité du client
          </Button>
          {/* V9 — Transfert du dossier a un collegue (EMPLOYE seulement, le bouton se masque sinon). */}
          <TransferDossierButton dossierId={dossier.id} raisonSociale={dossier.raisonSociale} />
        </>
      }
      action={
        inviter ? (
          <Button type="button" icon={<UserPlus />} onClick={() => setInviteOpen(true)}>
            Inviter un client
          </Button>
        ) : undefined
      }
      aide={
        /* Lot L1 : aide de l'ecran modifie (regle de l'aide integree, CDC 15.4). */
        <TexteAide cle="dataroom-conservation" titre="Ajouter, supprimer, conserver">
          <p>
            Les documents de la société sont conservés : une Data Room ne se supprime pas.
            L’employé responsable du dossier ajoute des documents ; il ne peut en supprimer que si
            le superviseur lui en a accordé le droit, et toute suppression est tracée. Un document
            supprimé reste en archive.
          </p>
        </TexteAide>
      }
    >
      <div className="space-y-6">
        {error && <Message ton="erreur">{error}</Message>}

        {/* §A — bandeau explicite : archive legale, lecture seule pour tous. */}
        {archived && (
          <div data-testid="dataroom-readonly-banner">
            <Message ton="attention" titre={dataroomReadOnlyMessage(dossier.statut)}>
              Consultation et téléchargement restent possibles (archives juridiques). Ajout,
              remplacement, suppression, dépôt de pièces et nouvelles demandes sont désactivés. Les
              échéances et alertes fiscales sont suspendues. Seul le parcours de liquidation peut
              encore déposer ses actes de clôture.
            </Message>
          </div>
        )}

        <div className="grid gap-4 @4xl/page:grid-cols-[minmax(0,1fr)_minmax(0,20rem)]">
          {/* Lot L1 (RG-CLI-01) : acces du client, regle par le responsable ou le superviseur. */}
          {settings ? (
            <AccesClientPanel
              dossierId={dossier.id}
              settings={settings}
              peutRegler={droits?.peutReglerAccesClient === true}
              onSettings={setSettings}
            />
          ) : (
            <Card aria-busy="true" aria-label="Chargement de l’accès du client">
              <div className="space-y-3">
                <Skeleton className="h-4 w-40" />
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-4 w-2/3" />
              </div>
            </Card>
          )}

          {/* 2026-07-01 — Client rattache au dossier (ou aucun). */}
          <Card title="Client rattaché" titleAs="h2">
            {loadingClient ? (
              <div className="space-y-2" aria-busy="true" aria-label="Chargement du client">
                <Skeleton className="h-4 w-3/4" />
                <Skeleton className="h-3 w-1/2" />
              </div>
            ) : client ? (
              <div className="space-y-3">
                <div className="flex items-start gap-2">
                  <UserCheck aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-success" />
                  <div className="min-w-0">
                    <p className="font-medium text-fg">
                      {client.firstName} {client.lastName}
                    </p>
                    <p className="truncate text-sm text-fg-muted">{client.email}</p>
                  </div>
                  {client.status && client.status !== 'ACTIVE' && (
                    <Badge variant="warning" className="ml-auto">
                      {client.status === 'PENDING' ? 'Invitation en attente' : client.status === 'INACTIVE' ? 'Compte désactivé' : client.status}
                    </Badge>
                  )}
                </div>
                {settings && (
                  <p className="text-xs text-fg-muted">
                    Client avec accès : <strong className="text-fg">{settings.accessCount}</strong>
                    {settings.lastAccessedAt &&
                      ` · dernière consultation le ${new Date(settings.lastAccessedAt).toLocaleDateString('fr-FR')}`}
                  </p>
                )}
                {canEdit && (
                  <Button
                    type="button"
                    variant="danger"
                    size="sm"
                    icon={<UserMinus />}
                    onClick={() => setRemoveClientOpen(true)}
                    title="Retirer définitivement l’accès de ce client (la suspension, elle, est réversible)"
                  >
                    Retirer l’accès
                  </Button>
                )}
              </div>
            ) : (
              <p className="text-sm text-fg-muted">
                Aucun client invité sur ce dossier.
                {canEdit
                  ? ' Invitez-le avec « Inviter un client » : il recevra ses identifiants par courriel.'
                  : ' L’employé responsable du dossier peut l’inviter.'}
              </p>
            )}
          </Card>
        </div>

        <div>
          <Onglets
            libelle="Sections de la Data Room"
            actif={tab}
            onChange={(id) => setTab(id as Tab)}
            onglets={TABS.map((t) => {
              const Icon = t.icon;
              return {
                id: t.value,
                libelle: (
                  <>
                    <Icon aria-hidden="true" className="h-4 w-4" />
                    {t.label}
                  </>
                ),
              };
            })}
          />
          <p className="mt-3 text-sm text-fg-muted" data-testid="aide-onglet-dataroom">
            {AIDE_ONGLETS[tab]}
          </p>
          <div role="tabpanel" aria-label={TABS.find((t) => t.value === tab)?.label} className="mt-4">
            {tab === 'juridique' && (
              <DossierJuridiqueTab
                dossierId={dossier.id}
                role={user?.role ?? null}
                readOnly={archived}
                readOnlyStatut={dossier.statut}
              />
            )}
            {tab === 'requetes' && (
              <RequetesAuClientSection
                dossierId={dossier.id}
                role={user?.role ?? null}
                readOnly={archived}
                readOnlyStatut={dossier.statut}
              />
            )}
            {tab === 'depots' && (
              <DepotsTab
                dossierId={dossier.id}
                role={user?.role ?? null}
                readOnly={archived}
                readOnlyStatut={dossier.statut}
              />
            )}
            {tab === 'demandes' && (
              <DemandesTab
                dossierId={dossier.id}
                role={user?.role ?? null}
                readOnly={archived}
                readOnlyStatut={dossier.statut}
              />
            )}
          </div>
        </div>

        <div className="grid gap-4 @4xl/page:grid-cols-2">
          {/* E2 — Activite (tracabilite) de ce dossier */}
          <Card>
            <EntityActivityPanel entityType="dossier" entityId={dossier.id} title="Activité du dossier" />
          </Card>

          {/* Lot L1 : historique des responsables du dossier (employe responsable, superviseur). */}
          <Card>
            <HistoriqueResponsablesPanel
              dossierId={dossier.id}
              raisonSociale={dossier.raisonSociale}
              peutReaffecter={user?.role === 'SUPERVISEUR'}
            />
          </Card>
        </div>
      </div>

      <InviteClientDrawer
        open={inviteOpen}
        dossierId={dossier.id}
        raisonSociale={dossier.raisonSociale}
        onClose={() => {
          setInviteOpen(false);
          // Un client vient peut-etre d'etre invite -> rafraichir la section.
          void loadClient();
        }}
      />

      {/* Sprint 7 / TASK 5 -- drawer "Activite client" (cabinet seulement) */}
      <ClientAccessLogDrawer
        open={accessLogOpen}
        dossierId={dossier.id}
        raisonSociale={dossier.raisonSociale}
        client={loadingClient ? undefined : client}
        onClose={() => setAccessLogOpen(false)}
      />

      {/* Retrait de l'acces client : confirmation NOMINATIVE. */}
      <ConfirmDialog
        open={removeClientOpen}
        onOpenChange={(o) => !o && setRemoveClientOpen(false)}
        title="Retirer l’accès du client"
        description={
          client
            ? `Retirer l’accès de ${client.email} ? ${client.firstName} ${client.lastName} ne pourra plus consulter ce dossier sans une nouvelle invitation. Le compte reste actif (autres dossiers, historique).`
            : 'Retirer l’accès du client à ce dossier ?'
        }
        variant="danger"
        confirmLabel="Retirer l’accès"
        loading={removingClient}
        onConfirm={confirmRemoveClientAccess}
      />
    </Page>
  );
}

/**
 * NOTE V2 :
 * - L'archive (V1) est SUPPRIMEE. Les anciennes versions sont accessibles
 *   via l'historique des tickets clotures dans DossierJuridiqueTab.
 * - "Dossier Financier" (V1) est remplace par "Dossier Comptable" (V2)
 *   avec 6 categories x N annees.
 */
