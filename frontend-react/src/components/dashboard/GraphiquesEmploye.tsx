import { BarChart3, CalendarRange, PieChart } from 'lucide-react';
import type { Ticket } from '../../types/ticket';
import type { EcheanceItem } from '../../types/dashboard';
import { ChartCard } from './charts/ChartCard';
import { ColonnesChart } from './charts/ColonnesChart';
import { DonutChart } from './charts/DonutChart';
import { FriseDelais, type PointDelai } from './charts/FriseDelais';
import { comptesParMois, estOuvert, ticketsParType } from './series';
import { joursRestants } from './delais';

const MOIS = 6;

/**
 * Lot L6b (E18) : diagrammes du tableau de bord de l'EMPLOYE, sur SES donnees deja chargees.
 *
 * - "Mes delais des 30 prochains jours" (frise calendrier) : quand tombent les dates limites de
 *   mes tickets ouverts et les delais legaux des demarches de mes dossiers ? Les tickets en
 *   retard ont leur case a part.
 * - "Mes tickets ouverts par type d'operation" (anneau avec total) : sur quoi je travaille ?
 */
export function ReperesEmploye({
  tickets,
  delaisDemarches,
  nomDossier,
  enRetard,
  aujourdHui = new Date(),
}: {
  tickets: Ticket[] | null;
  /** null : les delais des demarches n'ont pas pu etre charges. */
  delaisDemarches: EcheanceItem[] | null;
  nomDossier: (id: string) => string | undefined;
  enRetard: number | null;
  aujourdHui?: Date;
}) {
  const ouverts = (tickets ?? []).filter(estOuvert);
  const points: PointDelai[] = [];
  for (const t of ouverts) {
    if (t.deadline && joursRestants(t.deadline, aujourdHui) >= 0) {
      points.push({ date: t.deadline.slice(0, 10), libelle: `date limite du ticket « ${t.titre} »`, detail: t.reference });
    }
  }
  for (const e of delaisDemarches ?? []) {
    if (e.dateEcheance && joursRestants(e.dateEcheance, aujourdHui) >= 0) {
      points.push({ date: e.dateEcheance, libelle: e.libelle, detail: nomDossier(e.dossierId) });
    }
  }
  const parType = ticketsParType(ouverts);

  return (
    <div className="mb-8 grid gap-4 lg:grid-cols-5">
      <ChartCard
        className="lg:col-span-3"
        title="Mes délais des 30 prochains jours"
        icon={<CalendarRange />}
        description={
          delaisDemarches === null
            ? 'Dates limites de vos tickets ouverts (les délais légaux des démarches n’ont pas pu être chargés).'
            : 'Dates limites de vos tickets ouverts et délais légaux des démarches de vos dossiers, jour par jour.'
        }
        empty={tickets === null}
        emptyLabel="Vos tickets n’ont pas pu être chargés."
      >
        <FriseDelais points={points} enRetard={enRetard} libelleRetard="tickets en retard" legende="Mes délais des 30 prochains jours" aujourdHui={aujourdHui} />
      </ChartCard>
      <ChartCard
        className="lg:col-span-2"
        title="Mes tickets ouverts par type d’opération"
        icon={<PieChart />}
        description="Sur quoi porte votre travail en cours."
        empty={parType.length === 0}
        emptyLabel={tickets === null ? 'Vos tickets n’ont pas pu être chargés.' : 'Aucun ticket ouvert.'}
      >
        <DonutChart data={parType} height={240} legende="Mes tickets ouverts par type d’opération" />
      </ChartCard>
    </div>
  );
}

/**
 * - "Mon rythme" (colonnes groupees) : combien de tickets ai-je recus et clotures chaque mois,
 *   sur six mois ? Calcule sur la liste chargee ; si le serveur en a davantage, c'est dit.
 */
export function RythmeEmploye({ tickets, totalServeur, aujourdHui = new Date() }: { tickets: Ticket[] | null; totalServeur: number | null; aujourdHui?: Date }) {
  const liste = tickets ?? [];
  const donnees = comptesParMois({ recus: liste.map((t) => t.createdAt), clotures: liste.map((t) => (t.statut === 'CLOTURE_DOSSIER' ? t.clotureAt : null)) }, MOIS, aujourdHui);
  const partiel = totalServeur !== null && totalServeur > liste.length;
  const vide = donnees.every((d) => d.recus === 0 && d.clotures === 0);
  return (
    <ChartCard
      className="mt-2"
      title="Mon rythme : tickets reçus et clôturés par mois"
      icon={<BarChart3 />}
      description={
        partiel
          ? `Six derniers mois, calculé sur vos ${liste.length} tickets les plus récents (${totalServeur} au total).`
          : 'Six derniers mois : tickets qui vous ont été confiés ou que vous avez créés, et tickets clôturés.'
      }
      empty={tickets === null || vide}
      emptyLabel={tickets === null ? 'Vos tickets n’ont pas pu être chargés.' : 'Aucun ticket reçu ni clôturé sur les six derniers mois.'}
    >
      <ColonnesChart
        donnees={donnees}
        cleX="mois"
        legende="Mes tickets reçus et clôturés par mois"
        series={[
          { cle: 'recus', nom: 'Reçus', ton: 'info' },
          { cle: 'clotures', nom: 'Clôturés', ton: 'succes' },
        ]}
      />
    </ChartCard>
  );
}
