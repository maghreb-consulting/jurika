import { useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { AlarmClock, ArrowRight, Building2, CalendarClock, Clock, Inbox, MessageSquare, Send, Ticket as TicketIcon, User } from 'lucide-react';
import { useCurrentUser } from '../../store/authStore';
import { ticketService } from '../../services/ticket.service';
import { dataroomService } from '../../services/dataroom.service';
import { dashboardService } from '../../services/dashboard.service';
import { Page } from '../../components/layout/Page';
import { ProchaineActionCarte } from '../../components/dashboard/ProchaineActionCarte';
import { prochainesActions } from '../../components/dashboard/prochaineAction';
import { joursRestants } from '../../components/dashboard/delais';
import { KpiCard } from '../../components/dashboard/KpiCard';
import { SectionTableauDeBord } from '../../components/dashboard/SectionTableauDeBord';
import { PendingTransfersPanel } from '../../components/transfer/PendingTransfersPanel';
import { TexteAide } from '../../components/ui/Aide';
import { Badge, type BadgeVariant } from '../../components/ui/Badge';
import { Lien } from '../../components/ui/Lien';
import { Message } from '../../components/ui/Message';
import { TYPE_TICKET_LABELS, labelOf } from '../../lib/dashboardLabels';
import type { Ticket } from '../../types/ticket';
import type { EmployeDashboardDto } from '../../types/dashboard';
import type { DemandeSummary, DossierBrief, RequeteStatut, RequeteSummary } from '../../types/dataroom';
import { TYPE_REQUETE_LABELS } from '../../types/dataroom';

/**
 * Lot L6b (E17) : seuls les tickets OUVERTS figurent au tableau de bord (un ticket clos n'a plus
 * rien a faire ; la liste des tickets garde l'historique).
 */
const KANBAN_STATUTS = ['CREATION_TICKET', 'GENERATION_DOCUMENTS', 'DEROULEMENT_DEMARCHE'] as const;
const KANBAN_LABELS: Record<(typeof KANBAN_STATUTS)[number], string> = {
  CREATION_TICKET: 'Création et collecte',
  GENERATION_DOCUMENTS: 'Génération des documents',
  DEROULEMENT_DEMARCHE: 'Démarches',
};

const DEMANDE_COLUMNS: Array<{ key: 'NON_TRAITEE' | 'EN_COURS' | 'TRAITEE'; label: string; ton: BadgeVariant }> = [
  { key: 'NON_TRAITEE', label: 'Non traitées', ton: 'danger' },
  { key: 'EN_COURS', label: 'En cours', ton: 'warning' },
  { key: 'TRAITEE', label: 'Traitées', ton: 'success' },
];

// Lot AG -- machine a etats des requetes EMPLOYE_TO_CLIENT, cote employe :
//   A faire = OUVERTE + A_COMPLETER (chez le client) ; A valider = REPONDUE
//   (le client a repondu, j'atteste) ; Termine = CLOTUREE.
const REQUETE_COLUMNS: Array<{ key: 'A_FAIRE' | 'A_VALIDER' | 'TERMINE'; label: string; match: RequeteStatut[]; ton: BadgeVariant }> = [
  { key: 'A_FAIRE', label: 'Chez le client', match: ['OUVERTE', 'A_COMPLETER'], ton: 'neutral' },
  { key: 'A_VALIDER', label: 'À valider', match: ['REPONDUE'], ton: 'warning' },
  { key: 'TERMINE', label: 'Terminées', match: ['CLOTUREE'], ton: 'success' },
];

/** Horizon du compteur "Delai sous 7 jours". */
const HORIZON_JOURS = 7;

/** Nom du dataroom (raison sociale) avec repli sur les 8 premiers car. de l'UUID. */
function dataroomLabel(dossierId: string, nameById: Record<string, string>): string {
  return nameById[dossierId] ?? dossierId.slice(0, 8).toUpperCase();
}

function pluriel(n: number, singulier: string, plurielTexte: string): string {
  return `${n} ${n > 1 ? plurielTexte : singulier}`;
}

/**
 * Tableau de bord de l'employe.
 *
 * Lot L6b (E17, demande du 2026-10-16 "regle moi tout le dashboard") : hierarchie unique.
 * 1. Quatre indicateurs qui demandent une action : tickets en cours, tickets en retard, delais
 *    sous 7 jours, echanges clients a traiter (aucun doublon : l'ancien "Taches urgentes"
 *    recomptait les tickets en cours, "Clotures" ne demandait rien).
 * 2. Le travail : mes tickets en cours par etape, les demandes de mes clients, mes requetes.
 * 3. La carte "Prochaine action" (place choisie le 2026-10-16 : apres les sections de travail,
 *    a la place de l'ancien copilote).
 * Retires : le bandeau "N tickets en cours, pensez aux delais" (sans valeur), les anneaux de
 * repartition (le tableau par etape les remplace), la zone "Mes indicateurs" (tickets ouverts,
 * derniers tickets et delais assignes : tous deja affiches plus haut ; graphique par semaine a
 * axe decimal).
 *
 * Toutes les sources sont SCOPEES a l'employe connecte (ticketService.list : assigne ou createur ;
 * dossiers et demandes : ses dossiers ; requetes : les siennes ; delais des demarches :
 * dashboard-service, dossiers dont il est responsable). Une source en echec rend null : ses
 * compteurs affichent "--" et sa section le dit, jamais un zero invente (motif 9).
 */
export function EmployeeDashboard() {
  const user = useCurrentUser();
  const [tickets, setTickets] = useState<Ticket[] | null>([]);
  const [dossiers, setDossiers] = useState<DossierBrief[]>([]);
  const [demandes, setDemandes] = useState<DemandeSummary[] | null>([]);
  const [requetes, setRequetes] = useState<RequeteSummary[] | null>([]);
  const [indicateurs, setIndicateurs] = useState<Partial<EmployeDashboardDto> | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingIndicateurs, setLoadingIndicateurs] = useState(true);

  useEffect(() => {
    let mounted = true;
    Promise.all([
      ticketService.list({ limit: 50 }).catch(() => null),
      dataroomService.listDossiers().catch(() => [] as DossierBrief[]),
      dataroomService.listDemandes().catch(() => null),
      dataroomService.listMesRequetes().catch(() => null),
    ]).then(([t, dos, dem, req]) => {
      if (!mounted) return;
      setTickets(t ? (t.items ?? []) : null);
      setDossiers(dos);
      setDemandes(dem);
      setRequetes(req);
      setLoading(false);
    });
    // Delais legaux des demarches (dashboard-service) : un seul appel, lu par les compteurs de
    // retard et par la carte "Prochaine action".
    dashboardService
      .getEmploye()
      .then((d) => mounted && setIndicateurs(d ?? {}))
      .catch(() => mounted && setIndicateurs(null))
      .finally(() => mounted && setLoadingIndicateurs(false));
    return () => {
      mounted = false;
    };
  }, []);

  const ticketsAbsents = tickets === null;
  const ouverts = (tickets ?? []).filter((t) => (KANBAN_STATUTS as readonly string[]).includes(t.statut));
  const enCreation = ouverts.filter((t) => t.statut === 'CREATION_TICKET').length;

  const delaisDemarches = indicateurs ? (indicateurs.mesEcheancesAssignees ?? []) : null;
  const actions = tickets ? prochainesActions(tickets, delaisDemarches, user?.userId) : [];
  const aujourdHui = new Date();
  const ecarts = actions.filter((a) => a.echeance).map((a) => joursRestants(a.echeance!.date, aujourdHui));
  const enRetard = ecarts.filter((n) => n < 0).length;
  const sousHorizon = ecarts.filter((n) => n >= 0 && n <= HORIZON_JOURS).length;

  const ticketsByStatut: Record<string, Ticket[]> = { CREATION_TICKET: [], GENERATION_DOCUMENTS: [], DEROULEMENT_DEMARCHE: [] };
  for (const t of ouverts) ticketsByStatut[t.statut].push(t);

  const demandesByStatut: Record<string, DemandeSummary[]> = { NON_TRAITEE: [], EN_COURS: [], TRAITEE: [] };
  for (const d of demandes ?? []) {
    if (d.statut in demandesByStatut) demandesByStatut[d.statut].push(d);
  }
  const nonTraitees = demandesByStatut.NON_TRAITEE.length;

  const dossierNameById: Record<string, string> = {};
  for (const d of dossiers) dossierNameById[d.id] = d.raisonSociale;

  const requetesByColumn: Record<string, RequeteSummary[]> = { A_FAIRE: [], A_VALIDER: [], TERMINE: [] };
  for (const r of requetes ?? []) {
    const col = REQUETE_COLUMNS.find((c) => c.match.includes(r.statut));
    if (col) requetesByColumn[col.key].push(r);
  }
  const aValider = requetesByColumn.A_VALIDER.length;
  const echangesAbsents = demandes === null || requetes === null;
  const indiceDelais = indicateurs === null && !loadingIndicateurs ? 'dates limites des tickets seulement' : undefined;

  return (
    <Page
      titre="Tableau de bord"
      description={`Bonjour, ${user?.email?.split('@')[0] ?? 'Employé'}. Ce qui demande votre attention aujourd’hui : vos tickets en retard ou proches de leur délai, et les échanges de vos clients qui attendent une réponse.`}
      action={
        <Lien to="/tickets" commeBouton={{ variant: 'primary' }}>
          Mes tickets
        </Lien>
      }
      aide={
        <TexteAide cle="tableau-de-bord-employe" titre="Par où commencer ?">
          <p>
            Les quatre indicateurs du haut disent ce qui presse : un ticket « en retard » a dépassé la date limite
            fixée sur le ticket ou le délai légal d’une démarche du dossier. Traitez d’abord les transferts de
            dossier en attente, puis les demandes « Non traitées » de vos clients et les réponses « À valider ».
            La carte « Prochaine action », en bas de page, dit pour chaque ticket ce qu’il faut faire à son étape
            et quel délai court ; ouvrez le ticket pour voir ce qui manque avant l’étape suivante.
          </p>
        </TexteAide>
      }
    >
      {/* V9 -- Transferts de dossier en attente (recus a accepter/refuser + envoyes a annuler) */}
      <PendingTransfersPanel />

      <div className="mb-8 grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <KpiCard
          icon={<TicketIcon />}
          label="Tickets en cours"
          value={ticketsAbsents ? null : ouverts.length}
          hint={ticketsAbsents ? 'liste des tickets indisponible' : enCreation > 0 ? `dont ${enCreation} en création` : 'aucun en création'}
          loading={loading}
          to="/tickets"
          aide="Vos tickets ouverts : création et collecte, génération des documents ou démarches. Les tickets clôturés ou annulés ne comptent pas."
        />
        <KpiCard
          icon={<AlarmClock />}
          label="En retard"
          value={ticketsAbsents ? null : enRetard}
          variant={enRetard > 0 ? 'danger' : 'default'}
          hint={indiceDelais ?? (enRetard > 0 ? 'délai dépassé : à traiter en priorité' : 'aucun délai dépassé')}
          loading={loading || loadingIndicateurs}
          aide="Tickets en cours dont la date limite du ticket, ou le délai légal d’une démarche restant à accomplir sur le dossier, est dépassé."
        />
        <KpiCard
          icon={<CalendarClock />}
          label={`Délai sous ${HORIZON_JOURS} jours`}
          value={ticketsAbsents ? null : sousHorizon}
          variant={sousHorizon > 0 ? 'warning' : 'default'}
          hint={indiceDelais ?? 'tickets dont le délai tombe cette semaine'}
          loading={loading || loadingIndicateurs}
          aide={`Tickets en cours dont le délai le plus proche (date limite du ticket ou délai légal d’une démarche) tombe aujourd’hui ou dans les ${HORIZON_JOURS} prochains jours.`}
        />
        <KpiCard
          icon={<Inbox />}
          label="Échanges à traiter"
          value={echangesAbsents ? null : nonTraitees + aValider}
          variant={nonTraitees + aValider > 0 ? 'warning' : 'default'}
          hint={
            echangesAbsents
              ? 'échanges indisponibles'
              : `${pluriel(nonTraitees, 'demande non traitée', 'demandes non traitées')} · ${pluriel(aValider, 'réponse à valider', 'réponses à valider')}`
          }
          loading={loading}
          to="/data-rooms"
          aide="Demandes de vos clients que personne n’a encore prises en charge, et réponses de vos clients à vos requêtes qui attendent votre validation."
        />
      </div>

      <SectionTableauDeBord
        titre="Mes tickets en cours"
        icone={<TicketIcon />}
        etat={ticketsAbsents ? null : <Badge variant="neutral">{ouverts.length}</Badge>}
        lien={<Lien to="/tickets">Voir tous les tickets</Lien>}
      >
        {ticketsAbsents ? (
          <Message ton="attention" titre="Vos tickets n’ont pas pu être chargés">
            Rechargez la page dans quelques instants, ou ouvrez la liste des tickets.
          </Message>
        ) : (
          <div className="grid gap-4 md:grid-cols-3">
            {KANBAN_STATUTS.map((s) => (
              <Colonne key={s} titre={KANBAN_LABELS[s]} nombre={ticketsByStatut[s].length}>
                {ticketsByStatut[s].length === 0 ? (
                  <VideColonne>Aucun ticket à cette étape</VideColonne>
                ) : (
                  <>
                    {ticketsByStatut[s].slice(0, 4).map((t) => (
                      <TicketMiniCard key={t.id} ticket={t} />
                    ))}
                    {ticketsByStatut[s].length > 4 && (
                      <p className="px-1.5 text-xs text-fg-muted">
                        et {ticketsByStatut[s].length - 4} autre{ticketsByStatut[s].length - 4 > 1 ? 's' : ''} :{' '}
                        <Lien to="/tickets">voir la liste</Lien>
                      </p>
                    )}
                  </>
                )}
              </Colonne>
            ))}
          </div>
        )}
      </SectionTableauDeBord>

      <SectionTableauDeBord
        titre="Demandes de mes clients"
        icone={<MessageSquare />}
        etat={nonTraitees > 0 ? <Badge variant="danger">{nonTraitees} non traitée{nonTraitees > 1 ? 's' : ''}</Badge> : null}
        lien={<Lien to="/data-rooms">Voir les Data Rooms</Lien>}
      >
        {demandes === null ? (
          <Message ton="attention" titre="Les demandes de vos clients n’ont pas pu être chargées">
            Rechargez la page dans quelques instants ; chaque demande reste consultable dans la Data Room du dossier.
          </Message>
        ) : (
          <div className="grid gap-4 md:grid-cols-3">
            {DEMANDE_COLUMNS.map((col) => (
              <Colonne key={col.key} titre={col.label} nombre={demandesByStatut[col.key].length} ton={col.ton}>
                {demandesByStatut[col.key].length === 0 ? (
                  <VideColonne>Aucune demande</VideColonne>
                ) : (
                  demandesByStatut[col.key].slice(0, 4).map((d) => (
                    <DemandeMiniCard
                      key={d.id}
                      demande={d}
                      dossierName={dataroomLabel(d.dossierId, dossierNameById)}
                      showCreateTicket={col.key === 'NON_TRAITEE'}
                    />
                  ))
                )}
              </Colonne>
            ))}
          </div>
        )}
      </SectionTableauDeBord>

      {/* Lot AG -- requetes du cabinet au client (direction EMPLOYE_TO_CLIENT) */}
      <SectionTableauDeBord
        titre="Requêtes du cabinet au client"
        icone={<Send />}
        etat={aValider > 0 ? <Badge variant="warning">{aValider} à valider</Badge> : null}
        lien={<Lien to="/data-rooms">Voir les Data Rooms</Lien>}
      >
        {requetes === null ? (
          <Message ton="attention" titre="Vos requêtes n’ont pas pu être chargées">
            Rechargez la page dans quelques instants ; chaque requête reste consultable dans la Data Room du dossier.
          </Message>
        ) : requetes.length === 0 ? (
          <p className="rounded-xl border border-dashed border-border-hi bg-bg-raised p-6 text-center text-sm text-fg-muted">
            Aucune requête envoyée à vos clients. Une requête se crée depuis la Data Room du dossier, onglet « Requêtes du cabinet ».
          </p>
        ) : (
          <div className="grid gap-4 md:grid-cols-3">
            {REQUETE_COLUMNS.map((col) => (
              <Colonne key={col.key} titre={col.label} nombre={requetesByColumn[col.key].length} ton={col.ton}>
                {requetesByColumn[col.key].length === 0 ? (
                  <VideColonne>Aucune requête</VideColonne>
                ) : (
                  requetesByColumn[col.key]
                    .slice(0, 4)
                    .map((r) => <RequeteMiniCard key={r.id} requete={r} dossierName={dataroomLabel(r.dossierId, dossierNameById)} />)
                )}
              </Colonne>
            ))}
          </div>
        )}
      </SectionTableauDeBord>

      {/* Demande du 2026-10-16 : le copilote est retire ; la carte "Prochaine action" prend sa place. */}
      <ProchaineActionCarte tickets={tickets} echeances={delaisDemarches} userId={user?.userId} loading={loading || loadingIndicateurs} />
    </Page>
  );
}

