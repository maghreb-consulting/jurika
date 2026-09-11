package ma.jurika.ticket.domain.state;

import ma.jurika.common.exception.ConflictException;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.TransitionChecks;
import org.springframework.stereotype.Component;

/**
 * Statut 2 — « Generation des documents ».
 *
 * <p>Points de controle du guide (onglet 2, ligne 1) : fiche de renseignements
 * complete et signee, pieces d'identite recues, forme sociale et capital
 * arretes. Ils correspondent a la ligne 1 du parcours du 9 septembre, donc au
 * cochage integral des demarches du statut « Creation du ticket » — ce que
 * verifie {@link ParcoursHandler}.
 *
 * <p>Reste non mecanisable : « honoraires acceptes » (la facturation n'est pas
 * rattachee au ticket dans le modele actuel).
 */
@Component
public class GenerationDocumentsHandler extends ParcoursHandler {

    public GenerationDocumentsHandler(TransitionChecks checks) {
        super(checks);
    }

    @Override
    public TicketStatut statut() {
        return TicketStatut.GENERATION_DOCUMENTS;
    }

    @Override
    protected TicketStatut predecesseur() {
        return TicketStatut.CREATION_TICKET;
    }

    @Override
    public void validateTransition(Ticket current, TransitionContext ctx) {
        super.validateTransition(current, ctx);
        // Regle conservee de l'ancien EN_COURS : la production ne demarre pas sur
        // un ticket sans responsable. Le use case auto-assigne l'acteur si besoin,
        // ce garde-fou couvre les appels qui ne passeraient pas par lui.
        if (current.statut() == TicketStatut.CREATION_TICKET && current.assigneId() == null) {
            throw new ConflictException(
                    "Le ticket doit etre assigne avant de passer en generation des documents");
        }
    }
}
