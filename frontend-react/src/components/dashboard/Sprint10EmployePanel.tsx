import { useEffect, useState } from 'react';
import { Briefcase, CalendarClock, Calendar, Loader2 } from 'lucide-react';
import { dashboardService } from '../../services/dashboard.service';
import type { EmployeDashboardDto } from '../../types/dashboard';
import { echeancesByWeek } from '../../lib/employeCharts';
import { KpiCard } from './KpiCard';
import { ChartCard } from './charts/ChartCard';
import { DistributionChart } from './DistributionChart';
import { chartTheme } from '../../lib/chartTheme';

export function Sprint10EmployePanel() {
  const [data, setData] = useState<EmployeDashboardDto | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    dashboardService
      .getEmploye()
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
        <Loader2 className="h-5 w-5 animate-spin text-fg-subtle" />
      </div>
    );
  }
  if (!data) return null;

  const echeancesSemaine = echeancesByWeek(data.mesEcheancesAssignees);

  return (
    <div className="mt-6 space-y-4">
      <ChartCard
        title="Échéances des 30 prochains jours"
        icon={<CalendarClock className="h-4 w-4" />}
        empty={echeancesSemaine.length === 0}
        emptyLabel="Aucune échéance dans les 30 prochains jours"
      >
        <DistributionChart
          data={echeancesSemaine.map((b) => ({ label: b.label, count: b.count }))}
          color={chartTheme.accent}
          height={180}
        />
      </ChartCard>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <KpiCard
          label="Mes tickets ouverts"
          value={data.mesTicketsOuverts}
          icon={<Briefcase className="h-4 w-4" />}
        />
        <KpiCard
          label="Échéances à venir"
          value={data.mesEcheancesAssignees.length}
          icon={<Calendar className="h-4 w-4" />}
          variant={data.mesEcheancesAssignees.length > 0 ? 'warning' : 'default'}
        />
        <KpiCard
          label="Activité Data Room récente"
          value={data.dernieresOperationsDataroom.length}
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="rounded-xl border border-border bg-bg-raised p-4">
          <h4 className="mb-2 text-sm font-semibold text-fg">Mes derniers tickets</h4>
          {data.derniersTickets.length === 0 ? (
            <p className="py-6 text-center text-xs text-fg-subtle">Aucun ticket récent</p>
          ) : (
            <ul className="divide-y divide-border">
              {data.derniersTickets.slice(0, 5).map((t) => (
                <li key={t.id} className="flex items-center justify-between py-2 text-sm">
                  <span className="truncate">
                    <span className="font-mono text-xs text-fg-subtle">{t.reference}</span>{' '}
                    {t.titre}
                  </span>
                  <span className="rounded-full bg-bg-overlay px-2 py-0.5 text-[10px] font-semibold text-fg-muted">
                    {t.statut}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="rounded-xl border border-border bg-bg-raised p-4">
          <h4 className="mb-2 text-sm font-semibold text-fg">Échéances assignées</h4>
          {data.mesEcheancesAssignees.length === 0 ? (
            <p className="py-6 text-center text-xs text-fg-subtle">Aucune échéance prochaine</p>
          ) : (
            <ul className="divide-y divide-border">
              {data.mesEcheancesAssignees.slice(0, 5).map((e) => (
                <li key={e.id} className="flex items-center justify-between py-2 text-sm">
                  <span>{e.typeEcheance}</span>
                  <span className="text-xs text-fg-subtle">
                    {e.dateEcheance
                      ? new Date(e.dateEcheance).toLocaleDateString('fr-FR')
                      : '—'}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}
