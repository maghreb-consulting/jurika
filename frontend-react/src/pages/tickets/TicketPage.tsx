import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, ArrowRight } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { ticketService } from '../../services/ticket.service';
import { workflowService, type VariableDuMagasin } from '../../services/workflow.service';
import { dataroomService } from '../../services/dataroom.service';
import { useCurrentUser } from '../../store/authStore';
import { extractError } from '../../lib/api';
import { STATUT_LABELS, TICKET_TYPE_LABELS } from '../../types/ticket';
import type { Ticket, TicketStatut } from '../../types/ticket';
import type { DocumentSummary, DossierTicket } from '../../types/dataroom';
import { DOCUMENT_TYPE_LABELS } from '../../types/dataroom';
import { referenceDocument } from '../../lib/referencesLisibles';
import { ProvenanceDonnees } from '../../components/workflow/ProvenanceDonnees';
import { DecisionsTicketPanel } from './DecisionsTicketPanel';
import { DeboursPanel } from './DeboursPanel';
import { DemarchesPanel } from './DemarchesPanel';
import { NoteTicketPanel } from './NoteTicketPanel';
import { RecapitulatifPanel } from './RecapitulatifPanel';

/**
 * Lot L4d (E9) : la page du ticket, une seule pour tous les parcours. Elle réunit ce que
 * l'employé fait et ce que le superviseur suit : passage de statut (obstacles nommés avant la
 * demande, refus du serveur avec sa raison — RG-TKT-06), décisions, données saisies et leur
 * provenance, documents du ticket, démarches, note interne, récapitulatif. Le parcours
 * (saisie et génération) reste sur sa page propre, ouverte d'ici.
 */
interface Passage {
  statut: string;
  suivant: string | null;
  obstacles: string[];
}

function libelleStatut(s: string | null): string {
  return s ? (STATUT_LABELS as Record<string, string>)[s] ?? s : '—';
}

