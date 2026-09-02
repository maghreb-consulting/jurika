import { PieChart, BarChart3 } from 'lucide-react';
import type { Ticket } from '../../types/ticket';
import { ticketsByStatut, ticketsByType } from '../../lib/employeCharts';
import { statutTicketColors } from '../../lib/chartTheme';
import { ChartCard } from './charts/ChartCard';
import { DonutChart } from './charts/DonutChart';
import { DistributionChart } from './DistributionChart';

/**
 * Graphiques EMPLOYE derives de SES tickets (deja charges/scopes par la page
 * EmployeeDashboard). Aucune requete supplementaire, aucune donnee factice :
 * les series proviennent a 100 % de `tickets`.
 */
export function EmployeeChartsPanel({ tickets }: { tickets: Ticket[] }) {
  const parStatut = ticketsByStatut(tickets);
  const parType = ticketsByType(tickets);

  return (
    <section className="mb-6">
      <h3 className="mb-4 text-lg font-semibold text-fg">Répartition de mes tickets</h3>
      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard
          title="Par statut"
          icon={<PieChart className="h-4 w-4" />}
          empty={parStatut.length === 0}
        >
          <DonutChart data={parStatut} colorByKey={statutTicketColors} />
        </ChartCard>

        <ChartCard
          title="Par type de dossier"
          icon={<BarChart3 className="h-4 w-4" />}
          empty={parType.length === 0}
        >
          <DistributionChart data={parType} height={200} />
        </ChartCard>
      </div>
    </section>
  );
}
