import { useEffect, useState } from 'react';
import { AlertTriangle, CheckCircle, FolderOpen, Ticket as TicketIcon, TrendingUp, Users } from 'lucide-react';
import {
  supervisionService,
  type TicketsDaily,
  type TicketsPerEmploye,
  type TicketsPerType,
  type WorkspaceKpis,
} from '../../services/supervision.service';
import { Page } from '../../components/layout/Page';
import { Sprint10SupervisorPanel } from '../../components/dashboard/Sprint10SupervisorPanel';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { Badge } from '../../components/ui/Badge';
import { Lien } from '../../components/ui/Lien';
import { EmptyState } from '../../components/ui/EmptyState';
import { Tableau, type ColonneTableau } from '../../components/ui/Tableau';
import { SupervisionDemandesPanel } from '../../components/dashboard/SupervisionDemandesPanel';
import { EvolutionChart } from '../../components/dashboard/EvolutionChart';
import { DistributionChart } from '../../components/dashboard/DistributionChart';
import { ChartCard } from '../../components/dashboard/charts/ChartCard';
import { KpiCard } from '../../components/dashboard/KpiCard';
import { TYPE_TICKET_LABELS, labelOf } from '../../lib/dashboardLabels';

/** Pluriel simple d'un compteur, ou rien si le compteur est absent. */
function compte(n: number | null | undefined, singulier: string, pluriel: string): string | undefined {
  if (typeof n !== 'number') return undefined;
  return `${n} ${n > 1 ? pluriel : singulier}`;
}

/**
 * Tableau de bord du superviseur : vue d'ensemble du cabinet (lot L6b, E9).
 *
 * Une source en echec rend `null` (et non une liste vide ou un zero) : l'indicateur affiche
 * alors "--", jamais un chiffre invente (motif 9).
 */
