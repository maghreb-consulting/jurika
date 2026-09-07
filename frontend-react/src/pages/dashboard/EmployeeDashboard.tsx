import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
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
import { Sprint10EmployePanel } from '../../components/dashboard/Sprint10EmployePanel';
import { EmployeeChartsPanel } from '../../components/dashboard/EmployeeChartsPanel';
import { CopilotePanel } from '../../components/dashboard/CopilotePanel';
import { PendingTransfersPanel } from '../../components/transfer/PendingTransfersPanel';
import type { Ticket } from '../../types/ticket';
import type {
  DemandeSummary,
  DossierBrief,
  RequeteStatut,
  RequeteSummary,
} from '../../types/dataroom';
import { TYPE_REQUETE_LABELS } from '../../types/dataroom';

const KANBAN_STATUTS = [
  'CREATION_TICKET',
  'GENERATION_DOCUMENTS',
  'DEROULEMENT_DEMARCHE',
  'CLOTURE_DOSSIER',
] as const;
const KANBAN_LABELS: Record<string, string> = {
  CREATION_TICKET: 'CREATION',
  GENERATION_DOCUMENTS: 'GENERATION',
  DEROULEMENT_DEMARCHE: 'DEMARCHES',
  CLOTURE_DOSSIER: 'CLOTURE',
};

const DEMANDE_COLUMNS: Array<{ key: 'NON_TRAITEE' | 'EN_COURS' | 'TRAITEE'; label: string; bg: string }> = [
  { key: 'NON_TRAITEE', label: 'NON TRAITEES', bg: 'bg-danger' },
  { key: 'EN_COURS', label: 'EN COURS', bg: 'bg-accent/70' },
  { key: 'TRAITEE', label: 'TRAITEES', bg: 'bg-emerald-500' },
];

// Lot AG — machine a etats des requetes EMPLOYE_TO_CLIENT, cote employe :
//   À faire = OUVERTE + A_COMPLETER (chez le client) ; À valider = REPONDUE
//   (le client a repondu, j'atteste) ; Terminé = CLOTUREE.
const REQUETE_COLUMNS: Array<{ key: 'A_FAIRE' | 'A_VALIDER' | 'TERMINE'; label: string; match: RequeteStatut[]; bg: string }> = [
  { key: 'A_FAIRE', label: 'CHEZ LE CLIENT', match: ['OUVERTE', 'A_COMPLETER'], bg: 'bg-accent/70' },
  { key: 'A_VALIDER', label: 'A VALIDER', match: ['REPONDUE'], bg: 'bg-amber-500' },
  { key: 'TERMINE', label: 'TERMINEES', match: ['CLOTUREE'], bg: 'bg-emerald-500' },
];

/** Nom du dataroom (raison sociale) avec repli sur les 8 premiers car. de l'UUID. */
function dataroomLabel(dossierId: string, nameById: Record<string, string>): string {
  return nameById[dossierId] ?? dossierId.slice(0, 8).toUpperCase();
}

