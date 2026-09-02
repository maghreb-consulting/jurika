import { useEffect, useState } from 'react';
import {
  Activity,
  Building2,
  Database,
  HardDrive,
  Layers,
  LineChart as LineChartIcon,
  PieChart as PieChartIcon,
  ScrollText,
  ServerCog,
  TrendingUp,
  Users,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { KpiCard } from '../../components/dashboard/KpiCard';
import { ChartCard } from '../../components/dashboard/charts/ChartCard';
import { DonutChart } from '../../components/dashboard/charts/DonutChart';
import { TrendLineChart } from '../../components/dashboard/charts/TrendLineChart';
import { DistributionChart } from '../../components/dashboard/DistributionChart';
import { EvolutionChart } from '../../components/dashboard/EvolutionChart';
import { dashboardService } from '../../services/dashboard.service';
import type { SuperAdminDashboardDto } from '../../types/dashboard';
import { chartTheme } from '../../lib/chartTheme';
import {
  FORFAIT_LABELS,
  STATUT_WORKSPACE_LABELS,
  labelOf,
  formatMonth,
} from '../../lib/dashboardLabels';

/**
 * Console SUPER_ADMIN — etat de la plateforme en un coup d'oeil, sur donnees
 * 100% reelles (GET /api/v1/dashboards/super-admin). Aucune valeur codee en
 * dur : si une donnee manque, on affiche "—" plutot que d'inventer.
 *
 * Le SUPER_ADMIN n'accede PAS aux tickets/dossiers/documents metier (RG-U07).
 */
export function SuperAdminDashboard() {
  const [data, setData] = useState<SuperAdminDashboardDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let mounted = true;
    dashboardService
      .getSuperAdmin()
      .then((d) => mounted && setData(d))
      .catch(() => mounted && setError(true))
      .finally(() => mounted && setLoading(false));
    return () => {
      mounted = false;
    };
  }, []);

  return (
    <div className="space-y-6">
      <header>
        <p className="font-mono text-[11px] uppercase tracking-[0.18em] text-fg-subtle">
          Console Super Admin
        </p>
        <h1 className="font-heading text-2xl font-semibold text-fg">Plateforme JURIKA</h1>
        <p className="text-sm text-fg-subtle">
          Vue globale des cabinets clients. Pas d'accès aux tickets/dossiers (RG-U07).
        </p>
      </header>

      {loading && <DashboardSkeleton />}

      {!loading && error && (
        <Card className="p-8 text-center">
          <ServerCog className="mx-auto mb-3 h-8 w-8 text-fg-subtle" />
          <p className="font-medium text-fg">Tableau de bord indisponible</p>
          <p className="mt-1 text-sm text-fg-subtle">
            Le service de dashboard n'a pas répondu. Réessayez dans un instant.
          </p>
        </Card>
      )}

      {!loading && !error && data && <DashboardContent data={data} />}

      {/* Cartes de gestion : toujours accessibles, meme si les KPIs sont KO. */}
      <ManagementCards />
    </div>
  );
}