export function SupervisorDashboard() {
  const [kpis, setKpis] = useState<WorkspaceKpis | null>(null);
  const [perType, setPerType] = useState<TicketsPerType[] | null>(null);
  const [perEmploye, setPerEmploye] = useState<TicketsPerEmploye[] | null>(null);
  const [daily, setDaily] = useState<TicketsDaily[] | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    Promise.all([
      supervisionService.kpis().catch(() => null),
      supervisionService.ticketsPerType().catch(() => null),
      supervisionService.ticketsPerEmploye().catch(() => null),
      supervisionService.ticketsLast30Days().catch(() => null),
    ]).then(([k, t, e, d]) => {
      if (!mounted) return;
      setKpis(k ?? null);
      setPerType(Array.isArray(t) ? t : null);
      setPerEmploye(Array.isArray(e) ? e : null);
      setDaily(Array.isArray(d) ? d.slice(-14) : null);
      setLoading(false);
    });
    return () => {
      mounted = false;
    };
  }, []);

  const dailySeries = (daily ?? []).map((d) => ({ day: d.date, count: d.count }));
  const typeSeries = (perType ?? []).map((t) => ({ label: labelOf(TYPE_TICKET_LABELS, t.type), count: t.count }));

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
            {e.firstName} {e.lastName}
          </span>
        </span>
      ),
    },
    { cle: 'enCours', titre: 'En cours', alignement: 'centre', rendu: (e) => <span className="tabular-nums">{e.enCours}</span> },
    { cle: 'clotures', titre: 'Clôturés', alignement: 'centre', rendu: (e) => <span className="tabular-nums">{e.clotures}</span> },
    { cle: 'annules', titre: 'Annulés', alignement: 'centre', masquerSous: 'sm', rendu: (e) => <span className="tabular-nums">{e.annules}</span> },
    {
      cle: 'score',
      titre: (
        <span className="inline-flex items-center gap-1">
          Score
          <InfoBulle
            libelle="Comment le score est-il calculé ?"
            texte="Part des tickets clôturés parmi les tickets de l’employé (en cours, clôturés et annulés), en pourcentage."
          />
        </span>
      ),
      alignement: 'centre',
      rendu: (e) => {
        const total = e.clotures + e.enCours + e.annules;
        if (total === 0) return <span className="text-fg-subtle" title="Aucun ticket">--</span>;
        const score = Math.round((e.clotures / total) * 100);
        return (
          <Badge variant={score >= 80 ? 'success' : score >= 50 ? 'warning' : 'danger'}>
            {score >= 80 && <CheckCircle className="h-3 w-3" aria-hidden="true" />}
            {score < 50 && <AlertTriangle className="h-3 w-3" aria-hidden="true" />}
            {score} %
          </Badge>
        );
      },
    },
  ];

  return (
    <Page
      titre="Tableau de bord"
      description="Vue d’ensemble du cabinet : dossiers, tickets, charge des employés et échéances."
      aide={
        <TexteAide cle="tableau-de-bord-superviseur" titre="Que suivre ici ?">
          <p>
            Les chiffres du cabinet (dossiers, employés, tickets en cours et clôturés), l’activité des
            quatorze derniers jours et la charge de chaque employé. Vous consultez : seul l’employé
            responsable d’un dossier fait avancer ses tickets. Pour confier un dossier à un autre
            employé, ouvrez sa Data Room et utilisez « Réaffecter le dossier ». Un indicateur « -- »
            signale une donnée qui n’a pas pu être chargée.
          </p>
        </TexteAide>
      }
    >
      <div className="mb-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <KpiCard
          icon={<FolderOpen />}
          label="Total dossiers"
          value={kpis?.dossiers}
          hint={compte(kpis?.dossiersActifs, 'actif', 'actifs')}
          loading={loading}
        />
        <KpiCard icon={<Users />} label="Employés" value={perEmploye?.length} loading={loading} />
        <KpiCard
          icon={<TicketIcon />}
          label="Tickets en cours"
          value={kpis?.ticketsEnCours}
          variant="warning"
          hint={compte(kpis?.ticketsNouveaux, 'nouveau', 'nouveaux')}
          loading={loading}
        />
        <KpiCard
          icon={<CheckCircle />}
          label="Clôturés"
          value={kpis?.ticketsClotures}
          variant="success"
          hint={compte(kpis?.ticketsAnnules, 'annulé', 'annulés')}
          loading={loading}
        />
      </div>

      <div className="mb-6 grid gap-4 lg:grid-cols-2">
        <ChartCard
          title="Activité (14 derniers jours)"
          icon={<TrendingUp />}
          empty={dailySeries.length === 0}
          emptyLabel={daily === null && !loading ? 'Activité indisponible : la donnée n’a pas pu être chargée.' : undefined}
        >
          <EvolutionChart data={dailySeries} height={200} />
        </ChartCard>
        <ChartCard
          title="Tickets par type"
          empty={typeSeries.length === 0}
          emptyLabel={perType === null && !loading ? 'Répartition indisponible : la donnée n’a pas pu être chargée.' : undefined}
        >
          <DistributionChart data={typeSeries} height={200} />
        </ChartCard>
      </div>

      <section aria-labelledby="titre-performances" className="mb-6">
        <h2 id="titre-performances" className="mb-3 text-lg font-semibold text-heading">
          Performances par employé
        </h2>
        <Tableau
          legende="Performances par employé"
          colonnes={colonnes}
          lignes={perEmploye ?? []}
          cleLigne={(e) => e.userId}
          chargement={loading}
          lignesChargement={3}
          enTeteCollant={false}
          vide={
            perEmploye === null ? (
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
      </section>

      {/* Demandes des clients + Requetes aux clients (workspace-wide, avec nom du
          dataroom et employe responsable resolus par jointure) */}
      <SupervisionDemandesPanel />

      {/* Echeances des demarches, dossiers a risque, temps moyen de cloture */}
      <Sprint10SupervisorPanel />
    </Page>
  );
}
