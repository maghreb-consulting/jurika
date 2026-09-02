import { useEffect, useState } from 'react';
import { Activity, HardDrive, Layers, LineChart, PieChart, Users } from 'lucide-react';
import { dashboardService } from '../../services/dashboard.service';
import type { SuperAdminDashboardDto } from '../../types/dashboard';
import { EvolutionChart } from './EvolutionChart';
import { DonutChart } from './charts/DonutChart';
import { TrendLineChart } from './charts/TrendLineChart';
import { ChartCard } from './charts/ChartCard';
import { KpiCard } from './KpiCard';
import { chartTheme } from '../../lib/chartTheme';
import {
  FORFAIT_LABELS,
  STATUT_WORKSPACE_LABELS,
  labelOf,
  formatMonth,
} from '../../lib/dashboardLabels';

function bytesToGB(n: number): string {
  return (n / (1024 * 1024 * 1024)).toFixed(2);
}

export function Sprint10SuperAdminPanel() {
  const [data, setData] = useState<SuperAdminDashboardDto | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    dashboardService
      .getSuperAdmin()
      .then((d) => mounted && setData(d))
      .catch(() => mounted && setData(null))
      .finally(() => mounted && setLoading(false));
    return () => {
      mounted = false;
    };
  }, []);

  if (loading) {
    return (
      <div className="mt-6 flex h-24 items-center justify-center rounded-xl border border-border bg-bg-raised">
        <span className="h-5 w-5 animate-spin rounded-full border-2 border-fg-subtle border-t-transparent" />
      </div>
    );
  }
  if (!data) return null;

  // Guards `?? []` : compat snapshots caches anterieurs (sans ces series).
  const parForfait = (data.workspacesParForfait ?? []).map((c) => ({
    label: labelOf(FORFAIT_LABELS, c.label),
    count: c.count,
  }));
  const parStatut = (data.workspacesParStatut ?? []).map((c) => ({
    label: labelOf(STATUT_WORKSPACE_LABELS, c.label),
    count: c.count,
  }));
  const inscriptions = (data.signupsMensuels ?? []).map((m) => ({
    label: formatMonth(m.month),
    count: m.count,
  }));

  return (
    <div className="mt-6 space-y-4">
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <KpiCard
          label="Workspaces actifs (30j)"
          value={data.activeWorkspaces30d}
          icon={<Users className="h-4 w-4" />}
        />
        <KpiCard
          label="Signups (30j)"
          value={data.signups30d.reduce((acc, d) => acc + d.count, 0)}
        />
        <KpiCard
          label="Documents stockés"
          value={data.storage.fileCount}
          hint={`${bytesToGB(data.storage.totalBytes)} GB`}
          icon={<HardDrive className="h-4 w-4" />}
        />
        <KpiCard
          label="Événements critiques 24h"
          value={data.criticalAuditEvents24h.length}
          icon={<Activity className="h-4 w-4" />}
          variant={data.criticalAuditEvents24h.length > 0 ? 'danger' : 'default'}
        />
      </div>

      {/* Repartition des workspaces : par forfait + par statut */}
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
          icon={<PieChart className="h-4 w-4" />}
          empty={parStatut.length === 0}
        >
          <DonutChart data={parStatut} />
        </ChartCard>
      </div>

      {/* Evolution des inscriptions (12 mois) */}
      <ChartCard
        title="Évolution des inscriptions (12 mois)"
        icon={<LineChart className="h-4 w-4" />}
        empty={inscriptions.every((m) => m.count === 0)}
      >
        <TrendLineChart
          data={inscriptions}
          xKey="label"
          series={[{ key: 'count', name: 'Inscriptions', color: chartTheme.accent }]}
        />
      </ChartCard>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="rounded-xl border border-border bg-bg-raised p-4">
          <h4 className="mb-2 text-sm font-semibold text-fg">Signups 30j</h4>
          {data.signups30d.length === 0 ? (
            <p className="py-8 text-center text-xs text-fg-subtle">Aucun signup récent</p>
          ) : (
            <EvolutionChart data={data.signups30d} color="#10B981" />
          )}
        </div>

        <div className="rounded-xl border border-border bg-bg-raised p-4">
          <h4 className="mb-2 text-sm font-semibold text-fg">Santé services</h4>
          <ul className="grid grid-cols-2 gap-2">
            {Object.entries(data.servicesHealth).map(([svc, status]) => (
              <li key={svc} className="flex items-center gap-2 text-xs">
                <span
                  className={`h-2 w-2 rounded-full ${
                    status === 'UP'
                      ? 'bg-emerald-500'
                      : status === 'DEGRADED'
                        ? 'bg-warning/100'
                        : 'bg-danger'
                  }`}
                />
                <span className="font-medium text-fg-muted">{svc}</span>
                <span className="ml-auto text-fg-subtle">{status}</span>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}
