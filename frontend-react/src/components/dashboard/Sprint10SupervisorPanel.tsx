import { useEffect, useState } from 'react';
import { AlertTriangle, Calendar, Inbox, PieChart, TrendingUp, Users } from 'lucide-react';
import { dashboardService } from '../../services/dashboard.service';
import type { SuperviseurDashboardDto } from '../../types/dashboard';
import { EvolutionChart } from './EvolutionChart';
import { DistributionChart } from './DistributionChart';
import { DonutChart } from './charts/DonutChart';
import { TrendLineChart } from './charts/TrendLineChart';
import { ChartCard } from './charts/ChartCard';
import { KpiCard } from './KpiCard';
import { chartTheme, statutTicketColors, statutDemandeColors } from '../../lib/chartTheme';
import {
  STATUT_TICKET_LABELS,
  STATUT_DEMANDE_LABELS,
  labelOf,
  formatMonth,
} from '../../lib/dashboardLabels';

export function Sprint10SupervisorPanel() {
  const [data, setData] = useState<SuperviseurDashboardDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    dashboardService
      .getSuperviseur()
      .then((d) => mounted && setData(d))
      .catch((e) => mounted && setError(String(e?.message ?? e)))
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
  if (error || !data) return null; // dégradation gracieuse si dashboard-service KO

  // Guards `?? []` : un snapshot cache anterieur (Sprint 10) peut ne pas
  // contenir les nouvelles series -> etat vide propre plutot que crash.
  const parStatut = (data.ticketsParStatut ?? []).map((c) => ({
    key: c.label,
    label: labelOf(STATUT_TICKET_LABELS, c.label),
    count: c.count,
  }));
  const charge = (data.chargeParEmploye ?? []).map((e) => ({
    label: e.email?.split('@')[0] ?? '—',
    count: e.ticketsOuverts,
  }));
  const mensuel = (data.evolutionMensuelle ?? []).map((m) => ({
    label: formatMonth(m.month),
    crees: m.crees,
    clotures: m.clotures,
  }));
  const demandes = (data.demandesParStatut ?? []).map((c) => ({
    key: c.label,
    label: labelOf(STATUT_DEMANDE_LABELS, c.label),
    count: c.count,
  }));

  return (
    <div className="mt-6 space-y-4">
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <KpiCard label="Tickets ouverts" value={data.ticketsOuverts} variant="warning" />
        <KpiCard label="Clos 30j" value={data.ticketsClos30d} variant="success" />
        <KpiCard
          label="Temps moyen clôture"
          value={`${data.tempsMoyenClotureHeures.toFixed(1)} h`}
        />
        <KpiCard
          label="Dossiers à risque"
          value={data.dossiersARisque.length}
          variant={data.dossiersARisque.length > 0 ? 'danger' : 'default'}
        />
      </div>

      {/* Repartition tickets par statut + charge par employe */}
      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard
          title="Tickets par statut"
          icon={<PieChart className="h-4 w-4" />}
          empty={parStatut.length === 0}
        >
          <DonutChart data={parStatut} colorByKey={statutTicketColors} />
        </ChartCard>

        <ChartCard
          title="Charge par employé (tickets ouverts)"
          icon={<Users className="h-4 w-4" />}
          empty={charge.length === 0}
        >
          <DistributionChart data={charge} color={chartTheme.fgMuted} height={200} />
        </ChartCard>
      </div>

      {/* Evolution mensuelle (crees vs clotures) + demandes clients par statut */}
      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard
          title="Évolution mensuelle (6 mois)"
          icon={<TrendingUp className="h-4 w-4" />}
          empty={mensuel.every((m) => m.crees === 0 && m.clotures === 0)}
        >
          <TrendLineChart
            data={mensuel}
            xKey="label"
            series={[
              { key: 'crees', name: 'Créés', color: chartTheme.accent },
              { key: 'clotures', name: 'Clôturés', color: chartTheme.success },
            ]}
          />
        </ChartCard>

        <ChartCard
          title="Demandes clients par statut"
          icon={<Inbox className="h-4 w-4" />}
          empty={demandes.length === 0}
        >
          <DonutChart data={demandes} colorByKey={statutDemandeColors} />
        </ChartCard>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="rounded-xl border border-border bg-bg-raised p-4">
          <h4 className="mb-2 text-sm font-semibold text-fg">Évolution tickets (30j)</h4>
          {data.evolutionTickets30j.length === 0 ? (
            <p className="py-8 text-center text-xs text-fg-subtle">Aucune donnée</p>
          ) : (
            <EvolutionChart data={data.evolutionTickets30j} />
          )}
        </div>

        <div className="rounded-xl border border-border bg-bg-raised p-4">
          <div className="mb-2 flex items-center gap-2">
            <Calendar className="h-4 w-4 text-amber-500" />
            <h4 className="text-sm font-semibold text-fg">Échéances DGI</h4>
          </div>
          {data.echeancesJ30J15J3.every((b) => b.items.length === 0) ? (
            <p className="py-8 text-center text-xs text-fg-subtle">Aucune échéance imminente</p>
          ) : (
            <div className="grid grid-cols-3 gap-2 text-xs">
              {data.echeancesJ30J15J3.map((b) => (
                <div key={b.label}>
                  <div
                    className={`mb-1 rounded-md px-2 py-1 text-center font-bold ${
                      b.label === 'J-3'
                        ? 'bg-danger/20 text-danger'
                        : b.label === 'J-15'
                          ? 'bg-amber-100 text-amber-700'
                          : 'bg-bg-overlay text-fg-muted'
                    }`}
                  >
                    {b.label} ({b.items.length})
                  </div>
                  <ul className="space-y-1">
                    {b.items.slice(0, 4).map((it) => (
                      <li key={it.id} className="truncate text-fg-muted">
                        {it.libelle}
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {data.dossiersARisque.length > 0 && (
        <div className="rounded-xl border border-danger/40 bg-danger/10 p-4">
          <div className="mb-2 flex items-center gap-2">
            <AlertTriangle className="h-4 w-4 text-danger" />
            <h4 className="text-sm font-semibold text-rose-900">Dossiers à risque</h4>
          </div>
          <ul className="space-y-1 text-xs">
            {data.dossiersARisque.slice(0, 6).map((d) => (
              <li key={d.dossierId + d.motif} className="flex items-center justify-between">
                <span className="font-medium text-fg">{d.raisonSociale}</span>
                <span className="text-danger">{d.motif}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