function Colonne({ titre, nombre, ton = 'neutral', children }: { titre: string; nombre: number | null; ton?: BadgeVariant; children: ReactNode }) {
  return (
    <div className="min-w-0 rounded-xl border border-border bg-bg-overlay p-2">
      <div className="mb-2 flex items-center justify-between px-1.5 pt-1">
        <h3 className="text-xs font-semibold uppercase tracking-wide text-fg-muted">{titre}</h3>
        <Badge variant={ton}>{nombre ?? '--'}</Badge>
      </div>
      <div className="space-y-2">{children}</div>
    </div>
  );
}

function VideColonne({ children }: { children: ReactNode }) {
  return <p className="rounded-lg border border-dashed border-border-hi p-4 text-center text-xs text-fg-muted">{children}</p>;
}

/** Carte cliquable : le lien principal couvre la carte (::after), le clavier passe par lui. */
const CARTE = 'relative rounded-lg border border-border bg-bg-raised p-3 shadow-card transition-shadow duration-150 hover:border-border-hi hover:shadow-card-lifted';
const LIEN_ETIRE = 'rounded-sm text-sm font-semibold text-fg after:absolute after:inset-0 after:rounded-lg hover:underline';

const PRIORITE: Record<string, { libelle: string; ton: BadgeVariant }> = {
  URGENTE: { libelle: 'Urgente', ton: 'danger' },
  HAUTE: { libelle: 'Haute', ton: 'warning' },
  NORMALE: { libelle: 'Normale', ton: 'neutral' },
  BASSE: { libelle: 'Basse', ton: 'neutral' },
};

