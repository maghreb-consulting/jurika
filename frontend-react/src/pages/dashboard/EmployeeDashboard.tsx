import { useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowRight,
  Building2,
  CheckCircle,
  Clock,
  FolderOpen,
  MessageSquare,
  Send,
  Ticket as TicketIcon,
  User,
} from 'lucide-react';
import { useCurrentUser } from '../../store/authStore';
import { ticketService } from '../../services/ticket.service';
import { dataroomService } from '../../services/dataroom.service';
import { dashboardService } from '../../services/dashboard.service';
import { Page } from '../../components/layout/Page';
import { Sprint10EmployePanel } from '../../components/dashboard/Sprint10EmployePanel';
import { EmployeeChartsPanel } from '../../components/dashboard/EmployeeChartsPanel';
import { ProchaineActionCarte } from '../../components/dashboard/ProchaineActionCarte';
import { KpiCard } from '../../components/dashboard/KpiCard';
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

const KANBAN_STATUTS = ['CREATION_TICKET', 'GENERATION_DOCUMENTS', 'DEROULEMENT_DEMARCHE', 'CLOTURE_DOSSIER'] as const;
const KANBAN_LABELS: Record<string, string> = {
  CREATION_TICKET: 'Création',
  GENERATION_DOCUMENTS: 'Génération',
  DEROULEMENT_DEMARCHE: 'Démarches',
  CLOTURE_DOSSIER: 'Clôture',
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

/** Nom du dataroom (raison sociale) avec repli sur les 8 premiers car. de l'UUID. */
function dataroomLabel(dossierId: string, nameById: Record<string, string>): string {
  return nameById[dossierId] ?? dossierId.slice(0, 8).toUpperCase();
}

/**
 * Tableau de bord de l'employe (lot L6b, E9) : ses indicateurs, ses tickets par statut, les
 * demandes de ses clients, ses requetes, ses graphiques, puis la carte "Prochaine action" (a la
 * place du copilote, retire le 2026-10-16) et sa charge de la semaine.
 */
export function EmployeeDashboard() {
  const user = useCurrentUser();
  const [tickets, setTickets] = useState<Ticket[] | null>([]);
  const [dossiers, setDossiers] = useState<DossierBrief[]>([]);
  const [demandes, setDemandes] = useState<DemandeSummary[]>([]);
  const [requetes, setRequetes] = useState<RequeteSummary[]>([]);
  const [indicateurs, setIndicateurs] = useState<Partial<EmployeDashboardDto> | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingIndicateurs, setLoadingIndicateurs] = useState(true);

  useEffect(() => {
    let mounted = true;
    // Toutes les sources sont SCOPEES a l'employe connecte :
    //  - ticketService.list        -> assigne_or_creePar = userId (RG-U08) ;
    //  - dataroomService.listDossiers -> responsable_id = userId (Lot Q) ;
    //  - dataroomService.listDemandes -> demandes de MES dossiers (Lot S3).
    //  - dataroomService.listMesRequetes -> MES requetes au client (EMPLOYE_TO_CLIENT).
    // On n'appelle PLUS supervisionService.kpis() (agregat WORKSPACE-WIDE) qui
    // faisait fuiter des totaux globaux (ex. "23 dossiers") a un employe qui
    // n'en gere qu'un.
    Promise.all([
      ticketService.list({ limit: 50 }).catch(() => null),
      dataroomService.listDossiers().catch(() => [] as DossierBrief[]),
      dataroomService.listDemandes().catch(() => [] as DemandeSummary[]),
      dataroomService.listMesRequetes().catch(() => [] as RequeteSummary[]),
    ]).then(([t, dos, dem, req]) => {
      if (!mounted) return;
      setTickets(t ? (t.items ?? []) : null);
      setDossiers(dos);
      setDemandes(dem);
      setRequetes(req);
      setLoading(false);
    });
    // Indicateurs de dashboard-service (echeances des demarches) : un seul appel, partage par
    // la carte "Prochaine action" et le panneau des indicateurs.
    dashboardService
      .getEmploye()
      .then((d) => mounted && setIndicateurs(d ?? {}))
      .catch(() => mounted && setIndicateurs(null))
      .finally(() => mounted && setLoadingIndicateurs(false));
    return () => {
      mounted = false;
    };
  }, []);

  const mesTickets = tickets ?? [];
  // Compteurs 100% derives de MES donnees (aucun total workspace-wide).
  const dossiersInactifs = new Set(['CLOTURE', 'ARCHIVE', 'RADIE']);
  const dossiersActifs = dossiers.filter((d) => !dossiersInactifs.has(d.statut)).length;
  // « En cours » couvre les deux statuts de production : generation des actes,
  // puis deroulement des demarches administratives.
  const ticketsEnCours = mesTickets.filter((t) => t.statut === 'GENERATION_DOCUMENTS' || t.statut === 'DEROULEMENT_DEMARCHE').length;
  const ticketsNouveaux = mesTickets.filter((t) => t.statut === 'CREATION_TICKET').length;
  // "Taches urgentes" = mes tickets EN_COURS.
  const taches = ticketsEnCours;
  const cloturesMois = mesTickets.filter((t) => t.statut === 'CLOTURE_DOSSIER').length;
  const ticketsAbsents = tickets === null;

  const ticketsByStatut: Record<string, Ticket[]> = { CREATION_TICKET: [], GENERATION_DOCUMENTS: [], DEROULEMENT_DEMARCHE: [], CLOTURE_DOSSIER: [] };
  for (const t of mesTickets) {
    if (t.statut in ticketsByStatut) ticketsByStatut[t.statut].push(t);
  }

  const demandesByStatut: Record<string, DemandeSummary[]> = { NON_TRAITEE: [], EN_COURS: [], TRAITEE: [] };
  for (const d of demandes) {
    if (d.statut in demandesByStatut) demandesByStatut[d.statut].push(d);
  }
  const nonTraitees = demandesByStatut.NON_TRAITEE.length;

  // Map dossierId -> raisonSociale (jointure front, aucun appel back en plus).
  const dossierNameById: Record<string, string> = {};
  for (const d of dossiers) dossierNameById[d.id] = d.raisonSociale;

  // Requetes au client regroupees par colonne de la machine a etats.
  const requetesByColumn: Record<string, RequeteSummary[]> = { A_FAIRE: [], A_VALIDER: [], TERMINE: [] };
  for (const r of requetes) {
    const col = REQUETE_COLUMNS.find((c) => c.match.includes(r.statut));
    if (col) requetesByColumn[col.key].push(r);
  }
  const aValider = requetesByColumn.A_VALIDER.length;

  return (
    <Page
      titre="Tableau de bord"
      description={`Bonjour, ${user?.email?.split('@')[0] ?? 'Employé'}. Votre journée en un coup d’œil : ce qu’il faut faire maintenant, vos tickets par statut, les demandes de vos clients et les requêtes qui attendent une réponse.`}
      action={
        <Lien to="/tickets" commeBouton={{ variant: 'primary' }}>
          Mes tickets
        </Lien>
      }
      aide={
        <TexteAide cle="tableau-de-bord-employe" titre="Par où commencer ?">
          <p>
            Traitez d’abord les transferts de dossier en attente et les demandes « Non traitées » de vos
            clients. Suivez ensuite la carte « Prochaine action » : pour chaque ticket en cours, elle dit
            ce qu’il faut faire à son statut et l’échéance la plus proche ; ouvrez le ticket pour voir ce
            qui manque. Les requêtes « À valider » attendent votre vérification de la réponse du client.
          </p>
        </TexteAide>
      }
    >
      {/* V9 -- Transferts de dossier en attente (recus a accepter/refuser + envoyes a annuler) */}
      <PendingTransfersPanel />


      <div className="mb-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <KpiCard
          icon={<FolderOpen />}
          label="Dossiers actifs"
          value={dossiersActifs}
          hint={`${dossiers.length} dossier${dossiers.length > 1 ? 's' : ''} à vous`}
          loading={loading}
        />
        <KpiCard
          icon={<TicketIcon />}
          label="Tickets en cours"
          value={ticketsAbsents ? null : ticketsEnCours}
          hint={ticketsAbsents ? undefined : `${ticketsNouveaux} nouveau${ticketsNouveaux > 1 ? 'x' : ''}`}
          loading={loading}
        />
        <KpiCard
          icon={<AlertTriangle />}
          label="Tâches urgentes"
          value={ticketsAbsents ? null : taches}
          variant={taches > 0 ? 'warning' : 'default'}
          hint={ticketsAbsents ? undefined : `sur ${mesTickets.length} de mes tickets`}
          loading={loading}
        />
        <KpiCard
          icon={<CheckCircle />}
          label="Clôturés"
          value={ticketsAbsents ? null : cloturesMois}
          variant="success"
          hint={ticketsAbsents ? undefined : `${mesTickets.length} de mes tickets`}
          loading={loading}
        />
      </div>

      {taches > 0 && (
        <Message ton="attention" className="mb-6">
          {taches} ticket{taches > 1 ? 's' : ''} en cours. Pensez à vérifier les délais.
        </Message>
      )}

      <Section
        titre="Mes tickets actifs"
        icone={<TicketIcon />}
        lien={<Lien to="/tickets">Voir tous les tickets</Lien>}
      >
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {KANBAN_STATUTS.map((s) => (
            <Colonne key={s} titre={KANBAN_LABELS[s]} nombre={ticketsAbsents ? null : ticketsByStatut[s].length}>
              {ticketsByStatut[s].length === 0 ? (
                <VideColonne>{ticketsAbsents ? 'Tickets indisponibles' : 'Aucun ticket'}</VideColonne>
              ) : (
                ticketsByStatut[s].slice(0, 4).map((t) => <TicketMiniCard key={t.id} ticket={t} />)
              )}
            </Colonne>
          ))}
        </div>
      </Section>

      <Section
        titre="Demandes de mes clients"
        icone={<MessageSquare />}
        etat={nonTraitees > 0 ? <Badge variant="danger">{nonTraitees} non traitée{nonTraitees > 1 ? 's' : ''}</Badge> : null}
        lien={<Lien to="/data-rooms">Voir les Data Rooms</Lien>}
      >
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
      </Section>

      {/* Lot AG -- requetes du cabinet au client (direction EMPLOYE_TO_CLIENT) */}
      <Section
        titre="Requêtes du cabinet au client"
        icone={<Send />}
        etat={aValider > 0 ? <Badge variant="warning">{aValider} à valider</Badge> : null}
        lien={<Lien to="/data-rooms">Voir les Data Rooms</Lien>}
      >
        {requetes.length === 0 ? (
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
      </Section>

      {/* Graphiques : repartition de mes tickets (statut + type) */}
      <EmployeeChartsPanel tickets={mesTickets} />

      {/* Demande du 2026-10-16 : le copilote est retire ; la carte "Prochaine action" prend sa place. */}
      <ProchaineActionCarte
        tickets={tickets}
        echeances={indicateurs ? (indicateurs.mesEcheancesAssignees ?? []) : null}
        userId={user?.userId}
        loading={loading || loadingIndicateurs}
      />

      {/* Sprint 10 -- ma charge semaine + echeances assignees */}
      <Sprint10EmployePanel data={indicateurs} loading={loadingIndicateurs} />
    </Page>
  );
}

function Section({ titre, icone, etat, lien, children }: { titre: string; icone: ReactNode; etat?: ReactNode; lien?: ReactNode; children: ReactNode }) {
  return (
    <section className="mb-8">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h2 className="flex items-center gap-2 text-lg font-semibold text-heading">
          <span className="text-fg-subtle [&>svg]:h-5 [&>svg]:w-5" aria-hidden="true">
            {icone}
          </span>
          {titre}
          {etat}
        </h2>
        {lien && <span className="text-sm">{lien}</span>}
      </div>
      {children}
    </section>
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

function TicketMiniCard({ ticket }: { ticket: Ticket }) {
  return (
    <div className={CARTE}>
      <div className="mb-1.5 flex items-center justify-between gap-2">
        <Badge variant="neutral">{labelOf(TYPE_TICKET_LABELS, ticket.type)}</Badge>
        <span className="font-mono text-xs text-fg-subtle">{ticket.reference}</span>
      </div>
      <Link to={`/workflows/${ticket.id}`} className={LIEN_ETIRE}>
        {ticket.titre}
      </Link>
      <p className="mt-2 flex items-center gap-1 text-xs text-fg-muted">
        <Clock className="h-3 w-3" aria-hidden="true" />
        Priorité : {ticket.priorite ? ticket.priorite.toLowerCase() : '--'}
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
