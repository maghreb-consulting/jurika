import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Activity,
  ArrowLeft,
  Building2,
  Calculator,
  Check,
  Download,
  Eye,
  FileText,
  FolderOpen,
  Landmark,
  Loader2,
  Lock,
  MessageSquare,
  PauseCircle,
  PlayCircle,
  Printer,
  Search,
  Trash2,
  Upload,
  UserCheck,
  UserMinus,
  UserPlus,
} from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { TextField } from '../../components/ui/TextField';
import { Button } from '../../components/ui/Button';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { PromptDialog } from '../../components/ui/PromptDialog';
import { dataroomService } from '../../services/dataroom.service';
import { extractError } from '../../lib/api';
import {
  dataroomReadOnlyMessage,
  isDossierArchived,
} from '../../lib/dossierArchive';
import { useCurrentUser } from '../../store/authStore';
import type { DataroomSettings, DossierBrief } from '../../types/dataroom';
import { displayFormeJuridique } from '../../types/dataroom';
import type { DossierClient } from '../../types/auth';
import { DossierJuridiqueTab } from './DossierJuridiqueTab';
import { DossierComptableTab } from './DossierComptableTab';
import { DossierFiscalTab } from './DossierFiscalTab';
import { DemandesTab } from './DemandesTab';
import { DepotsTab } from './DepotsTab';
import { ClientDataroomView } from './ClientDataroomView';
import { InviteClientDrawer } from './InviteClientDrawer';
import { ClientAccessLogDrawer } from './components/ClientAccessLogDrawer';
import { authService } from '../../services/auth.service';
import { TransferDossierButton } from '../../components/transfer/TransferDossierButton';
import { EntityActivityPanel } from '../../components/tracabilite/EntityActivityPanel';

// Sprint 7 / TASK 6.3 -- ajout 4e tab "Dossier Fiscal" (placeholder Sprint 8)
type Tab = 'juridique' | 'comptable' | 'fiscal' | 'depots' | 'demandes';