function TicketMiniCard({ ticket }: { ticket: Ticket }) {
  const priorite = ticket.priorite ? PRIORITE[ticket.priorite.toUpperCase()] : undefined;
  return (
    <div className={CARTE}>
      <div className="mb-1.5 flex items-center justify-between gap-2">
        <Badge variant="neutral">{labelOf(TYPE_TICKET_LABELS, ticket.type)}</Badge>
        <span className="font-mono text-xs text-fg-subtle">{ticket.reference}</span>
      </div>
      <Link to={`/workflows/${ticket.id}`} className={LIEN_ETIRE}>
        {ticket.titre}
      </Link>
      <p className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-fg-muted">
        {priorite && priorite.ton !== 'neutral' ? (
          <Badge variant={priorite.ton}>Priorité {priorite.libelle.toLowerCase()}</Badge>
        ) : (
          <span>Priorité {priorite ? priorite.libelle.toLowerCase() : 'non renseignée'}</span>
        )}
        {ticket.deadline && (
          <span className="inline-flex items-center gap-1">
            <Clock className="h-3 w-3" aria-hidden="true" />
            Date limite : {new Date(ticket.deadline).toLocaleDateString('fr-FR')}
          </span>
        )}
      </p>
    </div>
  );
}

/** Petit libelle « Dataroom : X » commun aux cartes demande / requete. */
function DataroomTag({ name }: { name: string }) {
  return (
    <p className="mb-1 flex items-center gap-1 text-xs text-fg-subtle" title={`Data Room : ${name}`}>
      <Building2 className="h-3 w-3 shrink-0" aria-hidden="true" />
      <span className="truncate">Data Room : {name}</span>
    </p>
  );
}

