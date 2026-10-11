package ma.jurika.ticket.application.referentiel;

import ma.jurika.ticket.domain.model.Demarche;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.DemarcheReferentielRepository;

import java.util.List;
import java.util.Optional;

/**
 * Lot L4b (E4) : les lignes de demarches d'un ticket. En production,
 * {@link DemarchesDuTicket} (creation : lignes en service ; autres : corpus selon les
 * decisions) ; {@link #duReferentiel} garde le comportement d'avant (lignes par type).
 */
public interface LignesDuTicket {

    List<Demarche> toutes(Ticket t);

    List<Demarche> duStatut(Ticket t, TicketStatut statut);

    Optional<Demarche> parOrdre(Ticket t, int ordre);

    static LignesDuTicket duReferentiel(DemarcheReferentielRepository referentiel) {
        return new LignesDuTicket() {
            @Override
            public List<Demarche> toutes(Ticket t) {
                return referentiel.findByWorkflow(t.type().name());
            }

            @Override
            public List<Demarche> duStatut(Ticket t, TicketStatut statut) {
                return referentiel.findByWorkflowAndStatut(t.type().name(), statut);
            }

            @Override
            public Optional<Demarche> parOrdre(Ticket t, int ordre) {
                return referentiel.findByWorkflowAndOrdre(t.type().name(), ordre);
            }
        };
    }
}