export function TicketPage() {
  const { ticketId = '' } = useParams<{ ticketId: string }>();
  const user = useCurrentUser();
  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [acces, setAcces] = useState<{ enCharge: boolean; peutAgir: boolean; raison: string | null } | null>(null);
  const [passage, setPassage] = useState<Passage | null>(null);
  const [variables, setVariables] = useState<VariableDuMagasin[]>([]);
  const [documents, setDocuments] = useState<{ societe: string; dossier: DossierTicket | null } | null>(null);
  const [refus, setRefus] = useState<string | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  const [enCours, setEnCours] = useState(false);
  const [versionDemarches, setVersionDemarches] = useState(0);

  const charger = useCallback(async () => {
    try {
      const t = await ticketService.get(ticketId);
      setTicket(t);
      ticketService.acces(ticketId).then(setAcces).catch(() => setAcces({ enCharge: false, peutAgir: false, raison: null }));
      ticketService.passage(ticketId).then(setPassage).catch(() => setPassage(null));
      workflowService.variables(ticketId).then(setVariables).catch(() => setVariables([]));
      if (t.dossierId) {
        dataroomService.getJuridique(t.dossierId)
          .then((v) => setDocuments({
            societe: v.raisonSociale,
            dossier: v.dossiersParTicket.find((d) => d.ticketId === ticketId) ?? null,
          }))
          .catch(() => setDocuments(null));
      }
    } catch (e) {
      setErreur(extractError(e).message);
    }
  }, [ticketId]);

  useEffect(() => {
    // Chargement initial par promesse (aucun etat pose de facon synchrone dans l'effet).
    Promise.resolve().then(charger).catch(() => undefined);
  }, [charger]);

  const canAct = user?.role === 'EMPLOYE' && acces?.peutAgir === true;
  const actif = ticket ? ticket.statut !== 'CLOTURE_DOSSIER' && ticket.statut !== 'ANNULE' : false;

  async function passer() {
    if (!passage?.suivant) return;
    setRefus(null);
    setEnCours(true);
    try {
      await ticketService.transition(ticketId, { target: passage.suivant as TicketStatut });
      await charger();
    } catch (e) {
      // RG-TKT-06 : le refus du serveur s'affiche tel quel, avec sa raison.
      setRefus(extractError(e).message);
      ticketService.passage(ticketId).then(setPassage).catch(() => undefined);
    } finally {
      setEnCours(false);
    }
  }

  if (erreur) return <p role="alert" className="p-6 text-sm text-danger">{erreur}</p>;
  if (!ticket) return <p className="p-6 text-sm text-fg-subtle">Chargement du ticket…</p>;

  const docs: DocumentSummary[] = (documents?.dossier?.groupes ?? []).flatMap((g) => g.documents);

  return (
    <div className="mx-auto max-w-5xl space-y-5 p-4 md:p-6" data-testid="page-ticket">
      <Link to="/tickets" className="inline-flex items-center gap-1 text-sm text-fg-subtle hover:text-fg">
        <ArrowLeft className="h-4 w-4" /> Tous les tickets
      </Link>

      <header className="space-y-1">
        <div className="flex flex-wrap items-center gap-2">
          <h1 className="text-xl font-bold text-fg">{ticket.titre}</h1>
          <Badge variant="neutral">{libelleStatut(ticket.statut)}</Badge>
        </div>
        <p className="text-sm text-fg-subtle">
          {ticket.reference} — {TICKET_TYPE_LABELS[ticket.type]}
        </p>
        {acces && !canAct && (
          <p className="text-xs text-fg-subtle" data-testid="acces-raison">
            {acces.raison ?? 'Consultation seule — actions réservées à l’employé responsable du dossier.'}
          </p>
        )}
      </header>

      <TexteAide cle="page-ticket" titre="Comment utiliser cette page ?">
        <p>
          Tout le ticket est ici, quel que soit le parcours. De haut en bas : ce qui empêche de passer au
          statut suivant, les décisions qui choisissent les démarches, les données déjà saisies et leur
          provenance, les documents produits ou déposés, les débours et leur état, les démarches à
          cocher et la note interne.
          La saisie et la génération des actes se font sur la page du parcours, ouverte par le bouton
          « Ouvrir le parcours ».
        </p>
      </TexteAide>

      {/* Passage de statut : obstacles nommés avant la demande, refus avec sa raison (RG-TKT-06). */}
      <section className="space-y-2 rounded-xl border border-border bg-bg-raised p-4" data-testid="passage-statut">
        <h2 className="flex items-center gap-1 text-sm font-semibold text-fg">
          Passage de statut
          <InfoBulle
            libelle="Comment le ticket avance-t-il ?"
            texte="Le ticket suit quatre statuts : création, génération des documents, déroulement de la démarche, clôture. Pour passer au suivant, les démarches obligatoires du statut en cours doivent être cochées et les données attendues saisies. Ce qui manque est listé ici avant toute demande."
          />
        </h2>
        {passage?.suivant ? (
          <>
            <p className="text-sm text-fg">
              Statut suivant : <strong>{libelleStatut(passage.suivant)}</strong>
            </p>
            {passage.obstacles.length > 0 ? (
              <div data-testid="obstacles">
                <p className="text-sm text-warning">Le passage est impossible tant que ceci n’est pas réglé :</p>
                <ul className="ml-5 list-disc text-sm text-fg">
                  {passage.obstacles.map((o) => <li key={o}>{o}</li>)}
                </ul>
              </div>
            ) : (
              <p className="text-sm text-success" data-testid="passage-possible">Rien ne s’oppose au passage.</p>
            )}
            {canAct && (
              <Button onClick={passer} disabled={enCours || passage.obstacles.length > 0}>
                Passer à « {libelleStatut(passage.suivant)} » <ArrowRight className="ml-1 h-4 w-4" />
              </Button>
            )}
          </>
        ) : (
          <p className="text-sm text-fg-subtle">
            {ticket.statut === 'ANNULE'
              ? 'Ticket annulé : il ne reprend qu’à son statut antérieur, depuis la liste des tickets.'
              : 'Ticket clos : il n’y a plus de statut suivant.'}
          </p>
        )}
        {refus && (
          <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-2 text-sm text-danger" data-testid="refus-transition">
            Passage refusé : {refus}
          </p>
        )}
        {canAct && actif && (
          <Link to={`/workflows/${ticket.id}`} className="inline-block text-sm text-accent underline">
            Ouvrir le parcours (saisie et génération des actes)
          </Link>
        )}
      </section>

      {ticket.type !== 'CREATION' && (
        <DecisionsTicketPanel ticketId={ticketId} canAct={canAct && actif}
          onChanged={() => { setVersionDemarches((v) => v + 1); void ticketService.passage(ticketId).then(setPassage); }} />
      )}

      <section className="space-y-2" data-testid="saisies-ticket">
        <h2 className="text-sm font-semibold text-fg">Données saisies</h2>
        {variables.filter((v) => !v.boucle && v.valeur).length === 0 ? (
          <p className="text-sm text-fg-subtle">Aucune donnée saisie pour ce ticket : elles apparaissent ici au fil du parcours.</p>
        ) : (
          <ProvenanceDonnees variables={variables} />
        )}
      </section>

      <section className="space-y-2 rounded-xl border border-border bg-bg-raised p-4" data-testid="documents-ticket">
        <h2 className="flex items-center gap-1 text-sm font-semibold text-fg">
          Documents du ticket
          <InfoBulle
            libelle="Quels documents figurent ici ?"
            texte="Les actes générés et les pièces déposées pour ce ticket, rangés dans la Data Room du dossier. Chaque document se lit par son type, la société et sa version."
          />
        </h2>
        {docs.length === 0 ? (
          <p className="text-sm text-fg-subtle">Aucun document pour ce ticket pour l’instant.</p>
        ) : (
          <ul className="divide-y divide-border text-sm">
            {docs.map((d) => (
              <li key={d.id} className="py-1.5 text-fg">
                {referenceDocument((DOCUMENT_TYPE_LABELS as Record<string, string>)[String(d.documentType)] ?? String(d.documentType),
                  documents?.societe, d.version)}
                <span className="text-fg-subtle"> — {d.title}</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <DeboursPanel ticketId={ticketId} reference={ticket.reference} dossierId={ticket.dossierId ?? null}
        statut={ticket.statut} canAct={canAct} />

      <DemarchesPanel key={versionDemarches} ticketId={ticketId} dossierId={ticket.dossierId ?? null} canAct={canAct && actif} />

      <NoteTicketPanel ticketId={ticketId} statut={ticket.statut} role={user?.role} />

      {(ticket.statut === 'DEROULEMENT_DEMARCHE' || ticket.statut === 'CLOTURE_DOSSIER' || ticket.statut === 'ANNULE') && (
        <RecapitulatifPanel ticketId={ticketId} />
      )}
    </div>
  );
}
