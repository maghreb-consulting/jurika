import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Activity,
  ArrowLeft,
  Briefcase,
  FileText,
  FolderKanban,
  HardDrive,
  Layers,
  ScrollText,
  Ticket,
  Users,
} from 'lucide-react';
import { Page } from '../../components/layout/Page';
import { Card } from '../../components/ui/Card';
import { ErrorState } from '../../components/ui/ErrorState';
import { CardSkeleton, SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { useToast } from '../../components/ui/useToast';
import { KpiCard } from '../../components/dashboard/KpiCard';
import { ChartCard } from '../../components/dashboard/charts/ChartCard';
import { DonutChart } from '../../components/dashboard/charts/DonutChart';
import { DistributionChart } from '../../components/dashboard/DistributionChart';
import { EvolutionChart } from '../../components/dashboard/EvolutionChart';
import { adminService } from '../../services/admin.service';
import type { WorkspaceDetail, WorkspaceStatusAction } from '../../types/admin';
import { chartTheme, statutTicketColors } from '../../lib/chartTheme';
import { FORFAIT_LABELS, STATUT_TICKET_LABELS, labelOf } from '../../lib/dashboardLabels';
import { extractError } from '../../lib/api';
import {
  STATUT_ABONNEMENT_LABELS,
  STATUT_ESSAI_LABELS,
  libelleCode,
} from '../../lib/adminLabels';
import { TexteAide } from '../../components/ui/Aide';
import { libelleAction } from '../../lib/libelleAction';

const ROLE_LABELS: Record<string, string> = {
  SUPER_ADMIN: 'Super-administrateur',
  SUPERVISEUR: 'Superviseur',
  EMPLOYE: 'Employé',
  CLIENT: 'Client',
};
const DOC_LABELS: Record<string, string> = {
  JURIDIQUE: 'Juridique',
  COMPTABLE: 'Comptable',
  FISCAL: 'Fiscal',
};
const ACTION_META: Record<
  WorkspaceStatusAction,
  { label: string; verb: string; done: string; target: string; variant: 'primary' | 'danger' }
> = {
  activate: { label: 'Activer', verb: 'activer', done: 'activé', target: 'ACTIVE', variant: 'primary' },
  suspend: { label: 'Suspendre', verb: 'suspendre', done: 'suspendu', target: 'SUSPENDED', variant: 'danger' },
  // Lot L7 (E5), RG-PLN-07 : la désactivation archive le cabinet (rien n'est supprimé ; le
  // superviseur garde l'accès pour souscrire de nouveau, exporter ou demander la suppression).
  deactivate: { label: 'Archiver', verb: 'archiver', done: 'archivé', target: 'ARCHIVED', variant: 'danger' },
};

export function WorkspaceDetailPage() {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const navigate = useNavigate();
  const toast = useToast();
  const [ws, setWs] = useState<WorkspaceDetail | null>(null);
  const [error, setError] = useState(false);
  const [pending, setPending] = useState<WorkspaceStatusAction | null>(null);
  const [acting, setActing] = useState(false);

  // Recuperation seule (etat pose dans la promesse) : appelee au montage et par " Reessayer ".
  const recuperer = useCallback(() => {
    if (!workspaceId) return Promise.resolve();
    return adminService
      .getWorkspaceDetail(workspaceId)
      .then((d) => {
        setWs(d);
        setError(false);
      })
      .catch(() => setError(true));
  }, [workspaceId]);

  useEffect(() => {
    void recuperer();
  }, [recuperer]);

  function load() {
    setError(false);
    setWs(null);
    return recuperer();
  }

  async function confirmAction() {
    if (!pending || !workspaceId) return;
    setActing(true);
    try {
      await adminService.setWorkspaceStatus(workspaceId, pending);
      toast.success(`Cabinet « ${ws?.name} » ${ACTION_META[pending].done}.`);
      setWs((prev) => (prev ? { ...prev, statut: ACTION_META[pending].target } : prev));
      setPending(null);
    } catch (e) {
      toast.error(`L’action a échoué : ${extractError(e).message}`);
    } finally {
      setActing(false);
    }
  }

  const fil = (nom: string) => [{ libelle: 'Accueil', to: '/dashboard' }, { libelle: 'Cabinets', to: '/admin/workspaces' }, { libelle: nom }];

  if (error) {
    return (
      <Page titre="Fiche du cabinet" fil={fil('Fiche du cabinet')}>
        <ErrorState
          title="Cabinet introuvable"
          message="La fiche de ce cabinet n’a pas pu être récupérée. Revenez à la liste des cabinets et rouvrez-la ; si le problème persiste, vérifiez l’état des services."
          onRetry={() => void load()}
        />
        <Button type="button" variant="secondary" className="mt-4" icon={<ArrowLeft />} onClick={() => navigate('/admin/workspaces')}>
          Retour aux cabinets
        </Button>
      </Page>
    );
  }

  if (!ws) {
    return (
      <Page titre="Fiche du cabinet" fil={fil('Fiche du cabinet')}>
        <SqueletteZone libelle="Chargement de la fiche du cabinet…" className="space-y-4">
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
            {Array.from({ length: 5 }).map((_, i) => (
              <CardSkeleton key={i} className="h-[6.5rem]" />
            ))}
          </div>
          <CardSkeleton className="h-64" />
        </SqueletteZone>
      </Page>
    );
  }

  const usersByRole = (ws.usersByRole ?? []).map((c) => ({ key: c.label, label: ROLE_LABELS[c.label] ?? c.label, count: c.count }));
  const docsByType = (ws.docsByType ?? []).map((c) => ({ key: c.label, label: DOC_LABELS[c.label] ?? c.label, count: c.count }));
  const ticketsByStatut = (ws.ticketsByStatut ?? []).map((c) => ({
    key: c.label,
    label: labelOf(STATUT_TICKET_LABELS, c.label),
    count: c.count,
  }));
  const activite = ws.activity30d ?? [];
  const evenements = ws.recentEvents ?? [];
  // Lot L6b (E9) : le serveur rend deja le libelle de l'action comme categorie.
  const eventsByAction = (ws.eventsByAction ?? []).map((c) => ({ label: c.label, count: c.count }));

  const availableActions: WorkspaceStatusAction[] =
    ws.statut === 'DEACTIVATED' || ws.statut === 'ARCHIVED'
      ? ['activate']
      : ws.statut === 'SUSPENDED'
        ? ['activate', 'deactivate']
        : ['suspend', 'deactivate'];

  return (
    <Page
      titre={ws.name}
      fil={fil(ws.name)}
      etat={<StatusBadge statut={ws.statut} />}
      description={<span className="font-mono">{ws.code}</span>}
      actions={availableActions.map((a) => (
        <Button key={a} type="button" variant={ACTION_META[a].variant === 'danger' ? 'danger' : 'primary'} onClick={() => setPending(a)}>
          {ACTION_META[a].label}
        </Button>
      ))}
      aide={
        <TexteAide cle="admin-fiche-cabinet" titre="Fiche du cabinet">
          <p>
            Cette fiche réunit l’identité du cabinet, son forfait, ses compteurs (équipe, clients, tickets,
            dossiers, stockage) et son activité des 30 derniers jours. Les boutons en haut à droite changent
            son statut ; une confirmation vous est demandée. Le contenu des dossiers n’est jamais affiché ici.
          </p>
        </TexteAide>
      }
    >
    <div className="space-y-6">
      {/* KPIs */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
        <KpiCard label="Équipe" value={ws.employes} icon={<Briefcase />} />
        <KpiCard label="Clients" value={ws.clients} icon={<Users />} />
        <KpiCard label="Tickets" value={ws.ticketsTotal} icon={<Ticket />} />
        <KpiCard label="Dossiers" value={ws.dossiers} icon={<FolderKanban />} />
        <KpiCard
          label="Stockage"
          value={typeof ws.storageBytes === 'number' ? formatStorage(ws.storageBytes) : null}
          hint={typeof ws.storageFiles === 'number' ? `${ws.storageFiles} document${ws.storageFiles > 1 ? 's' : ''}` : undefined}
          icon={<HardDrive />}
        />
      </div>

      {/* Identité */}
      <Card
        title={
          <span className="inline-flex items-center gap-2">
            <FileText className="h-4 w-4 text-fg-subtle" aria-hidden="true" /> Informations du cabinet
          </span>
        }
      >
        <dl className="grid grid-cols-1 gap-x-8 gap-y-3 sm:grid-cols-2 lg:grid-cols-3">
          <Field label="Contact" value={ws.contactEmail} />
          <Field label="Forfait" value={ws.forfait ? labelOf(FORFAIT_LABELS, ws.forfait) : null} />
          <Field label="Créé le" value={new Date(ws.createdAt).toLocaleDateString('fr-FR')} />
          <Field label="Ville" value={ws.city} />
          <Field label="ICE" value={ws.ice} mono />
          <Field label="IF (identifiant fiscal)" value={ws.ifFiscal} mono />
          <Field label="RC" value={ws.rcNumber} mono />
          <Field label="Téléphone" value={ws.telephone} />
          <Field label="Site web" value={ws.siteWeb} />
          <Field label="Adresse" value={ws.adresse} />
          <Field
            label="Essai"
            value={
              ws.trialStatus === 'TRIAL_ACTIVE' && ws.trialEndsAt
                ? `En cours jusqu’au ${new Date(ws.trialEndsAt).toLocaleDateString('fr-FR')}`
                : libelleCode(STATUT_ESSAI_LABELS, ws.trialStatus)
            }
          />
          <Field label="Abonnement" value={libelleCode(STATUT_ABONNEMENT_LABELS, ws.subscriptionStatus)} />
        </dl>
      </Card>

      {/* Répartitions */}
      <div className="grid gap-4 lg:grid-cols-3">
        <ChartCard title="Utilisateurs par rôle" icon={<Users />} empty={usersByRole.length === 0}>
          <DonutChart data={usersByRole} />
        </ChartCard>
        <ChartCard title="Documents par type" icon={<Layers />} empty={docsByType.every((d) => d.count === 0)}>
          <DonutChart data={docsByType} />
        </ChartCard>
        <ChartCard title="Tickets par statut" icon={<Ticket />} empty={ticketsByStatut.length === 0}>
          <DonutChart data={ticketsByStatut} colorByKey={statutTicketColors} />
        </ChartCard>
      </div>

      {/* Activité */}
      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard
          title="Activité (30 jours)"
          icon={<Activity />}
          empty={activite.length === 0}
          emptyLabel="Aucune activité sur 30 jours"
        >
          <EvolutionChart data={activite.map((d) => ({ day: d.day, count: d.count }))} color={chartTheme.accent} />
        </ChartCard>
        <ChartCard title="Principales actions (30 jours)" icon={<Layers />} empty={eventsByAction.length === 0}>
          <DistributionChart data={eventsByAction} color={chartTheme.accent} height={220} />
        </ChartCard>
      </div>

      {/* Événements récents */}
      <ChartCard
        title="Événements récents"
        icon={<ScrollText />}
        empty={evenements.length === 0}
        emptyLabel="Aucun événement enregistré"
      >
        <ul className="divide-y divide-border">
          {evenements.map((e) => (
            <li key={e.id} className="flex flex-wrap items-center gap-3 py-2 text-sm">
              <Badge variant="neutral">{libelleAction(e.actionLabel, e.action)}</Badge>
              {e.entityType && <span className="text-fg-muted">{e.entityType}</span>}
              <span className="ml-auto text-xs tabular-nums text-fg-subtle">
                {new Date(e.createdAt).toLocaleString('fr-FR', {
                  day: '2-digit',
                  month: '2-digit',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </span>
            </li>
          ))}
        </ul>
      </ChartCard>

      <ConfirmDialog
        open={pending !== null}
        onOpenChange={(o) => !o && setPending(null)}
        title={pending ? `${ACTION_META[pending].label} le cabinet` : ''}
        description={
          pending
            ? `Voulez-vous vraiment ${ACTION_META[pending].verb} le cabinet « ${ws.name} » (${ws.code}) ?`
              + (pending === 'deactivate'
                ? ' Les accès sont fermés, rien n’est supprimé : le superviseur garde l’accès pour souscrire de nouveau, exporter les données ou demander leur suppression.'
                : '')
            : undefined
        }
        confirmLabel={pending ? ACTION_META[pending].label : 'Confirmer'}
        variant={pending === 'activate' ? 'primary' : 'danger'}
        loading={acting}
        onConfirm={() => void confirmAction()}
      />
    </div>
    </Page>
  );
}

function Field({ label, value, mono }: { label: string; value: string | null; mono?: boolean }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-fg-muted">{label}</dt>
      <dd className={`mt-0.5 text-sm ${value ? 'text-fg' : 'text-fg-subtle'} ${mono ? 'font-mono' : ''}`}>{value || 'Non renseigné'}</dd>
    </div>
  );
}

function StatusBadge({ statut }: { statut: string }) {
  switch (statut) {
    case 'ACTIVE':
      return <Badge variant="success">Actif</Badge>;
    case 'ESSAI':
      return <Badge variant="info">Essai</Badge>;
    case 'SUSPENDED':
      return <Badge variant="warning">Suspendu</Badge>;
    case 'DEACTIVATED':
      return <Badge variant="danger">Désactivé</Badge>;
    case 'PENDING_VERIFICATION':
      return <Badge variant="neutral">En attente</Badge>;
    default:
      return <Badge variant="neutral">{statut}</Badge>;
  }
}

function formatStorage(n: number): string {
  const gb = n / (1024 * 1024 * 1024);
  return gb >= 0.01 ? `${gb.toFixed(2)} Go` : `${(n / (1024 * 1024)).toFixed(0)} Mo`;
}
