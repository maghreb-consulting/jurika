import { useEffect, useState } from 'react';
import { AlarmClock, BarChart3, CalendarClock, Inbox, Layers, Ticket as TicketIcon, Users } from 'lucide-react';
import { supervisionService, type TicketsPerEmploye, type WorkspaceKpis } from '../../services/supervision.service';
import { dashboardService } from '../../services/dashboard.service';
import { dataroomService } from '../../services/dataroom.service';
import { Page } from '../../components/layout/Page';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { Lien } from '../../components/ui/Lien';
import { EmptyState } from '../../components/ui/EmptyState';
import { Message } from '../../components/ui/Message';
import { CardSkeleton, SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { Tableau, type ColonneTableau } from '../../components/ui/Tableau';
import { FonctionHorsForfait } from '../../components/billing/FonctionHorsForfait';
import { SupervisionDemandesPanel } from '../../components/dashboard/SupervisionDemandesPanel';
import { DelaisCabinetCarte } from '../../components/dashboard/DelaisCabinetCarte';
import { SectionTableauDeBord } from '../../components/dashboard/SectionTableauDeBord';
import { ChartCard } from '../../components/dashboard/charts/ChartCard';
import { ColonnesChart } from '../../components/dashboard/charts/ColonnesChart';
import { BarresHorizontales } from '../../components/dashboard/charts/BarresHorizontales';
import type { TonSerie } from '../../components/dashboard/charts/apparence';
import { KpiCard } from '../../components/dashboard/KpiCard';
import { joursRestants } from '../../components/dashboard/delais';
import { dureeCloture } from '../../components/dashboard/valeurs';
import { STATUT_TICKET_LABELS, formatMonth, labelOf } from '../../lib/dashboardLabels';
import type { SuperviseurDashboardDto } from '../../types/dashboard';
import type { DossierBrief } from '../../types/dataroom';

/** Etapes d'un ticket ouvert, dans l'ordre du parcours, et leur ton dans les graphiques. */
const ETAPES_OUVERTES: Array<{ statut: string; ton: TonSerie }> = [
  { statut: 'CREATION_TICKET', ton: 'info' },
  { statut: 'GENERATION_DOCUMENTS', ton: 'info' },
  { statut: 'DEROULEMENT_DEMARCHE', ton: 'info' },
];

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
 * 3. L'equipe : charge de chaque employe (noms, jamais un identifiant).
 * 4. Demandes et requetes (lecture seule).
 * 5. Activite : tickets ouverts par etape ; crees et clotures par mois (productivite, forfaits
 *    Max et Sur Devis), avec la duree moyenne de cloture.
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
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    Promise.all([
      supervisionService.kpis().catch(() => null),
      supervisionService.ticketsPerEmploye().catch(() => null),
      dashboardService.getSuperviseur().catch(() => null),
      dataroomService.listDossiers().catch(() => null),
    ]).then(([k, e, s, d]) => {
      if (!mounted) return;
      setKpis(k ?? null);
      setEquipe(Array.isArray(e) ? e : null);
      setSuivi(s ?? null);
      setDossiers(Array.isArray(d) ? d : null);
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
  const parEtape = ETAPES_OUVERTES.map((e) => ({
    cle: e.statut,
    libelle: labelOf(STATUT_TICKET_LABELS, e.statut),
    valeur: (suivi?.ticketsParStatut ?? []).find((c) => c.label === e.statut)?.count ?? 0,
    ton: e.ton,
  }));
  const parEtapeConnu = !!suivi?.ticketsParStatut;
  const mensuel = (suivi?.evolutionMensuelle ?? []).map((m) => ({ mois: formatMonth(m.month), crees: m.crees, clotures: m.clotures }));
  const clos30 = nombre(suivi?.ticketsClos30d);
  const moyenne = nombre(suivi?.tempsMoyenClotureHeures);

  const maxEnCours = Math.max(1, ...(equipe ?? []).map((e) => e.enCours));
  const equipeTriee = [...(equipe ?? [])].sort((a, b) => b.enCours - a.enCours || `${a.lastName}`.localeCompare(`${b.lastName}`));
  const colonnes: ColonneTableau<TicketsPerEmploye>[] = [
    {
      cle: 'employe',
      titre: 'Employé',
      rendu: (e) => (
        <span className="flex items-center gap-3">
          <span
            aria-hidden="true"
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full border border-border bg-bg-overlay text-xs font-semibold text-fg-muted"
          >
            {(e.firstName?.[0] ?? '') + (e.lastName?.[0] ?? '')}
          </span>
          <span className="font-medium text-fg">
            {`${e.firstName ?? ''} ${e.lastName ?? ''}`.trim() || 'Nom non renseigné'}
          </span>
        </span>
      ),
    },
    {
      cle: 'enCours',
      titre: (
        <span className="inline-flex items-center gap-1">
          En production
          <InfoBulle
            libelle="Que compte la colonne « En production » ?"
            texte="Tickets confiés à l’employé qui sont à la génération des documents ou au déroulement des démarches. Les tickets encore à la création ne sont pas comptés ici."
          />
        </span>
      ),
      rendu: (e) => (
        <span className="flex items-center gap-3">
          <span className="w-6 text-right font-semibold tabular-nums text-heading">{e.enCours}</span>
          <span className="hidden h-2 w-32 overflow-hidden rounded-full bg-bg-overlay sm:block" aria-hidden="true">
            <span className="block h-full rounded-full bg-accent" style={{ width: `${(e.enCours / maxEnCours) * 100}%` }} />
          </span>
        </span>
      ),
    },
    { cle: 'clotures', titre: 'Clôturés', alignement: 'centre', rendu: (e) => <span className="tabular-nums">{e.clotures}</span> },
    { cle: 'annules', titre: 'Annulés', alignement: 'centre', masquerSous: 'sm', rendu: (e) => <span className="tabular-nums">{e.annules}</span> },
  ];

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
            dossier » ; la charge de chacun figure dans le tableau « Équipe ». Un indicateur « -- » signale une donnée
            qui n’a pas pu être chargée.
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

      <SectionTableauDeBord
        titre="Équipe"
        icone={<Users />}
        lien={<Lien to="/app/team">Gérer l’équipe</Lien>}
      >
        <Tableau
          legende="Charge de chaque employé"
          colonnes={colonnes}
          lignes={equipeTriee}
          cleLigne={(e) => e.userId}
          chargement={loading}
          lignesChargement={3}
          enTeteCollant={false}
          vide={
            equipe === null ? (
              <EmptyState compact title="Données indisponibles" description="La charge des employés n’a pas pu être chargée. Rechargez la page dans quelques instants." />
            ) : (
              <EmptyState
                compact
                icon={<Users />}
                title="Aucun employé actif"
                description="Invitez vos employés depuis la page « Équipe » ; leur charge apparaîtra ici dès leur premier ticket."
                cta={<Lien to="/app/team" commeBouton={{ variant: 'secondary', size: 'sm' }}>Ouvrir l’équipe</Lien>}
              />
            )
          }
        />
      </SectionTableauDeBord>

      {/* Demandes des clients + requetes aux clients (lecture seule, noms resolus par le serveur) */}
      <SupervisionDemandesPanel />

      <SectionTableauDeBord titre="Activité des tickets" icone={<BarChart3 />} className="mt-8">
        {loading ? (
          <SqueletteZone libelle="Chargement de l’activité…" className="grid gap-4 lg:grid-cols-2">
            <CardSkeleton className="h-64" />
            <CardSkeleton className="h-64" />
          </SqueletteZone>
        ) : suivi === null ? (
          <Message ton="attention" titre="Activité indisponible">
            Les chiffres d’activité n’ont pas pu être chargés. Rechargez la page dans quelques instants.
          </Message>
        ) : (
          <div className="grid gap-4 lg:grid-cols-2">
            <ChartCard
              title="Où en sont les tickets ouverts ?"
              icon={<Layers />}
              empty={!parEtapeConnu || parEtape.every((e) => e.valeur === 0)}
              emptyLabel={parEtapeConnu ? 'Aucun ticket ouvert.' : 'Répartition non rendue par le serveur.'}
            >
              <BarresHorizontales barres={parEtape} legende="Tickets ouverts par étape du parcours" part total="Tickets ouverts" />
            </ChartCard>
            {productivite ? (
              <ChartCard
                title="Tickets créés et clôturés par mois"
                icon={<BarChart3 />}
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
