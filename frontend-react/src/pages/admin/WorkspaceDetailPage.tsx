import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Activity,
  ArrowLeft,
  Briefcase,
  Building2,
  FileText,
  FolderKanban,
  HardDrive,
  Layers,
  ScrollText,
  Ticket,
  Users,
} from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { useToast } from '../../components/ui/Toast';
import { KpiCard } from '../../components/dashboard/KpiCard';
import { ChartCard } from '../../components/dashboard/charts/ChartCard';
import { DonutChart } from '../../components/dashboard/charts/DonutChart';
import { DistributionChart } from '../../components/dashboard/DistributionChart';
import { EvolutionChart } from '../../components/dashboard/EvolutionChart';
import { adminService } from '../../services/admin.service';
import type { WorkspaceDetail, WorkspaceStatusAction } from '../../types/admin';
import { chartTheme, statutTicketColors } from '../../lib/chartTheme';
import { FORFAIT_LABELS, STATUT_TICKET_LABELS, labelOf } from '../../lib/dashboardLabels';

const ROLE_LABELS: Record<string, string> = {
  SUPER_ADMIN: 'Super Admin',
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
  { label: string; verb: string; target: string; variant: 'primary' | 'danger' }
> = {
  activate: { label: 'Activer', verb: 'activer', target: 'ACTIVE', variant: 'primary' },
  suspend: { label: 'Suspendre', verb: 'suspendre', target: 'SUSPENDED', variant: 'danger' },
  deactivate: { label: 'Désactiver', verb: 'désactiver', target: 'DEACTIVATED', variant: 'danger' },
};

export function WorkspaceDetailPage() {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const navigate = useNavigate();
  const toast = useToast();
  const [ws, setWs] = useState<WorkspaceDetail | null>(null);
  const [error, setError] = useState(false);
  const [pending, setPending] = useState<WorkspaceStatusAction | null>(null);
  const [acting, setActing] = useState(false);

  const load = useCallback(async () => {
    if (!workspaceId) return;
    setError(false);
    setWs(null);
    try {
      setWs(await adminService.getWorkspaceDetail(workspaceId));
    } catch {
      setError(true);
    }
  }, [workspaceId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function confirmAction() {
    if (!pending || !workspaceId) return;
    setActing(true);
    try {
      await adminService.setWorkspaceStatus(workspaceId, pending);
      toast.success(`« ${ws?.name} » — ${ACTION_META[pending].verb} effectué.`);
      setWs((prev) => (prev ? { ...prev, statut: ACTION_META[pending].target } : prev));
      setPending(null);
    } catch {
      toast.error("L'action a échoué. Réessayez.");
    } finally {
      setActing(false);
    }
  }

  if (error) {
    return (
      <div className="space-y-4">
        <BackLink />
        <Card className="p-10 text-center">
          <p className="font-medium text-fg">Workspace introuvable</p>
          <p className="mt-1 text-sm text-fg-subtle">Les détails n'ont pas pu être récupérés.</p>
        </Card>
      </div>
    );
  }

  if (!ws) {
    return (
      <div className="space-y-4">
        <BackLink />
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="h-24 animate-pulse rounded-xl border border-border bg-bg-raised" />
          ))}
        </div>
        <div className="h-64 animate-pulse rounded-xl border border-border bg-bg-raised" />
      </div>
    );
  }

  const usersByRole = ws.usersByRole.map((c) => ({ key: c.label, label: ROLE_LABELS[c.label] ?? c.label, count: c.count }));
  const docsByType = ws.docsByType.map((c) => ({ key: c.label, label: DOC_LABELS[c.label] ?? c.label, count: c.count }));
  const ticketsByStatut = ws.ticketsByStatut.map((c) => ({
    key: c.label,
    label: labelOf(STATUT_TICKET_LABELS, c.label),
    count: c.count,
  }));
  const eventsByAction = ws.eventsByAction.map((c) => ({ label: c.label, count: c.count }));

  const availableActions: WorkspaceStatusAction[] =
    ws.statut === 'DEACTIVATED'
      ? ['activate']
      : ws.statut === 'SUSPENDED'
        ? ['activate', 'deactivate']
        : ['suspend', 'deactivate'];

  return (
    <div className="space-y-6">
      <BackLink />

      {/* En-tête */}
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex items-start gap-3">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-accent/10 text-accent">
            <Building2 className="h-6 w-6" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="font-heading text-2xl font-semibold text-fg">{ws.name}</h1>
              <StatusBadge statut={ws.statut} />
            </div>
            <p className="font-mono text-xs text-fg-subtle">{ws.code}</p>
          </div>
        </div>
        <div className="flex gap-2">
          {availableActions.map((a) => (
            <Button
              key={a}
              variant={ACTION_META[a].variant === 'danger' ? 'danger' : 'primary'}
              size="sm"
              onClick={() => setPending(a)}
            >
              {ACTION_META[a].label}
            </Button>
          ))}
        </div>
      </header>

      {/* KPIs */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
        <KpiCard label="Équipe" value={ws.employes} icon={<Briefcase className="h-4 w-4" />} />
        <KpiCard label="Clients" value={ws.clients} icon={<Users className="h-4 w-4" />} />
        <KpiCard label="Tickets" value={ws.ticketsTotal} icon={<Ticket className="h-4 w-4" />} />
        <KpiCard label="Dossiers" value={ws.dossiers} icon={<FolderKanban className="h-4 w-4" />} />
        <KpiCard
          label="Stockage"
          value={ws.storageBytes !== null ? formatStorage(ws.storageBytes) : '—'}
          hint={ws.storageFiles !== null ? `${ws.storageFiles} document${ws.storageFiles > 1 ? 's' : ''}` : undefined}
          icon={<HardDrive className="h-4 w-4" />}
        />
      </div>

      {/* Identité */}
      <Card className="p-6">
        <h3 className="mb-4 flex items-center gap-2 font-semibold text-fg">
          <FileText className="h-4 w-4 text-accent" /> Informations du cabinet
        </h3>
        <dl className="grid grid-cols-1 gap-x-8 gap-y-3 sm:grid-cols-2 lg:grid-cols-3">
          <Field label="Contact" value={ws.contactEmail} />
          <Field label="Forfait" value={ws.forfait ? labelOf(FORFAIT_LABELS, ws.forfait) : null} />
          <Field label="Créé le" value={new Date(ws.createdAt).toLocaleDateString('fr-FR')} />
          <Field label="Ville" value={ws.city} />
          <Field label="ICE" value={ws.ice} mono />
          <Field label="IF fiscal" value={ws.ifFiscal} mono />
          <Field label="RC" value={ws.rcNumber} mono />
          <Field label="Téléphone" value={ws.telephone} />
          <Field label="Site web" value={ws.siteWeb} />
          <Field label="Adresse" value={ws.adresse} />
          <Field
            label="Essai"
            value={
              ws.trialStatus === 'TRIAL_ACTIVE' && ws.trialEndsAt
                ? `Actif jusqu'au ${new Date(ws.trialEndsAt).toLocaleDateString('fr-FR')}`
                : ws.trialStatus ?? null
            }
          />
          <Field label="Abonnement" value={ws.subscriptionStatus} />
        </dl>
      </Card>

      {/* Répartitions */}
      <div className="grid gap-4 lg:grid-cols-3">
        <ChartCard title="Utilisateurs par rôle" icon={<Users className="h-4 w-4" />} empty={usersByRole.length === 0}>
          <DonutChart data={usersByRole} />
        </ChartCard>
        <ChartCard title="Documents par type" icon={<Layers className="h-4 w-4" />} empty={docsByType.every((d) => d.count === 0)}>
          <DonutChart data={docsByType} />
        </ChartCard>
        <ChartCard title="Tickets par statut" icon={<Ticket className="h-4 w-4" />} empty={ticketsByStatut.length === 0}>
          <DonutChart data={ticketsByStatut} colorByKey={statutTicketColors} />
        </ChartCard>
      </div>

      {/* Activité */}
      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard
          title="Activité (30 jours)"
          icon={<Activity className="h-4 w-4" />}
          empty={ws.activity30d.length === 0}
          emptyLabel="Aucune activité sur 30 jours"
        >
          <EvolutionChart data={ws.activity30d.map((d) => ({ day: d.day, count: d.count }))} color={chartTheme.accent} />
        </ChartCard>
        <ChartCard title="Principales actions (30j)" icon={<Layers className="h-4 w-4" />} empty={eventsByAction.length === 0}>
          <DistributionChart data={eventsByAction} color={chartTheme.accent} height={220} />
        </ChartCard>
      </div>

      {/* Événements récents */}
      <ChartCard
        title="Événements récents"
        icon={<ScrollText className="h-4 w-4" />}
        empty={ws.recentEvents.length === 0}
        emptyLabel="Aucun événement enregistré"
      >
        <ul className="divide-y divide-border">
          {ws.recentEvents.map((e) => (
            <li key={e.id} className="flex items-center gap-3 py-2 text-sm">
              <span className="rounded bg-bg-overlay px-2 py-0.5 font-mono text-[11px] text-fg-muted">{e.action}</span>
              {e.entityType && <span className="text-fg-subtle">{e.entityType}</span>}
              <span className="ml-auto text-xs text-fg-subtle">
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
        title={pending ? `${ACTION_META[pending].label} le workspace` : ''}
        description={
          pending ? `Confirmer ${ACTION_META[pending].verb} « ${ws.name} » (${ws.code}) ?` : undefined
        }
        confirmLabel={pending ? ACTION_META[pending].label : 'Confirmer'}
        variant={pending === 'activate' ? 'primary' : 'danger'}
        loading={acting}
        onConfirm={() => void confirmAction()}
      />
    </div>
  );

  function BackLink() {
    return (
      <button
        type="button"
        onClick={() => navigate('/admin/workspaces')}
        className="inline-flex items-center gap-1.5 text-sm text-fg-subtle transition hover:text-accent"
      >
        <ArrowLeft className="h-4 w-4" /> Retour aux workspaces
      </button>
    );
  }
}

function Field({ label, value, mono }: { label: string; value: string | null; mono?: boolean }) {
  return (
    <div>
      <dt className="text-[11px] uppercase tracking-wide text-fg-subtle">{label}</dt>
      <dd className={`mt-0.5 text-sm text-fg ${mono ? 'font-mono' : ''}`}>{value || '—'}</dd>
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