function DashboardContent({ data }: { data: SuperAdminDashboardDto }) {
  const actifs = (data.workspacesParStatut ?? []).find((c) => c.label === 'ACTIVE')?.count ?? null;

  const parForfait = (data.workspacesParForfait ?? []).map((c) => ({
    key: c.label,
    label: labelOf(FORFAIT_LABELS, c.label),
    count: c.count,
  }));
  const parStatut = (data.workspacesParStatut ?? []).map((c) => ({
    key: c.label,
    label: labelOf(STATUT_WORKSPACE_LABELS, c.label),
    count: c.count,
  }));
  const inscriptions = (data.signupsMensuels ?? []).map((m) => ({
    label: formatMonth(m.month),
    count: m.count,
  }));
  const topWs = (data.topWorkspacesByUsage ?? []).map((w) => ({
    label: w.name,
    count: w.eventsLast7d,
  }));

  return (
    <>
      {/* KPIs plateforme — toutes valeurs reelles issues du DTO. */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <KpiCard
          label="Workspaces"
          value={data.totalWorkspaces}
          hint={actifs !== null ? `${actifs} actif${actifs > 1 ? 's' : ''}` : `${data.activeWorkspaces30d} actifs sur 30j`}
          icon={<Building2 className="h-4 w-4" />}
        />
        <KpiCard
          label="Utilisateurs plateforme"
          value={data.totalUsers}
          hint="Tous rôles, tous cabinets"
          icon={<Users className="h-4 w-4" />}
        />
        <KpiCard
          label="Stockage utilisé"
          value={`${bytesToGB(data.storage.totalBytes)} Go`}
          hint={`${data.storage.fileCount} document${data.storage.fileCount > 1 ? 's' : ''}`}
          icon={<HardDrive className="h-4 w-4" />}
        />
        <KpiCard
          label="Événements critiques 24h"
          value={data.criticalAuditEvents24h.length}
          hint="Connexions échouées, 2FA, déblocages…"
          icon={<Activity className="h-4 w-4" />}
          variant={data.criticalAuditEvents24h.length > 0 ? 'danger' : 'default'}
        />
      </div>

      {/* Repartition des workspaces */}
      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard
          title="Workspaces par forfait"
          icon={<Layers className="h-4 w-4" />}
          empty={parForfait.length === 0}
        >
          <DonutChart data={parForfait} />
        </ChartCard>
        <ChartCard
          title="Workspaces par statut"
          icon={<PieChartIcon className="h-4 w-4" />}
          empty={parStatut.length === 0}
        >
          <DonutChart data={parStatut} />
        </ChartCard>
      </div>

      {/* Inscriptions dans le temps */}
      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard
          title="Inscriptions (12 mois)"
          icon={<LineChartIcon className="h-4 w-4" />}
          empty={inscriptions.every((m) => m.count === 0)}
        >
          <TrendLineChart
            data={inscriptions}
            xKey="label"
            series={[{ key: 'count', name: 'Inscriptions', color: chartTheme.accent }]}
          />
        </ChartCard>
        <ChartCard
          title="Signups (30 jours)"
          icon={<TrendingUp className="h-4 w-4" />}
          empty={data.signups30d.length === 0}
        >
          <EvolutionChart data={data.signups30d} color={chartTheme.success} />
        </ChartCard>
      </div>

      {/* Top workspaces + sante services */}
      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard
          title="Workspaces les plus actifs (7j)"
          icon={<Database className="h-4 w-4" />}
          empty={topWs.length === 0}
        >
          <DistributionChart data={topWs} color={chartTheme.accent} height={220} />
        </ChartCard>

        <ChartCard title="Santé des services" icon={<ServerCog className="h-4 w-4" />}>
          <ul className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            {Object.entries(data.servicesHealth).map(([svc, status]) => (
              <li key={svc} className="flex items-center gap-2 text-xs">
                <span className={`h-2 w-2 shrink-0 rounded-full ${healthDotClass(status)}`} />
                <span className="truncate font-medium text-fg-muted">{svc}</span>
                <span className="ml-auto text-fg-subtle">{healthLabel(status)}</span>
              </li>
            ))}
          </ul>
        </ChartCard>
      </div>

      {/* Evenements critiques 24h */}
      <ChartCard
        title="Événements critiques (24h)"
        icon={<Activity className="h-4 w-4" />}
        empty={data.criticalAuditEvents24h.length === 0}
        emptyLabel="Aucun événement critique sur les dernières 24h"
      >
        <ul className="divide-y divide-border">
          {data.criticalAuditEvents24h.slice(0, 12).map((e) => (
            <li key={e.id} className="flex items-center gap-3 py-2 text-sm">
              <span className="rounded bg-danger/10 px-2 py-0.5 font-mono text-[11px] text-danger">
                {e.action}
              </span>
              {e.entityType && <span className="text-fg-muted">{e.entityType}</span>}
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
    </>
  );
}

function ManagementCards() {
  const cards = [
    {
      icon: Building2,
      title: 'Workspaces',
      desc: 'Gérer les cabinets : activer, suspendre, désactiver.',
      to: '/admin/workspaces',
      cta: 'Voir les workspaces',
    },
    {
      icon: Users,
      title: 'Utilisateurs',
      desc: 'Vue cross-workspaces. Reset MDP, suspension.',
      to: '/admin/users',
      cta: 'Voir les utilisateurs',
    },
    {
      icon: ScrollText,
      title: "Journal d'audit",
      desc: 'Toutes les actions plateforme (auth, transferts).',
      to: '/admin/audit',
      cta: "Consulter l'audit",
    },
  ];
  return (
    <div className="grid gap-4 lg:grid-cols-3">
      {cards.map((c) => (
        <Card key={c.to} interactive className="flex flex-col p-6">
          <div className="mb-3 flex h-11 w-11 items-center justify-center rounded-lg bg-accent/10 text-accent">
            <c.icon className="h-5 w-5" />
          </div>
          <h3 className="font-semibold text-fg">{c.title}</h3>
          <p className="mb-4 mt-1 flex-1 text-sm text-fg-subtle">{c.desc}</p>
          <Link to={c.to}>
            <Button variant="secondary" className="w-full">
              {c.cta}
            </Button>
          </Link>
        </Card>
      ))}
    </div>
  );
}

function DashboardSkeleton() {
  return (
    <div className="space-y-4" aria-hidden>
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="h-24 animate-pulse rounded-xl border border-border bg-bg-raised" />
        ))}
      </div>
      <div className="grid gap-4 lg:grid-cols-2">
        {Array.from({ length: 2 }).map((_, i) => (
          <div key={i} className="h-56 animate-pulse rounded-xl border border-border bg-bg-raised" />
        ))}
      </div>
    </div>
  );
}

function bytesToGB(n: number): string {
  return (n / (1024 * 1024 * 1024)).toFixed(2);
}

function healthDotClass(status: string): string {
  switch (status) {
    case 'UP':
      return 'bg-emerald-500';
    case 'DEGRADED':
      return 'bg-warning';
    case 'DOWN':
      return 'bg-danger';
    default:
      return 'bg-slate-400'; // UNKNOWN
  }
}

function healthLabel(status: string): string {
  switch (status) {
    case 'UP':
      return 'En ligne';
    case 'DEGRADED':
      return 'Dégradé';
    case 'DOWN':
      return 'Hors ligne';
    default:
      return 'Inconnu';
  }
}