const TABS: {
  value: Tab;
  label: string;
  icon: typeof FileText;
  activeBg: string;
}[] = [
  { value: 'juridique', label: 'Dossier Juridique', icon: FileText, activeBg: 'bg-accent' },
  { value: 'comptable', label: 'Dossier Comptable', icon: Calculator, activeBg: 'bg-success' },
  { value: 'fiscal',    label: 'Dossier Fiscal',    icon: Landmark,  activeBg: 'bg-warning' },
  { value: 'depots',    label: 'Depots',            icon: Upload,    activeBg: 'bg-warning' },
  { value: 'demandes',  label: 'Demandes',          icon: MessageSquare, activeBg: 'bg-accent' },
];

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

  const [dossiers, setDossiers] = useState<DossierBrief[]>([]);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<DossierBrief | null>(null);
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
    loadDossiers();
  }, [loadDossiers]);

  // Auto-selection du dossier passe en query (?dossier=...) une fois la liste chargee.
  useEffect(() => {
    if (!deepLinkDossier || selected || dossiers.length === 0) return;
    const match = dossiers.find((d) => d.id === deepLinkDossier);
    if (match) setSelected(match);
  }, [deepLinkDossier, dossiers, selected]);

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
      [d.raisonSociale, displayFormeJuridique(d), d.ice, d.ville, d.pays]
        .filter(Boolean)
        .some((v) => String(v).toLowerCase().includes(q)),
    );
  }, [dossiers, query, statusTab]);

  if (selected) {
    return (
      <DataroomDetail
        dossier={selected}
        initialTab={deepLinkTab}
        onBack={() => {
          setSelected(null);
          // Nettoie le deep-link pour ne pas re-selectionner en revenant a la liste.
          if (deepLinkDossier || deepLinkTab) setSearchParams({}, { replace: true });
        }}
        canEdit={!!isEmploye}
        // Fix 2026-06-07 (BUG 3) — bouton "Supprimer le dataroom" visible
        // EMPLOYE ET SUPERVISEUR (aligne avec @PreAuthorize backend
        // hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR')).
        canDeleteDataroom={
          user?.role === 'EMPLOYE' || user?.role === 'SUPERVISEUR'
        }
      />
    );
  }

  return (
    <div className="space-y-5">
      <header className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <p className="text-xs text-fg-subtle">
            Dashboard / {isReadOnly ? 'Tous les Data Rooms' : 'Mes Data Rooms'}
          </p>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl md:text-3xl font-bold text-fg">
              {isReadOnly ? 'Tous les Data Rooms' : 'Mes Data Rooms'}
            </h1>
            {isReadOnly && (
              <span className="px-2 py-1 bg-accent/10 text-accent rounded text-xs font-bold">
                Consultation
              </span>
            )}
            <span className="rounded-full bg-bg-overlay px-2 py-0.5 text-xs font-medium text-fg-subtle">
              {filtered.length}
            </span>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-fg-subtle" />
            <TextField
              placeholder="Rechercher..."
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              className="w-56 pl-9"
            />
          </div>
        </div>
      </header>

      {error && (
        <div className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
          {error}
        </div>
      )}

      {/* Fix 2026-06-08 — Onglets ACTIF / SUSPENDU pour classer les datarooms */}
      <div className="flex items-center gap-1 border-b border-border">
        <button
          type="button"
          onClick={() => setStatusTab('ACTIVE')}
          className={`flex items-center gap-2 px-4 py-2 text-sm font-medium border-b-2 -mb-px transition ${
            statusTab === 'ACTIVE'
              ? 'border-accent text-accent'
              : 'border-transparent text-fg-subtle hover:text-fg'
          }`}
        >
          <span className="h-2 w-2 rounded-full bg-success" />
          Actifs
          <span className="rounded-full bg-bg-overlay px-2 py-0.5 text-[10px] font-bold text-fg-subtle">
            {activeCount}
          </span>
        </button>
        <button
          type="button"
          onClick={() => setStatusTab('SUSPENDED')}
          className={`flex items-center gap-2 px-4 py-2 text-sm font-medium border-b-2 -mb-px transition ${
            statusTab === 'SUSPENDED'
              ? 'border-danger text-danger'
              : 'border-transparent text-fg-subtle hover:text-fg'
          }`}
        >
          <span className="h-2 w-2 rounded-full bg-danger" />
          Suspendus
          <span className="rounded-full bg-bg-overlay px-2 py-0.5 text-[10px] font-bold text-fg-subtle">
            {suspendedCount}
          </span>
        </button>
      </div>

      {loading && dossiers.length === 0 ? (
        <div className="flex justify-center py-12">
          <Loader2 className="h-8 w-8 animate-spin text-accent" />
        </div>
      ) : filtered.length === 0 ? (
        <Card className="flex flex-col items-center justify-center gap-2 p-12 text-center">
          <FolderOpen className="h-10 w-10 text-fg-subtle" />
          <p className="text-sm text-fg-subtle">
            {statusTab === 'SUSPENDED'
              ? "Aucun Data Room suspendu."
              : "Aucun Data Room actif. Les Data Rooms sont crees automatiquement a partir d'un workflow (Creation SARL, Import dossier...)."}
          </p>
        </Card>
      ) : isReadOnly ? (
        /**
         * VUE SUPERVISEUR : la maquette groupe les Data Rooms par employe assignee.
         * NOTE BACKEND : le DTO DossierBrief n'expose pas (encore) l'identite
         * de l'employe assignee a chaque dossier. On affiche donc une vue plate
         * avec badge "Consultation" comme indique dans le brief. TODO Phase 7 :
         * exposer assigneeId/assigneeName sur DossierBrief pour groupement.
         */
        <DossierFlatList
          dossiers={filtered}
          onOpen={(d) => setSelected(d)}
        />
      ) : (
        <DossierFlatList
          dossiers={filtered}
          onOpen={(d) => setSelected(d)}
        />
      )}
    </div>
  );
}

// ============================================================
// Liste plate des dossiers (vue maquette)
// ============================================================

function DossierFlatList({
  dossiers,
  onOpen,
}: {
  dossiers: DossierBrief[];
  onOpen: (d: DossierBrief) => void;
}) {
  return (
    <div className="space-y-3">
      {dossiers.map((d) => (
        <DossierCard key={d.id} dossier={d} onOpen={() => onOpen(d)} />
      ))}
    </div>
  );
}

