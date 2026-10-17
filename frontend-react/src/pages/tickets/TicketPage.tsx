import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { ArrowRight, CircleCheck, Database, FileText } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { EmptyState } from '../../components/ui/EmptyState';
import { Lien } from '../../components/ui/Lien';
import { Message } from '../../components/ui/Message';
import { SquelettePage } from '../../components/ui/LoadingSkeleton';
import { Page } from '../../components/layout/Page';
import { StatutBadge } from './TableView';
import { LIBELLES_DONNEES } from './libellesDonnees';
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
import { AideStatutTicket } from './AideStatutTicket';
import { profilTicket } from './textesAideStatut';

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

  /** Lot L6b (E14) : obstacles relus apres chaque changement des demarches. */
  const relirePassage = useCallback(() => {
    ticketService.passage(ticketId).then(setPassage).catch(() => undefined);
  }, [ticketId]);

  const canAct = user?.role === 'EMPLOYE' && acces?.peutAgir === true;
  const actif = ticket ? ticket.statut !== 'CLOTURE_DOSSIER' && ticket.statut !== 'ANNULE' : false;

  async function passer() {
    if (!passage?.suivant) return;
    setRefus(null);
    setEnCours(true);
    try {
      await ticketService.transition(ticketId, { target: passage.suivant as TicketStatut });
      await charger();
      // Lot L6b (E14) : les demarches du nouveau statut deviennent actionnables ; le panneau
      // se recharge (sans cela, aucune n'offrait "Cocher" avant le rechargement de la page).
      setVersionDemarches((v) => v + 1);
    } catch (e) {
      // RG-TKT-06 : le refus du serveur s'affiche tel quel, avec sa raison.
      setRefus(extractError(e).message);
      ticketService.passage(ticketId).then(setPassage).catch(() => undefined);
    } finally {
      setEnCours(false);
    }
  }

  if (erreur) {
    return (
      <Page titre="Ticket introuvable" fil={[{ libelle: 'Tickets', to: '/tickets' }, { libelle: 'Ticket' }]}>
        <Message ton="erreur" titre="Le ticket ne peut pas être affiché" action={<Lien to="/tickets">Revenir à la liste des tickets</Lien>}>
          {erreur}
        </Message>
      </Page>
    );
  }
  if (!ticket) return <SquelettePage />;

  const docs: DocumentSummary[] = (documents?.dossier?.groupes ?? []).flatMap((g) => g.documents);

  return (
    <Page
      titre={ticket.titre}
      etat={<StatutBadge s={ticket.statut} />}
      fil={[{ libelle: 'Tickets', to: '/tickets' }, { libelle: ticket.reference }]}
      description={
        <>
          <span className="font-mono">{ticket.reference}</span> — {TICKET_TYPE_LABELS[ticket.type]}. Où en est
          l’opération, ce qu’il reste à faire et tout ce que le ticket contient.
        </>
      }
      actions={
        canAct && actif ? (
          <Lien to={`/workflows/${ticket.id}`} commeBouton={{ variant: 'secondary' }}>
            Ouvrir le parcours (saisie et génération des actes)
          </Lien>
        ) : undefined
      }
      aide={
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
      }
    >
      <div className="space-y-5" data-testid="page-ticket">
        {acces && !canAct && (
          <Message ton="info">
            <span data-testid="acces-raison">
              {acces.raison ?? 'Consultation seule — actions réservées à l’employé responsable du dossier.'}
            </span>
          </Message>
        )}

        <AideStatutTicket statut={ticket.statut} profil={profilTicket(user?.role, acces?.enCharge === true)}
          obstacles={passage ? passage.obstacles : null} renvoi />

        {/* Passage de statut : obstacles nommes avant la demande, refus avec sa raison (RG-TKT-06). */}
        <Card
          titleAs="h2"
          title={
            <span className="flex items-center gap-1">
              Passage de statut
              <InfoBulle
                libelle="Comment le ticket avance-t-il ?"
                texte="Le ticket suit quatre statuts : création, génération des documents, déroulement de la démarche, clôture. Pour passer au suivant, les démarches obligatoires du statut en cours doivent être cochées et les données attendues saisies. Ce qui manque est listé ici avant toute demande. Seul l’employé responsable du dossier fait passer le ticket au statut suivant."
              />
            </span>
          }
        >
          <div className="space-y-3" data-testid="passage-statut">
            {passage?.suivant ? (
              <>
                <p className="text-sm text-fg">
                  Statut suivant : <strong>{libelleStatut(passage.suivant)}</strong>
                </p>
                {passage.obstacles.length > 0 ? (
                  <div data-testid="obstacles">
                    <Message ton="attention" titre="Le passage est impossible tant que ceci n’est pas réglé :">
                      <ul className="ml-5 list-disc">
                        {passage.obstacles.map((o) => <li key={o}>{o}</li>)}
                      </ul>
                    </Message>
                  </div>
                ) : (
                  <p className="flex items-center gap-1.5 text-sm font-medium text-success" data-testid="passage-possible">
                    <CircleCheck className="h-4 w-4" aria-hidden="true" /> Rien ne s’oppose au passage.
                  </p>
                )}
                {canAct && (
                  <Button type="button" onClick={passer} loading={enCours} disabled={enCours || passage.obstacles.length > 0}
                    iconEnd={<ArrowRight />}>
                    Passer à « {libelleStatut(passage.suivant)} »
                  </Button>
                )}
              </>
            ) : (
              <p className="text-sm text-fg-muted">
                {ticket.statut === 'ANNULE'
                  ? 'Ticket annulé : il ne reprend qu’à son statut antérieur, depuis la liste des tickets.'
                  : 'Ticket clos : il n’y a plus de statut suivant.'}
              </p>
            )}
            {refus && (
              <div data-testid="refus-transition">
                <Message ton="erreur" onFermer={() => setRefus(null)}>
                  Passage refusé : {refus}
                </Message>
              </div>
            )}
          </div>
        </Card>

        {ticket.type !== 'CREATION' && (
          <DecisionsTicketPanel ticketId={ticketId} canAct={canAct && actif}
            onChanged={() => { setVersionDemarches((v) => v + 1); void ticketService.passage(ticketId).then(setPassage); }} />
        )}

        <section className="space-y-2" data-testid="saisies-ticket" aria-labelledby="titre-saisies">
          <h2 id="titre-saisies" className="text-base font-semibold text-heading">Données saisies</h2>
          {variables.filter((v) => !v.boucle && v.valeur).length === 0 ? (
            <Card padding={false}>
              <EmptyState compact icon={<Database />} title="Aucune donnée saisie pour ce ticket"
                description="Elles apparaissent ici au fil du parcours, avec leur provenance." />
            </Card>
          ) : (
            <ProvenanceDonnees variables={variables} libelles={LIBELLES_DONNEES} />
          )}
        </section>

        <Card
          titleAs="h2"
          title={
            <span className="flex items-center gap-1">
              Documents du ticket
              <InfoBulle
                libelle="Quels documents figurent ici ?"
                texte="Les actes générés et les pièces déposées pour ce ticket, rangés dans la Data Room du dossier. Chaque document se lit par son type, la société et sa version."
              />
            </span>
          }
        >
          <div data-testid="documents-ticket">
            {docs.length === 0 ? (
              <p className="text-sm text-fg-muted">Aucun document pour ce ticket pour l’instant.</p>
            ) : (
              <ul className="divide-y divide-border text-sm">
                {docs.map((d) => (
                  <li key={d.id} className="flex items-start gap-2 py-2 text-fg">
                    <FileText className="mt-0.5 h-4 w-4 shrink-0 text-fg-muted" aria-hidden="true" />
                    <span className="min-w-0">
                      {referenceDocument((DOCUMENT_TYPE_LABELS as Record<string, string>)[String(d.documentType)] ?? String(d.documentType),
                        documents?.societe, d.version)}
                      <span className="text-fg-muted"> — {d.title}</span>
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </Card>

        <DeboursPanel ticketId={ticketId} reference={ticket.reference} dossierId={ticket.dossierId ?? null}
          statut={ticket.statut} canAct={canAct} />

        <DemarchesPanel key={versionDemarches} ticketId={ticketId} dossierId={ticket.dossierId ?? null} canAct={canAct && actif}
          onModifie={relirePassage} />

        <NoteTicketPanel ticketId={ticketId} statut={ticket.statut} role={user?.role} />

        {(ticket.statut === 'DEROULEMENT_DEMARCHE' || ticket.statut === 'CLOTURE_DOSSIER' || ticket.statut === 'ANNULE') && (
          <RecapitulatifPanel ticketId={ticketId} />
        )}
      </div>
    </Page>
  );
}