function DemandeMiniCard({ demande, dossierName, showCreateTicket }: { demande: DemandeSummary; dossierName: string; showCreateTicket: boolean }) {
  // Clic sur la demande -> ouvre directement le Data Room du dossier, onglet Demandes.
  const lien = `/data-rooms?dossier=${demande.dossierId}&tab=demandes`;
  return (
    <div className={CARTE}>
      <DataroomTag name={dossierName} />
      <p className="mb-1 flex items-center gap-1.5">
        <User className="h-3 w-3 shrink-0 text-fg-subtle" aria-hidden="true" />
        <Link to={lien} className={LIEN_ETIRE} title="Ouvrir dans la Data Room">
          {demande.sujet}
        </Link>
      </p>
      {demande.description && <p className="mb-2 line-clamp-2 text-xs text-fg-muted">{demande.description}</p>}
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs text-fg-subtle">{new Date(demande.createdAt).toLocaleDateString('fr-FR')}</span>
        {showCreateTicket && (
          <span className="relative z-10 inline-flex items-center gap-1 text-xs font-medium text-accent" aria-hidden="true">
            Traiter <ArrowRight className="h-3 w-3" />
          </span>
        )}
      </div>
    </div>
  );
}

function RequeteMiniCard({ requete, dossierName }: { requete: RequeteSummary; dossierName: string }) {
  // Clic sur la requete -> ouvre la Data Room du dossier, onglet « Requetes du cabinet »
  // (actions valider / complement). Lot L6 (E9) : l'onglet Demandes ne les porte plus
  // depuis le lot 1 ; le lien y menait encore.
  const lien = `/data-rooms?dossier=${requete.dossierId}&tab=requetes`;
  return (
    <div className={CARTE}>
      <div className="mb-1.5">
        <Badge variant="neutral">{requete.typeRequete ? TYPE_REQUETE_LABELS[requete.typeRequete] : 'Requête'}</Badge>
      </div>
      <DataroomTag name={dossierName} />
      <p className="mb-1 flex items-center gap-1.5">
        <Send className="h-3 w-3 shrink-0 text-fg-subtle" aria-hidden="true" />
        <Link to={lien} className={LIEN_ETIRE} title="Ouvrir dans la Data Room">
          {requete.sujet}
        </Link>
      </p>
      {requete.statut === 'REPONDUE' && requete.noteClient && (
        <p className="mb-2 line-clamp-2 rounded-md bg-success-soft p-1.5 text-xs text-fg">Réponse : {requete.noteClient}</p>
      )}
      <span className="text-xs text-fg-subtle">{new Date(requete.createdAt).toLocaleDateString('fr-FR')}</span>
    </div>
  );
}