function DossierCard({
  dossier,
  onOpen,
}: {
  dossier: DossierBrief;
  onOpen: () => void;
}) {
  const user = useCurrentUser();
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

  async function handleToggleSuspend(e: React.MouseEvent) {
    e.stopPropagation();
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
    <div
      onClick={onOpen}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter') onOpen();
      }}
      className={`group bg-bg-raised rounded-xl p-4 md:p-5 shadow-sm border-2 transition cursor-pointer ${
        suspended
          ? 'border-danger/20 opacity-80'
          : 'border-border hover:border-accent/30 hover:shadow-md'
      }`}
    >
      <div className="flex items-center gap-4">
        <div
          className={`w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0 ${
            suspended ? 'bg-danger/10' : 'bg-accent/10'
          }`}
        >
          <FolderOpen
            className={`w-6 h-6 ${
              suspended ? 'text-danger' : 'text-accent'
            }`}
          />
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <h3 className="text-base font-bold text-fg truncate">
              {dossier.raisonSociale}
            </h3>
            <span
              className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                suspended
                  ? 'bg-danger/10 text-danger'
                  : 'bg-success/10 text-success'
              }`}
            >
              {suspended ? 'Suspendu' : 'Actif'}
            </span>
            <span className="text-[10px] font-mono text-fg-subtle">
              {dossier.id.slice(0, 8)}
            </span>
          </div>
          <p className="mt-0.5 text-sm text-fg-subtle">
            {displayFormeJuridique(dossier) ?? '—'}
            {dossier.origine === 'ETRANGERE'
              ? dossier.pays
                ? ` • ${dossier.pays}`
                : ''
              : dossier.ice
                ? ` • ICE ${dossier.ice}`
                : ''}
            {dossier.ville ? ` • ${dossier.ville}` : ''}
          </p>
        </div>

        <div className="hidden md:block text-center px-2">
          <p className="text-[10px] uppercase tracking-wide text-fg-subtle">
            Client
          </p>
          <p className="text-base font-bold text-fg">
            {settings?.accessCount ?? 0}
          </p>
        </div>

        <div
          className="flex items-center gap-1 flex-shrink-0"
          onClick={(e) => e.stopPropagation()}
        >
          {canEdit &&
            (suspended ? (
              <button
                type="button"
                disabled={busy}
                onClick={handleToggleSuspend}
                className="p-2 hover:bg-success/10 rounded-lg transition text-success disabled:opacity-50"
                title="Reactiver"
              >
                <PlayCircle className="w-4 h-4" />
              </button>
            ) : (
              <button
                type="button"
                disabled={busy}
                onClick={handleToggleSuspend}
                className="p-2 hover:bg-danger/10 rounded-lg transition text-danger disabled:opacity-50"
                title="Suspendre"
              >
                <PauseCircle className="w-4 h-4" />
              </button>
            ))}
          <span className="text-fg-subtle group-hover:text-accent pl-1">
            →
          </span>
        </div>
      </div>
    </div>
  );
}

// ============================================================
// Vue detail (preservee — fonctionnalite identique a V1)
// ============================================================

function DataroomDetail({
  dossier,
  onBack,
  canEdit,
  canDeleteDataroom,
  initialTab,
}: {
  dossier: DossierBrief;
  onBack: () => void;
  canEdit: boolean;
  canDeleteDataroom: boolean;
  initialTab?: string | null;
}) {
  const user = useCurrentUser();
  const VALID_TABS: Tab[] = ['juridique', 'comptable', 'fiscal', 'depots', 'demandes'];
  const [tab, setTab] = useState<Tab>(
    initialTab && VALID_TABS.includes(initialTab as Tab) ? (initialTab as Tab) : 'juridique',
  );
  const [settings, setSettings] = useState<DataroomSettings | null>(null);
  const [loadingSettings, setLoadingSettings] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [savingPerms, setSavingPerms] = useState(false);
  const [inviteOpen, setInviteOpen] = useState(false);
  // 2026-07-01 -- identite du CLIENT lie (section "Acces client")
  const [client, setClient] = useState<DossierClient | null>(null);
  const [loadingClient, setLoadingClient] = useState(true);
  // Sprint 7 / TASK 5 -- drawer "Activite client"
  const [accessLogOpen, setAccessLogOpen] = useState(false);
  // Suppression definitive du dataroom : PromptDialog anti-misclick (saisie de
  // la raison sociale exacte) remplace le couple window.prompt + window.confirm.
  const [deleteDataroomOpen, setDeleteDataroomOpen] = useState(false);
  const [deletingDataroom, setDeletingDataroom] = useState(false);
  // Retrait de l'acces client : ConfirmDialog nominatif (remplace window.confirm).
  const [removeClientOpen, setRemoveClientOpen] = useState(false);
  const [removingClient, setRemovingClient] = useState(false);

  const loadSettings = useCallback(async () => {
    setLoadingSettings(true);
    try {
      const s = await dataroomService.getSettings(dossier.id);
      setSettings(s);
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoadingSettings(false);
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
    loadSettings();
    loadClient();
  }, [loadSettings, loadClient]);

  async function handleToggleSuspend() {
    if (!settings) return;
    const next = settings.accessStatus !== 'SUSPENDED';
    try {
      const updated = await dataroomService.toggleSuspension(dossier.id, next);
      setSettings(updated);
    } catch (err) {
      setError(extractError(err).message);
    }
  }

  /**
   * Fix 2026-06-07 (BUG 6) — Retire DEFINITIVEMENT l'acces du client
   * au dossier (vs. suspension qui est reversible).
   *
   * Demande confirmation explicite : action irreversible cote liaison.
   * Le compte user du client n'est pas supprime (autres dossiers,
   * historique). Mais il perd l'acces a CE dossier jusqu'a re-invitation.
   */
  /**
   * Fix 2026-06-07 (BUG 3) — Suppression COMPLETE du dataroom.
   *
   * Action DESTRUCTIVE (vs. suspension reversible / retrait client qui
   * detache juste le compte). Supprime tout : documents juridiques /
   * comptables / fiscaux, demandes, snapshots, access-log, exercices,
   * echeances. Le dossier passe en RADIE si des tickets historiques le
   * referencent (preserve l'audit), sinon DELETE physique.
   *
   * Sécurité anti-misclick : saisie de la raison sociale exacte via
   * <PromptDialog> (validate = correspondance stricte), qui remplace le couple
   * window.prompt + window.confirm en collapsant la double-confirmation en un
   * seul garde-fou. 409 si un ticket actif (NOUVEAU / EN_COURS) bloque la
   * suppression -> message clair.
   */
  async function confirmDeleteDataroom() {
    setDeletingDataroom(true);
    try {
      await dataroomService.deleteDataroom(dossier.id);
      setDeleteDataroomOpen(false);
      onBack();
    } catch (err) {
      // Le backend renvoie 409 ConflictException si un ticket actif est rattache.
      setError(extractError(err).message);
      setDeleteDataroomOpen(false);
    } finally {
      setDeletingDataroom(false);
    }
  }

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

  async function handlePermissionChange(
    perm: 'download' | 'print' | 'depot',
    value: boolean,
  ) {
    if (!settings) return;
    setSavingPerms(true);
    try {
      const updated = await dataroomService.updatePermissions(
        dossier.id,
        perm === 'download' ? value : settings.permDownload,
        perm === 'print' ? value : settings.permPrint,
        perm === 'depot' ? value : settings.permDepot,
      );
      setSettings(updated);
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setSavingPerms(false);
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

  return (
    <div className="space-y-5">
      <button
        type="button"
        onClick={onBack}
        className="flex items-center gap-2 text-sm font-medium text-accent hover:underline"
      >
        <ArrowLeft className="h-4 w-4" /> Retour aux Data Rooms
      </button>

      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div className="flex items-center gap-3">
          <div className="rounded-xl bg-accent/10 p-3 text-accent">
            <Building2 className="h-6 w-6" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-fg">
              {dossier.raisonSociale}
            </h1>
            <p className="text-sm text-fg-subtle">
              {displayFormeJuridique(dossier) ?? '—'}
              {dossier.origine === 'ETRANGERE'
                ? dossier.pays
                  ? ` • ${dossier.pays}`
                  : ''
                : dossier.ice
                  ? ` • ICE ${dossier.ice}`
                  : ''}
              {dossier.ville ? ` • ${dossier.ville}` : ''}
            </p>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span
            className={`px-2 py-1 rounded-full text-xs font-medium ${
              suspended
                ? 'bg-danger/10 text-danger'
                : 'bg-success/10 text-success'
            }`}
          >
            {suspended ? 'Suspendu' : 'Actif'}
          </span>
          <Button
            variant="secondary"
            onClick={() => setAccessLogOpen(true)}
            className="border border-accent/30 text-accent hover:bg-accent/10 bg-bg-raised"
            title="Voir l'activite client (consultations, telechargements)"
          >
            <Activity className="mr-2 h-4 w-4" />
            Activite client
          </Button>
          {/* V9 — Transfert du dossier a un collegue (EMPLOYE: demande / SUPERVISEUR: direct) */}
          <TransferDossierButton
            dossierId={dossier.id}
            raisonSociale={dossier.raisonSociale}
            className="border border-accent/30 text-accent hover:bg-accent/10 bg-bg-raised"
          />
          {canEdit && (
            <Button
              variant="secondary"
              onClick={handleToggleSuspend}
              disabled={loadingSettings}
              className={
                suspended
                  ? 'border border-success/30 text-success hover:bg-success/10 bg-bg-raised'
                  : 'border border-danger/30 text-danger hover:bg-danger/10 bg-bg-raised'
              }
            >
              {suspended ? (
                <>
                  <PlayCircle className="mr-2 h-4 w-4" /> Reactiver
                </>
              ) : (
                <>
                  <PauseCircle className="mr-2 h-4 w-4" /> Suspendre
                </>
              )}
            </Button>
          )}
          {canDeleteDataroom && (
            <Button
              variant="secondary"
              onClick={() => setDeleteDataroomOpen(true)}
              disabled={loadingSettings}
              className="border-2 border-danger bg-danger/10 text-danger font-semibold hover:bg-danger hover:text-bg-raised"
              title="SUPPRIMER le dataroom (documents, demandes, historique). IRREVERSIBLE."
            >
              <Trash2 className="mr-2 h-4 w-4" /> Supprimer le dataroom
            </Button>
          )}
        </div>
      </div>

      {error && (
        <div className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
          {error}
        </div>
      )}

      {/* §A — bandeau explicite : archive legale, lecture seule pour tous. */}
      {archived && (
        <div
          className="flex items-start gap-3 rounded-xl border border-warning/40 bg-warning/10 px-4 py-3"
          data-testid="dataroom-readonly-banner"
        >
          <Lock className="mt-0.5 h-5 w-5 shrink-0 text-warning" />
          <div className="text-sm">
            <p className="font-semibold text-fg">
              {dataroomReadOnlyMessage(dossier.statut)}
            </p>
            <p className="text-fg-subtle">
              Consultation et telechargement restent possibles (archives
              juridiques). Ajout, remplacement, suppression, depot de pieces et
              nouvelles demandes sont desactives. Les echeances et alertes
              fiscales sont suspendues. Seul le workflow de liquidation peut
              encore deposer ses actes de cloture.
            </p>
          </div>
        </div>
      )}

      {settings && (
        <div className="bg-bg-overlay border border-border rounded-xl p-3 md:p-4">
          <div className="flex flex-wrap items-center gap-3 md:gap-4">
            <span className="text-xs font-bold text-fg">
              Permissions client :
            </span>
            {/* 2026-06-30 — "Consultation" n'est plus une pill cosmetique
                cochee/desactivee : c'est l'info non editable du socle (toujours
                active). Les vraies permissions assignables sont les 3 toggles. */}
            <span
              className="inline-flex items-center gap-1.5 rounded-full border border-success/30 bg-success/10 px-3 py-1 text-xs font-medium text-success"
              title="Le client peut toujours consulter ses documents"
            >
              <Eye className="h-3.5 w-3.5" /> Consultation (toujours active)
            </span>
            <PermPill
              icon={Download}
              label="Telecharger"
              checked={settings.permDownload}
              disabled={!canEdit || savingPerms}
              onChange={(v) => handlePermissionChange('download', v)}
            />
            <PermPill
              icon={Printer}
              label="Imprimer"
              checked={settings.permPrint}
              disabled={!canEdit || savingPerms}
              onChange={(v) => handlePermissionChange('print', v)}
            />
            <PermPill
              icon={Upload}
              label="Depot"
              checked={settings.permDepot}
              disabled={!canEdit || savingPerms}
              onChange={(v) => handlePermissionChange('depot', v)}
            />
            <div className="ml-auto flex items-center gap-2 text-xs text-fg-subtle">
              <span>
                Client avec acces :{' '}
                <strong className="text-fg">
                  {settings.accessCount}
                </strong>
              </span>
              {settings.lastAccessedAt && (
                <span className="text-fg-subtle">
                  Dernier le{' '}
                  {new Date(settings.lastAccessedAt).toLocaleDateString(
                    'fr-FR',
                  )}
                </span>
              )}
            </div>
          </div>
        </div>
      )}

      {/* 2026-07-01 — Acces client : identite du CLIENT lie (ou aucun) */}
      <div className="bg-bg-overlay border border-border rounded-xl p-3 md:p-4">
        <div className="flex flex-wrap items-center gap-3">
          <span className="text-xs font-bold text-fg">Acces client :</span>
          {loadingClient ? (
            <span className="text-xs text-fg-subtle">Chargement…</span>
          ) : client ? (
            <>
              <span
                className="inline-flex items-center gap-1.5 rounded-full border border-accent/30 bg-accent/10 px-3 py-1 text-xs font-medium text-accent"
                title="Client rattache a ce dossier"
              >
                <UserCheck className="h-3.5 w-3.5" />
                {client.firstName} {client.lastName}
                <span className="text-fg-subtle">({client.email})</span>
              </span>
              {client.status && client.status !== 'ACTIVE' && (
                <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-bold text-amber-700">
                  {client.status}
                </span>
              )}
              {canEdit && (
                <Button
                  variant="secondary"
                  onClick={() => setRemoveClientOpen(true)}
                  className="ml-auto border border-danger/30 text-danger hover:bg-danger/10 bg-bg-raised"
                  title="Retirer l'acces de ce client (vs suspendre qui est reversible)"
                >
                  <UserMinus className="mr-2 h-4 w-4" /> Retirer l'acces
                </Button>
              )}
            </>
          ) : (
            <>
              <span className="text-xs text-fg-subtle">Aucun client</span>
              {canEdit && (
                <Button
                  variant="primary"
                  onClick={() => setInviteOpen(true)}
                  className="ml-auto bg-accent text-bg-raised hover:bg-accent-hover"
                >
                  <UserPlus className="mr-2 h-4 w-4" /> Inviter un client
                </Button>
              )}
            </>
          )}
        </div>
      </div>

      <div className="inline-flex w-fit gap-1 rounded-xl border border-border bg-bg-overlay p-1">
        {TABS.map((t) => {
          const Icon = t.icon;
          const active = tab === t.value;
          return (
            <button
              key={t.value}
              type="button"
              onClick={() => setTab(t.value)}
              className={`flex items-center gap-2 rounded-lg px-3 md:px-4 py-2 text-sm font-medium transition ${
                active
                  ? `${t.activeBg} text-bg-raised shadow-sm`
                  : 'text-fg-subtle hover:text-fg'
              }`}
            >
              <Icon className="h-4 w-4" />
              {t.label}
            </button>
          );
        })}
      </div>

      {tab === 'juridique' && (
        <DossierJuridiqueTab
          dossierId={dossier.id}
          role={user?.role ?? null}
          readOnly={archived}
          readOnlyStatut={dossier.statut}
        />
      )}
      {tab === 'comptable' && (
        <DossierComptableTab
          dossierId={dossier.id}
          role={user?.role ?? null}
          readOnly={archived}
          readOnlyStatut={dossier.statut}
        />
      )}
      {tab === 'fiscal' && (
        <DossierFiscalTab
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

      {/* E2 — Activite (tracabilite) de ce dossier */}
      <div className="rounded-xl border border-border bg-bg-raised p-4">
        <EntityActivityPanel entityType="dossier" entityId={dossier.id} title="Activite du dossier" />
      </div>

      <InviteClientDrawer
        open={inviteOpen}
        dossierId={dossier.id}
        raisonSociale={dossier.raisonSociale}
        onClose={() => {
          setInviteOpen(false);
          // Un client vient peut-etre d'etre invite -> rafraichir la section.
          loadClient();
        }}
      />

      {/* Sprint 7 / TASK 5 -- drawer "Activite client" (admin only) */}
      <ClientAccessLogDrawer
        open={accessLogOpen}
        dossierId={dossier.id}
        raisonSociale={dossier.raisonSociale}
        onClose={() => setAccessLogOpen(false)}
      />

      {/* Suppression definitive : anti-misclick par saisie exacte de la raison sociale. */}
      <PromptDialog
        open={deleteDataroomOpen}
        onOpenChange={(o) => !o && setDeleteDataroomOpen(false)}
        title="SUPPRESSION DEFINITIVE du dataroom"
        description={
          <>
            Tous les documents (juridique, comptable, fiscal), demandes,
            snapshots et l'historique d'acces seront DETRUITS. Si des tickets
            historiques existent, le dossier passe en RADIE ; sinon il est
            supprime physiquement. Cette action est IRREVERSIBLE.
          </>
        }
        label={`Pour confirmer, tapez EXACTEMENT la raison sociale : ${dossier.raisonSociale.trim()}`}
        placeholder={dossier.raisonSociale.trim()}
        variant="danger"
        confirmLabel="Supprimer definitivement"
        loading={deletingDataroom}
        validate={(v) =>
          v.trim() !== dossier.raisonSociale.trim()
            ? `La raison sociale tapee ne correspond pas a "${dossier.raisonSociale.trim()}". Suppression annulee.`
            : null
        }
        onConfirm={confirmDeleteDataroom}
      />

      {/* Retrait de l'acces client : confirmation NOMINATIVE. */}
      <ConfirmDialog
        open={removeClientOpen}
        onOpenChange={(o) => !o && setRemoveClientOpen(false)}
        title="Retirer l'acces du client"
        description={
          client
            ? `Retirer l'acces de ${client.email} ? ${client.firstName} ${client.lastName} ne pourra plus consulter ce dossier sans une nouvelle invitation. Le compte reste actif (autres dossiers, historique).`
            : "Retirer l'acces du client a ce dossier ?"
        }
        variant="danger"
        confirmLabel="Retirer l'acces"
        loading={removingClient}
        onConfirm={confirmRemoveClientAccess}
      />
    </div>
  );
}

function PermPill({
  icon: Icon,
  label,
  checked,
  disabled,
  onChange,
}: {
  icon: typeof Eye;
  label: string;
  checked: boolean;
  disabled?: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`flex items-center gap-2 rounded-lg border px-3 py-1.5 text-xs font-medium transition ${
        checked
          ? 'border-success/30 bg-success/10 text-success'
          : 'border-border bg-bg-raised text-fg-subtle hover:border-border-hi'
      } ${disabled ? 'cursor-not-allowed opacity-60' : 'cursor-pointer'}`}
    >
      <span className="flex h-4 w-4 items-center justify-center rounded border border-current/30">
        {checked ? <Check className="h-3 w-3" /> : null}
      </span>
      <Icon className="h-3.5 w-3.5" />
      {label}
    </button>
  );
}

/**
 * NOTE V2 :
 * - L'archive (V1) est SUPPRIMEE. Les anciennes versions sont accessibles
 *   via l'historique des tickets clotures dans DossierJuridiqueTab.
 * - "Dossier Financier" (V1) est remplace par "Dossier Comptable" (V2)
 *   avec 6 categories x N annees.
 */
