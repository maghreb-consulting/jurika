import { useEffect, useState } from 'react';
import { AlarmClock, BarChart3, CalendarClock, Inbox, PieChart, Ticket as TicketIcon, Users } from 'lucide-react';
import { supervisionService, type TicketsPerEmploye, type WorkspaceKpis } from '../../services/supervision.service';
import { dashboardService } from '../../services/dashboard.service';
import { dataroomService } from '../../services/dataroom.service';
import { ticketService } from '../../services/ticket.service';
import { Page } from '../../components/layout/Page';
import { TexteAide } from '../../components/ui/Aide';
import { Lien } from '../../components/ui/Lien';
import { Message } from '../../components/ui/Message';
import { CardSkeleton, SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { FonctionHorsForfait } from '../../components/billing/FonctionHorsForfait';
import { SupervisionDemandesPanel } from '../../components/dashboard/SupervisionDemandesPanel';
import { DelaisCabinetCarte } from '../../components/dashboard/DelaisCabinetCarte';
import { SectionTableauDeBord } from '../../components/dashboard/SectionTableauDeBord';
import { ChartCard } from '../../components/dashboard/charts/ChartCard';
import { ColonnesChart } from '../../components/dashboard/charts/ColonnesChart';
import { DonutChart } from '../../components/dashboard/charts/DonutChart';
import { ChargeEquipe } from '../../components/dashboard/ChargeEquipe';
import { estOuvert, ticketsParType } from '../../components/dashboard/series';
import { KpiCard } from '../../components/dashboard/KpiCard';
import { joursRestants } from '../../components/dashboard/delais';
import { dureeCloture } from '../../components/dashboard/valeurs';
import { formatMonth } from '../../lib/dashboardLabels';
import type { Ticket } from '../../types/ticket';
import type { SuperviseurDashboardDto } from '../../types/dashboard';
import type { DossierBrief } from '../../types/dataroom';

const HORIZON_JOURS = 7;

function pluriel(n: number, singulier: string, plurielTexte: string): string {
  return `${n} ${n > 1 ? plurielTexte : singulier}`;
}

/**
 * Tableau de bord du superviseur (lot L6b, E17 : refonte du 2026-10-16).
 *
 * Le superviseur supervise et ne fait pas avancer les tickets (CDC : lecture seule sur les
 * parcours) ; ses actions sont la reaffectation, l'equipe, le suivi des delais et des dossiers
 * en retard. Hierarchie :
 * 1. Quatre indicateurs : tickets ouverts, dossiers en retard, delais sous 7 jours, demandes des
 *    clients non traitees.
 * 2. "Delais a surveiller" : dossiers en retard puis delais legaux des 30 prochains jours.
 * 3. L'equipe : charge de chaque employe empilee par etape (noms, jamais un identifiant), et
 *    les tickets ouverts par type d'operation (anneau avec total).
 * 4. Demandes et requetes (lecture seule).
 * 5. Activite : crees et clotures par mois (productivite, forfaits Max et Sur Devis), avec la
 *    duree moyenne de cloture.
 * Lot L6b (E18, demande du 2026-10-17 : des diagrammes propres a chaque role) : frise des
 * delais du cabinet, charge empilee par etape, anneau des types d'operation ; les tickets
 * ouverts viennent de la liste des tickets (500 au plus, le total du serveur dit si elle est
 * complete).
 * Retires (doublons ou sans question claire) : total des dossiers, nombre d'employes, clotures
 * depuis toujours, courbes "Activite 14 jours" et "Evolution 30 jours" (lissees, en dents de
 * scie sur des comptages journaliers), "Tickets par type" (tous les tickets depuis toujours),
 * "Score" (part des clotures, sans lien avec la charge), anneaux des statuts et des demandes
 * (sans chiffres ; les demandes ont leur tableau), graphique de charge (identifiants a la place
 * des noms, doublon du tableau de l'equipe), trois tranches de delais qui se recouvraient.
 *
 * Une source en echec rend null : ses indicateurs affichent "--" et sa zone le dit (motif 9).
 */
export function SupervisorDashboard() {
  const [kpis, setKpis] = useState<WorkspaceKpis | null>(null);
  const [equipe, setEquipe] = useState<TicketsPerEmploye[] | null>(null);
  const [suivi, setSuivi] = useState<Partial<SuperviseurDashboardDto> | null>(null);
  const [dossiers, setDossiers] = useState<DossierBrief[] | null>(null);
  const [tickets, setTickets] = useState<Ticket[] | null>(null);
  const [totalTickets, setTotalTickets] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    Promise.all([
      supervisionService.kpis().catch(() => null),
      supervisionService.ticketsPerEmploye().catch(() => null),
      dashboardService.getSuperviseur().catch(() => null),
      dataroomService.listDossiers().catch(() => null),
      ticketService.list({ limit: 500 }).catch(() => null),
    ]).then(([k, e, s, d, t]) => {
      if (!mounted) return;
      setKpis(k ?? null);
      setEquipe(Array.isArray(e) ? e : null);
      setSuivi(s ?? null);
      setDossiers(Array.isArray(d) ? d : null);
      setTickets(t ? (t.items ?? []) : null);
      setTotalTickets(t && typeof t.total === 'number' ? t.total : null);
      setLoading(false);
    });
    return () => {
      mounted = false;
    };
  }, []);

  const nombre = (v: unknown): number | null => (typeof v === 'number' && Number.isFinite(v) ? v : null);
  const nouveaux = nombre(kpis?.ticketsNouveaux);
  const enProduction = nombre(kpis?.ticketsEnCours);
  const ouverts = nouveaux !== null && enProduction !== null ? nouveaux + enProduction : null;

  const risques = suivi ? (suivi.dossiersARisque ?? []) : null;
  const tranche30 = suivi ? (suivi.echeancesJ30J15J3 ?? []).find((b) => b.label === 'J-30') : undefined;
  const aVenir = suivi ? (tranche30?.items ?? []) : null;
  const aujourdHui = new Date();
  const sousHorizon = aVenir
    ? aVenir.filter((e) => e.dateEcheance && joursRestants(e.dateEcheance, aujourdHui) <= HORIZON_JOURS).length
    : null;

  const noms = new Map((dossiers ?? []).map((d) => [d.id, d.raisonSociale]));

  const productivite = suivi ? suivi.productiviteIncluse !== false : true;
  const ticketsOuverts = (tickets ?? []).filter(estOuvert);
  const parType = ticketsParType(ticketsOuverts);
  const listePartielle = totalTickets !== null && tickets !== null && totalTickets > tickets.length;
  const mensuel = (suivi?.evolutionMensuelle ?? []).map((m) => ({ mois: formatMonth(m.month), crees: m.crees, clotures: m.clotures }));
  const clos30 = nombre(suivi?.ticketsClos30d);
  const moyenne = nombre(suivi?.tempsMoyenClotureHeures);

  return (
    <Page
      titre="Tableau de bord"
      description="Le cabinet en un coup d’œil : les délais à surveiller, la charge de l’équipe et l’activité des tickets."
      aide={
        <TexteAide cle="tableau-de-bord-superviseur" titre="Que suivre ici ?">
          <p>
            Commencez par « Délais à surveiller » : les dossiers en retard, puis les délais légaux des démarches des
            30 prochains jours. Vous consultez sans faire avancer les tickets : c’est l’employé responsable du dossier
            qui agit. Si un employé est surchargé ou absent, ouvrez la Data Room du dossier et utilisez « Réaffecter le
            dossier » ; la charge de chacun, étape par étape, figure dans « Équipe ». La frise colore les jours où tombent
            des délais : choisissez un jour pour lire lesquels. Un indicateur « -- » signale une donnée qui n’a pas pu
            être chargée.
          </p>
        </TexteAide>
      }
    >
      <div className="mb-8 grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <KpiCard
          icon={<TicketIcon />}
          label="Tickets ouverts"
          value={ouverts}
          hint={nouveaux !== null ? (nouveaux > 0 ? `dont ${nouveaux} à la création` : 'aucun à la création') : undefined}
          loading={loading}
          to="/tickets"
          aide="Tickets du cabinet à la création, à la génération des documents ou au déroulement des démarches. Les tickets clôturés ou annulés ne comptent pas."
        />
        <KpiCard
          icon={<AlarmClock />}
          label="Dossiers en retard"
          value={risques ? risques.length : null}
          variant={risques && risques.length > 0 ? 'danger' : 'default'}
          hint={risques ? (risques.length > 0 ? 'délai légal dépassé' : 'aucun délai dépassé') : undefined}
          loading={loading}
          aide="Dossiers dont au moins une démarche restant à accomplir a dépassé son délai légal."
        />
        <KpiCard
          icon={<CalendarClock />}
          label={`Délais sous ${HORIZON_JOURS} jours`}
          value={sousHorizon}
          variant={sousHorizon ? 'warning' : 'default'}
          hint={sousHorizon !== null ? 'démarches dont le délai légal tombe cette semaine' : undefined}
          loading={loading}
          aide={`Délais légaux des démarches qui tombent aujourd’hui ou dans les ${HORIZON_JOURS} prochains jours, tous dossiers du cabinet.`}
        />
        <KpiCard
          icon={<Inbox />}
          label="Demandes non traitées"
          value={kpis?.demandesNonTraitees}
          variant={kpis?.demandesNonTraitees ? 'warning' : 'default'}
          hint={typeof kpis?.demandesNonTraitees === 'number' ? 'demandes de clients sans prise en charge' : undefined}
          loading={loading}
          to="/data-rooms"
          aide="Demandes envoyées par les clients que l’employé responsable du dossier n’a pas encore prises en charge."
        />
      </div>

      <DelaisCabinetCarte risques={risques} aVenir={aVenir} nomDossier={(id) => noms.get(id)} loading={loading} />

      <SectionTableauDeBord titre="Équipe" icone={<Users />} lien={<Lien to="/app/team">Gérer l’équipe</Lien>}>
        {loading ? (
          <SqueletteZone libelle="Chargement de l’équipe…" className="grid gap-4 lg:grid-cols-5">
            <CardSkeleton className="h-72 lg:col-span-3" />
            <CardSkeleton className="h-72 lg:col-span-2" />
          </SqueletteZone>
        ) : (
          <div className="grid gap-4 lg:grid-cols-5">
            <ChartCard
              className="lg:col-span-3"
              title="Charge de l’équipe, par étape"
              icon={<Users />}
              description={
                listePartielle
                  ? `Tickets ouverts confiés à chaque employé, par étape du parcours (calculé sur les ${tickets!.length} tickets les plus récents, ${totalTickets} au total).`
                  : 'Tickets ouverts confiés à chaque employé, par étape du parcours : de quoi décider d’une réaffectation.'
              }
              empty={tickets === null || (ticketsOuverts.length === 0 && (equipe ?? []).length === 0)}
              emptyLabel={tickets === null ? 'La charge de l’équipe n’a pas pu être chargée. Rechargez la page dans quelques instants.' : 'Aucun employé ni ticket ouvert. Invitez vos employés depuis la page « Équipe ».'}
            >
              <ChargeEquipe tickets={tickets ?? []} equipe={equipe} />
            </ChartCard>
            <ChartCard
              className="lg:col-span-2"
              title="Tickets ouverts par type d’opération"
              icon={<PieChart />}
              description="Sur quelles opérations le cabinet travaille en ce moment."
              empty={parType.length === 0}
              emptyLabel={tickets === null ? 'Les tickets n’ont pas pu être chargés.' : 'Aucun ticket ouvert.'}
            >
              <DonutChart data={parType} height={260} legende="Tickets ouverts par type d’opération" />
            </ChartCard>
          </div>
        )}
      </SectionTableauDeBord>

      {/* Demandes des clients + requetes aux clients (lecture seule, noms resolus par le serveur) */}
      <SupervisionDemandesPanel />

      <SectionTableauDeBord titre="Activité des tickets" icone={<BarChart3 />} className="mt-8">
        {loading ? (
          <SqueletteZone libelle="Chargement de l’activité…" className="grid gap-4 lg:grid-cols-2">
            <CardSkeleton className="h-64 lg:col-span-2" />
          </SqueletteZone>
        ) : suivi === null ? (
          <Message ton="attention" titre="Activité indisponible">
            Les chiffres d’activité n’ont pas pu être chargés. Rechargez la page dans quelques instants.
          </Message>
        ) : (
          <div>
            {productivite ? (
              <ChartCard
                title="Tickets créés et clôturés par mois"
                icon={<BarChart3 />}
                description="Six derniers mois : le cabinet ouvre-t-il plus de dossiers qu’il n’en termine ?"
                empty={mensuel.length === 0 || mensuel.every((m) => m.crees === 0 && m.clotures === 0)}
                emptyLabel="Aucun ticket créé ni clôturé sur les six derniers mois."
              >
                <ColonnesChart
                  donnees={mensuel}
                  cleX="mois"
                  legende="Tickets créés et clôturés par mois, six derniers mois"
                  series={[
                    { cle: 'crees', nom: 'Créés', ton: 'info' },
                    { cle: 'clotures', nom: 'Clôturés', ton: 'succes' },
                  ]}
                />
                <p className="mt-3 border-t border-border pt-3 text-sm text-fg-muted">
                  Durée moyenne de clôture sur 30 jours :{' '}
                  <span className="font-semibold text-heading">
                    {clos30 === null || moyenne === null
                      ? '--'
                      : clos30 === 0
                        ? 'aucun ticket clôturé'
                        : `${dureeCloture(moyenne)} (${pluriel(clos30, 'ticket clôturé', 'tickets clôturés')})`}
                  </span>
                </p>
              </ChartCard>
            ) : (
              <FonctionHorsForfait fonction="Tableaux de bord de productivité" disponibleDes="Max" />
            )}
          </div>
        )}
      </SectionTableauDeBord>
    </Page>
  );
}
