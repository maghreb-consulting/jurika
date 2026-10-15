import { Briefcase, CalendarClock, Calendar } from 'lucide-react';
import type { EmployeDashboardDto } from '../../types/dashboard';
import { echeancesByWeek } from '../../lib/employeCharts';
import { labelOf, STATUT_TICKET_LABELS } from '../../lib/dashboardLabels';
import { KpiCard } from './KpiCard';
import { longueur } from './valeurs';
import { ChartCard } from './charts/ChartCard';
import { DistributionChart } from './DistributionChart';
import { Badge } from '../ui/Badge';
import { Message } from '../ui/Message';
import { CardSkeleton, SqueletteZone } from '../ui/LoadingSkeleton';
import { chartTheme } from '../../lib/chartTheme';

/**
 * Indicateurs de l'employe (dashboard-service) : echeances, tickets ouverts, activite Data Room.
 *
 * Lot L6b (E9) : la page charge `/dashboards/employe` UNE fois (la carte "Prochaine action" en
 * lit aussi les echeances) et passe le resultat ici. `data` null apres chargement : le service
 * n'a pas repondu, c'est dit (la zone ne disparait plus en silence). Champs absents : "--".
 */
export function Sprint10EmployePanel({ data, loading }: { data: Partial<EmployeDashboardDto> | null; loading: boolean }) {
  if (loading) {
    return (
      <SqueletteZone libelle="Chargement de vos indicateurs…" className="mt-6 grid grid-cols-1 gap-4 md:grid-cols-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <CardSkeleton key={i} className="h-[6.5rem]" />
        ))}
      </SqueletteZone>
    );
  }
  if (!data) {
    return (
      <Message ton="attention" titre="Indicateurs indisponibles" className="mt-6">
        Vos indicateurs (échéances, tickets ouverts, activité Data Room) n’ont pas pu être chargés. Rechargez la page
        dans quelques instants.
      </Message>
    );
  }

  const echeances = data.mesEcheancesAssignees ?? [];
  const derniers = data.derniersTickets ?? [];
  const echeancesSemaine = echeancesByWeek(echeances);
  const nbEcheances = longueur(data.mesEcheancesAssignees);

  return (
    <section className="mt-6 space-y-4" aria-labelledby="titre-mes-indicateurs">
      <h2 id="titre-mes-indicateurs" className="text-lg font-semibold text-heading">
        Mes indicateurs
      </h2>
      <ChartCard
        title="Échéances des 30 prochains jours"
        icon={<CalendarClock />}
        empty={echeancesSemaine.length === 0}
        emptyLabel="Aucune échéance dans les 30 prochains jours."
      >
        <DistributionChart data={echeancesSemaine.map((b) => ({ label: b.label, count: b.count }))} color={chartTheme.accent} height={180} />
      </ChartCard>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <KpiCard label="Mes tickets ouverts" value={data.mesTicketsOuverts} icon={<Briefcase />} />
        <KpiCard label="Échéances à venir" value={nbEcheances} icon={<Calendar />} variant={nbEcheances ? 'warning' : 'default'} />
        <KpiCard label="Activité Data Room récente" value={longueur(data.dernieresOperationsDataroom)} />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard title="Mes derniers tickets" empty={derniers.length === 0} emptyLabel="Aucun ticket récent.">
          <ul className="divide-y divide-border">
            {derniers.slice(0, 5).map((t) => (
              <li key={t.id} className="flex items-center justify-between gap-3 py-2 text-sm">
                <span className="min-w-0 truncate text-fg">
                  <span className="font-mono text-xs text-fg-subtle">{t.reference}</span> {t.titre}
                </span>
                <Badge variant="neutral" className="shrink-0">
                  {labelOf(STATUT_TICKET_LABELS, t.statut)}
                </Badge>
              </li>
            ))}
          </ul>
        </ChartCard>

        <ChartCard title="Échéances assignées" empty={echeances.length === 0} emptyLabel="Aucune échéance prochaine.">
          <ul className="divide-y divide-border">
            {echeances.slice(0, 5).map((e) => (
              <li key={e.id} className="flex items-center justify-between gap-3 py-2 text-sm">
                <span className="min-w-0 truncate text-fg">{e.libelle}</span>
                <span className="shrink-0 text-xs tabular-nums text-fg-muted">
                  {e.dateEcheance ? new Date(e.dateEcheance).toLocaleDateString('fr-FR') : '--'}
                </span>
              </li>
            ))}
          </ul>
        </ChartCard>
      </div>
    </section>
  );
}
