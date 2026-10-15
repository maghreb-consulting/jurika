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
import { decimal, longueur } from './valeurs';
import { FonctionHorsForfait } from '../billing/FonctionHorsForfait';
import { Message } from '../ui/Message';
import { Badge } from '../ui/Badge';
import { CardSkeleton, SqueletteZone } from '../ui/LoadingSkeleton';
import { chartTheme, statutTicketColors, statutDemandeColors } from '../../lib/chartTheme';
import { STATUT_TICKET_LABELS, STATUT_DEMANDE_LABELS, labelOf, formatMonth } from '../../lib/dashboardLabels';

/**
 * Indicateurs de suivi du cabinet (dashboard-service), pour le superviseur.
 *
 * Lot L6b (E9) : l'ecran plantait ("toFixed" sur undefined) quand un instantane du serveur
 * n'avait pas tous les champs (cache anterieur, reponse partielle). Chaque champ est desormais
 * lu avec garde : un nombre absent s'affiche "--", une liste absente comme vide ; aucune valeur
 * de repli n'invente un zero. Un echec du service est dit, au lieu de faire disparaitre la zone.
 */
export function Sprint10SupervisorPanel() {
  const [data, setData] = useState<Partial<SuperviseurDashboardDto> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let mounted = true;
    dashboardService
      .getSuperviseur()
      .then((d) => mounted && setData(d ?? {}))
      .catch(() => mounted && setError(true))
      .finally(() => mounted && setLoading(false));
    return () => {
      mounted = false;
    };
  }, []);

  if (loading) {
    return (
      <SqueletteZone libelle="Chargement des indicateurs de suivi…" className="mt-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <CardSkeleton key={i} className="h-[6.5rem]" />
        ))}
      </SqueletteZone>
    );
  }
  if (error || !data) {
    return (
      <Message ton="attention" titre="Indicateurs indisponibles" className="mt-6">
        Les indicateurs de suivi n’ont pas pu être chargés (tickets ouverts, échéances, dossiers à risque).
        Rechargez la page dans quelques instants ; les autres zones du tableau de bord restent à jour.
      </Message>
    );
  }

  const parStatut = (data.ticketsParStatut ?? []).map((c) => ({
    key: c.label,
    label: labelOf(STATUT_TICKET_LABELS, c.label),
    count: c.count,
  }));
  const charge = (data.chargeParEmploye ?? []).map((e) => ({
    label: e.email?.split('@')[0] ?? '--',
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
  const evolution30j = data.evolutionTickets30j ?? [];
  const echeances = data.echeancesJ30J15J3 ?? [];
  const risques = data.dossiersARisque ?? [];
  const nbRisques = longueur(data.dossiersARisque);

  // Lot L7 (E5), CDC 4.5 : indicateurs de productivite seulement dans les forfaits Max et Sur Devis.
  const productivite = data.productiviteIncluse !== false;

  return (
    <section className="mt-6 space-y-4" aria-labelledby="titre-suivi-cabinet">
      <h2 id="titre-suivi-cabinet" className="text-lg font-semibold text-heading">
        Suivi du cabinet
      </h2>
      {!productivite && <FonctionHorsForfait compact fonction="Tableaux de bord de productivité" disponibleDes="Max" />}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <KpiCard label="Tickets ouverts" value={data.ticketsOuverts} variant="warning" />
        <KpiCard label="Clos sur 30 jours" value={data.ticketsClos30d} variant="success" />
        {productivite && (
          <KpiCard
            label="Temps moyen de clôture"
            value={typeof data.tempsMoyenClotureHeures === 'number' ? `${decimal(data.tempsMoyenClotureHeures)} h` : null}
          />
        )}
        <KpiCard label="Dossiers à risque" value={nbRisques} variant={nbRisques ? 'danger' : 'default'} />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard title="Tickets par statut" icon={<PieChart />} empty={parStatut.length === 0}>
          <DonutChart data={parStatut} colorByKey={statutTicketColors} />
        </ChartCard>
        <ChartCard title="Charge par employé (tickets ouverts)" icon={<Users />} empty={charge.length === 0}>
          <DistributionChart data={charge} color={chartTheme.fgMuted} height={200} />
        </ChartCard>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        {productivite && (
          <ChartCard
            title="Évolution mensuelle (6 mois)"
            icon={<TrendingUp />}
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
        )}
        <ChartCard title="Demandes des clients par statut" icon={<Inbox />} empty={demandes.length === 0}>
          <DonutChart data={demandes} colorByKey={statutDemandeColors} />
        </ChartCard>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        {productivite && (
          <ChartCard title="Évolution des tickets (30 jours)" empty={evolution30j.length === 0}>
            <EvolutionChart data={evolution30j} />
          </ChartCard>
        )}
        <ChartCard
          title="Échéances des démarches"
          icon={<Calendar />}
          empty={echeances.every((b) => (b.items ?? []).length === 0)}
          emptyLabel="Aucune échéance dans les 30 prochains jours."
        >
          <div className="grid grid-cols-3 gap-2 text-xs">
            {echeances.map((b) => (
              <div key={b.label} className="min-w-0">
                <Badge
                  variant={b.label === 'J-3' ? 'danger' : b.label === 'J-15' ? 'warning' : 'neutral'}
                  className="mb-1.5 w-full justify-center"
                >
                  {b.label} ({(b.items ?? []).length})
                </Badge>
                <ul className="space-y-1">
                  {(b.items ?? []).slice(0, 4).map((it) => (
                    <li key={it.id} className="truncate text-fg-muted" title={it.libelle}>
                      {it.libelle}
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </ChartCard>
      </div>

      {risques.length > 0 && (
        <section className="rounded-xl border border-danger/40 border-l-4 border-l-danger bg-danger-soft p-4">
          <div className="mb-2 flex items-center gap-2">
            <AlertTriangle className="h-4 w-4 text-danger" aria-hidden="true" />
            <h3 className="text-sm font-semibold text-fg">Dossiers à risque</h3>
          </div>
          <ul className="space-y-1 text-sm">
            {risques.slice(0, 6).map((d) => (
              <li key={d.dossierId + d.motif} className="flex flex-wrap items-center justify-between gap-2">
                <span className="font-medium text-fg">{d.raisonSociale}</span>
                <span className="text-danger">{d.motif}</span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </section>
  );
}