export function EmployeeDashboard() {
  const user = useCurrentUser();
  const navigate = useNavigate();
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [dossiers, setDossiers] = useState<DossierBrief[]>([]);
  const [demandes, setDemandes] = useState<DemandeSummary[]>([]);
  const [requetes, setRequetes] = useState<RequeteSummary[]>([]);
  const [loading, setLoading] = useState(true);

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
      ticketService.list({ limit: 50 }).catch(() => ({ items: [] as Ticket[] })),
      dataroomService.listDossiers().catch(() => [] as DossierBrief[]),
      dataroomService.listDemandes().catch(() => [] as DemandeSummary[]),
      dataroomService.listMesRequetes().catch(() => [] as RequeteSummary[]),
    ]).then(([t, dos, dem, req]) => {
      if (!mounted) return;
      setTickets(t.items ?? []);
      setDossiers(dos);
      setDemandes(dem);
      setRequetes(req);
      setLoading(false);
    });
    return () => {
      mounted = false;
    };
  }, []);

  // Compteurs 100% derives de MES donnees (aucun total workspace-wide).
  const dossiersInactifs = new Set(['CLOTURE', 'ARCHIVE', 'RADIE']);
  const dossiersActifs = dossiers.filter((d) => !dossiersInactifs.has(d.statut)).length;
  // « En cours » couvre les deux statuts de production : generation des actes,
  // puis deroulement des demarches administratives.
  const ticketsEnCours = tickets.filter(
    (t) => t.statut === 'GENERATION_DOCUMENTS' || t.statut === 'DEROULEMENT_DEMARCHE',
  ).length;
  const ticketsNouveaux = tickets.filter((t) => t.statut === 'CREATION_TICKET').length;
  // "Taches urgentes" = mes tickets EN_COURS.
  const taches = ticketsEnCours;
  const cloturesMois = tickets.filter((t) => t.statut === 'CLOTURE_DOSSIER').length;

  const ticketsByStatut: Record<string, Ticket[]> = {
    CREATION_TICKET: [],
    GENERATION_DOCUMENTS: [],
    DEROULEMENT_DEMARCHE: [],
    CLOTURE_DOSSIER: [],
  };
  for (const t of tickets) {
    if (t.statut in ticketsByStatut) ticketsByStatut[t.statut].push(t);
  }

  const demandesByStatut: Record<string, DemandeSummary[]> = {
    NON_TRAITEE: [],
    EN_COURS: [],
    TRAITEE: [],
  };
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
    <div className="p-6">
      <div className="mb-6">
        <h2 className="font-heading text-2xl font-semibold text-fg">
          Bonjour, {user?.email?.split('@')[0] ?? 'Employe'}
        </h2>
        <p className="text-sm text-fg-subtle">Voici un resume de votre activite.</p>
      </div>

      {/* V9 — Transferts de dossier en attente (recus a accepter/refuser + envoyes a annuler) */}
      <PendingTransfersPanel />

      <div className="mb-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <KpiCard icon={FolderOpen} iconBg="bg-accent/10" iconColor="text-accent"
          label="Dossiers actifs" value={dossiersActifs}
          tag={`${dossiers.length} dossier${dossiers.length > 1 ? 's' : ''} a moi`} tagColor="text-fg-subtle" loading={loading} />
        <KpiCard icon={TicketIcon} iconBg="bg-warning/10" iconColor="text-warning"
          label="Tickets en cours" value={ticketsEnCours}
          tag={`${ticketsNouveaux} nouveau${ticketsNouveaux > 1 ? 'x' : ''}`} tagColor="text-accent" loading={loading} />
        <KpiCard icon={AlertTriangle} iconBg="bg-rose-50" iconColor="text-rose-600"
          label="Taches urgentes" value={taches}
          tag={`sur ${tickets.length} de mes tickets`} tagColor="text-rose-600" loading={loading} />
        <KpiCard icon={CheckCircle} iconBg="bg-emerald-50" iconColor="text-success"
          label="Cloturees" value={cloturesMois}
          tag={`${tickets.length} de mes tickets`} tagColor="text-fg-subtle" loading={loading} />
      </div>

      {taches > 0 && (
        <div className="mb-6 flex items-center gap-3 rounded-lg border-l-4 border-amber-500 bg-warning/10 p-4">
          <AlertTriangle className="h-5 w-5 flex-shrink-0 text-amber-500" />
          <p className="text-sm text-amber-900">
            {taches} ticket{taches > 1 ? 's' : ''} en cours. Pensez a verifier les delais.
          </p>
        </div>
      )}

      <section className="mb-6">
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-lg font-semibold text-fg">Mes tickets actifs</h3>
          <a href="/tickets" className="text-sm font-medium text-accent hover:underline">
            Voir tous →
          </a>
        </div>
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {KANBAN_STATUTS.map((s) => (
            <div key={s}>
              <div className="rounded-t-lg bg-bg-overlay px-3 py-2">
                <h4 className="text-xs font-semibold uppercase text-fg-subtle">{KANBAN_LABELS[s]}</h4>
              </div>
              <div className="space-y-3 pt-3">
                {ticketsByStatut[s].length === 0 && (
                  <div className="rounded-lg border border-dashed border-border bg-bg-raised p-4 text-center text-xs text-fg-subtle">
                    Aucun ticket
                  </div>
                )}
                {ticketsByStatut[s].slice(0, 4).map((t) => (
                  <TicketMiniCard key={t.id} ticket={t} />
                ))}
              </div>
            </div>
          ))}
        </div>
      </section>

      <section className="mb-6">
        <div className="mb-4 flex items-center justify-between">
          <button
            type="button"
            onClick={() => navigate('/data-rooms')}
            className="group flex items-center gap-2 rounded-lg transition hover:opacity-80"
            title="Ouvrir les Data Rooms"
          >
            <MessageSquare className="h-5 w-5 text-violet-600" />
            <h3 className="text-lg font-semibold text-fg group-hover:underline">Demandes de mes clients</h3>
            {nonTraitees > 0 && (
              <span className="rounded-full bg-danger px-2 py-0.5 text-[10px] font-bold text-bg-raised">
                {nonTraitees} non traitee{nonTraitees > 1 ? 's' : ''}
              </span>
            )}
          </button>
          <button
            type="button"
            onClick={() => navigate('/data-rooms')}
            className="text-sm font-medium text-violet-600 hover:underline"
          >
            Voir tout →
          </button>
        </div>
        <div className="grid gap-4 md:grid-cols-3">
          {DEMANDE_COLUMNS.map((col) => (
            <div key={col.key}>
              <div className={`flex items-center justify-between rounded-t-lg px-4 py-2.5 ${col.bg} text-bg-raised`}>
                <h4 className="text-xs font-bold uppercase tracking-wider">{col.label}</h4>
                <span className="flex h-5 w-5 items-center justify-center rounded-full bg-bg-raised/20 text-[10px] font-bold">
                  {demandesByStatut[col.key].length}
                </span>
              </div>
              <div className="space-y-3 pt-3">
                {demandesByStatut[col.key].length === 0 && (
                  <div className="rounded-lg border border-dashed border-border bg-bg-raised p-4 text-center text-xs text-fg-subtle">
                    Aucune demande
                  </div>
                )}
                {demandesByStatut[col.key].slice(0, 4).map((d) => (
                  <DemandeMiniCard
                    key={d.id}
                    demande={d}
                    dossierName={dataroomLabel(d.dossierId, dossierNameById)}
                    showCreateTicket={col.key === 'NON_TRAITEE'}
                  />
                ))}
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Lot AG — Mes requetes au client (direction EMPLOYE_TO_CLIENT) */}
      <section className="mb-6">
        <div className="mb-4 flex items-center justify-between">
          <button
            type="button"
            onClick={() => navigate('/data-rooms')}
            className="group flex items-center gap-2 rounded-lg transition hover:opacity-80"
            title="Ouvrir les Data Rooms"
          >
            <Send className="h-5 w-5 text-accent" />
            <h3 className="text-lg font-semibold text-fg group-hover:underline">Mes requetes au client</h3>
            {aValider > 0 && (
              <span className="rounded-full bg-amber-500 px-2 py-0.5 text-[10px] font-bold text-bg-raised">
                {aValider} a valider
              </span>
            )}
          </button>
        </div>
        {requetes.length === 0 ? (
          <div className="rounded-lg border border-dashed border-border bg-bg-raised p-6 text-center text-sm text-fg-subtle">
            Aucune requete envoyee a vos clients.
          </div>
        ) : (
          <div className="grid gap-4 md:grid-cols-3">
            {REQUETE_COLUMNS.map((col) => (
              <div key={col.key}>
                <div className={`flex items-center justify-between rounded-t-lg px-4 py-2.5 ${col.bg} text-bg-raised`}>
                  <h4 className="text-xs font-bold uppercase tracking-wider">{col.label}</h4>
                  <span className="flex h-5 w-5 items-center justify-center rounded-full bg-bg-raised/20 text-[10px] font-bold">
                    {requetesByColumn[col.key].length}
                  </span>
                </div>
                <div className="space-y-3 pt-3">
                  {requetesByColumn[col.key].length === 0 && (
                    <div className="rounded-lg border border-dashed border-border bg-bg-raised p-4 text-center text-xs text-fg-subtle">
                      Aucune requete
                    </div>
                  )}
                  {requetesByColumn[col.key].slice(0, 4).map((r) => (
                    <RequeteMiniCard
                      key={r.id}
                      requete={r}
                      dossierName={dataroomLabel(r.dossierId, dossierNameById)}
                    />
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Graphiques : repartition de mes tickets (statut + type) */}
      <EmployeeChartsPanel tickets={tickets} />

      {/* Lot IA-1 -- agent copilote (plan du jour, echeances) */}
      <div id="copilote">
        <CopilotePanel />
      </div>

      {/* Sprint 10 -- ma charge semaine + echeances assignees */}
      <Sprint10EmployePanel />
    </div>
  );
}

function KpiCard({
  icon: Icon,
  iconBg,
  iconColor,
  label,
  value,
  tag,
  tagColor,
  loading,
}: {
  icon: typeof FolderOpen;
  iconBg: string;
  iconColor: string;
  label: string;
  value: number;
  tag: string;
  tagColor: string;
  loading: boolean;
}) {
  return (
    <div className="rounded-xl border border-border bg-bg-raised p-5 shadow-sm">
      <div className={`mb-3 flex h-12 w-12 items-center justify-center rounded-lg ${iconBg}`}>
        <Icon className={`h-6 w-6 ${iconColor}`} />
      </div>
      <p className="mb-1 text-sm text-fg-subtle">{label}</p>
      <p className="mb-2 text-3xl font-bold text-fg">{loading ? '—' : value}</p>
      <p className={`text-xs ${tagColor}`}>{tag}</p>
    </div>
  );
}

function TicketMiniCard({ ticket }: { ticket: Ticket }) {
  return (
    <a
      href={`/workflows/${ticket.id}`}
      className="block rounded-lg border border-border bg-bg-raised p-4 shadow-sm transition hover:shadow-md"
    >
      <div className="mb-2 flex items-center justify-between">
        <span className="rounded bg-accent/10 px-2 py-1 text-xs text-accent">{ticket.type}</span>
        <span className="font-mono text-xs text-fg-subtle">{ticket.reference}</span>
      </div>
      <h5 className="mb-3 text-sm font-semibold text-fg">{ticket.titre}</h5>
      <div className="flex items-center justify-between">
        <div className="flex h-6 w-6 items-center justify-center rounded-full bg-accent text-xs font-semibold text-bg-raised">
          {ticket.titre[0]?.toUpperCase() ?? '?'}
        </div>
        <span className="flex items-center gap-1 text-xs text-fg-subtle">
          <Clock className="h-3 w-3" />
          {ticket.priorite}
        </span>
      </div>
    </a>
  );
}

/** Petit libelle « Dataroom : X » commun aux cartes demande / requete. */
function DataroomTag({ name }: { name: string }) {
  return (
    <div className="mb-1 flex items-center gap-1 text-[10px] text-fg-subtle" title={`Dataroom : ${name}`}>
      <Building2 className="h-3 w-3 shrink-0" />
      <span className="truncate">Dataroom : {name}</span>
    </div>
  );
}

function DemandeMiniCard({
  demande,
  dossierName,
  showCreateTicket,
}: {
  demande: DemandeSummary;
  dossierName: string;
  showCreateTicket: boolean;
}) {
  const navigate = useNavigate();
  // Clic sur la demande -> ouvre directement le Data Room du dossier, onglet Demandes.
  const openDataroom = () =>
    navigate(`/data-rooms?dossier=${demande.dossierId}&tab=demandes`);
  return (
    <div
      role="button"
      tabIndex={0}
      onClick={openDataroom}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          openDataroom();
        }
      }}
      title="Ouvrir dans le Data Room"
      className="cursor-pointer rounded-lg border border-border bg-bg-raised p-4 shadow-sm transition hover:border-violet-300 hover:shadow-md"
    >
      <div className="mb-2 flex items-center justify-between">
        <span className="font-mono text-[10px] text-fg-subtle">
          {demande.id.slice(0, 8).toUpperCase()}
        </span>
      </div>
      <DataroomTag name={dossierName} />
      <div className="mb-1 flex items-center gap-1.5">
        <User className="h-3 w-3 text-fg-subtle" />
        <span className="text-xs font-semibold text-fg">{demande.sujet}</span>
      </div>
      {demande.description && (
        <p className="mb-2 line-clamp-2 text-xs text-fg-subtle">{demande.description}</p>
      )}
      <div className="flex items-center justify-between">
        <span className="text-[10px] text-fg-subtle">
          {new Date(demande.createdAt).toLocaleDateString('fr-FR')}
        </span>
        {showCreateTicket && (
          <button
            type="button"
            onClick={(e) => {
              // Ne pas declencher la navigation de la carte.
              e.stopPropagation();
              openDataroom();
            }}
            className="flex items-center gap-1 rounded bg-accent/10 px-2 py-1 text-[10px] font-medium text-accent transition hover:bg-accent/20"
          >
            <ArrowRight className="h-3 w-3" /> Traiter
          </button>
        )}
      </div>
    </div>
  );
}

function RequeteMiniCard({ requete, dossierName }: { requete: RequeteSummary; dossierName: string }) {
  const navigate = useNavigate();
  // Clic sur la requete -> ouvre le Data Room du dossier, onglet Demandes (ou se
  // trouve la section « Mes requetes au client » avec les actions valider/complement).
  const openDataroom = () =>
    navigate(`/data-rooms?dossier=${requete.dossierId}&tab=demandes`);
  return (
    <div
      role="button"
      tabIndex={0}
      onClick={openDataroom}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          openDataroom();
        }
      }}
      title="Ouvrir dans le Data Room"
      className="cursor-pointer rounded-lg border border-border bg-bg-raised p-4 shadow-sm transition hover:border-accent/40 hover:shadow-md"
    >
      <div className="mb-2 flex items-center justify-between">
        <span className="rounded bg-accent/10 px-2 py-0.5 text-[10px] font-semibold text-accent">
          {requete.typeRequete ? TYPE_REQUETE_LABELS[requete.typeRequete] : 'Requete'}
        </span>
        <span className="font-mono text-[10px] text-fg-subtle">
          {requete.id.slice(0, 8).toUpperCase()}
        </span>
      </div>
      <DataroomTag name={dossierName} />
      <div className="mb-1 flex items-center gap-1.5">
        <Send className="h-3 w-3 text-fg-subtle" />
        <span className="text-xs font-semibold text-fg">{requete.sujet}</span>
      </div>
      {requete.statut === 'REPONDUE' && requete.noteClient && (
        <p className="mb-2 line-clamp-2 rounded bg-emerald-50 p-1.5 text-[11px] text-emerald-800">
          Reponse : {requete.noteClient}
        </p>
      )}
      <span className="text-[10px] text-fg-subtle">
        {new Date(requete.createdAt).toLocaleDateString('fr-FR')}
      </span>
    </div>
  );
}
