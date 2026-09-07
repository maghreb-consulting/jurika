package ma.jurika.ticket.domain.state;

import ma.jurika.common.exception.ValidationException;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketStatut;
import org.springframework.stereotype.Component;

/**
 * Statut 5 — « Ticket annule ».
 *
 * <p>Ce n'est pas une etape finale du parcours mais une SORTIE LATERALE :
 * accessible depuis n'importe quel statut, y compris « Cloture de dossier »
 * (guide, onglet 2, ligne 5 : « Peut intervenir depuis n'importe quel statut »).
 *
 * <p>Aucun point de controle de parcours ne s'y applique : on n'exige pas d'un
 * dossier abandonne qu'il ait accompli ses demarches. Le MOTIF, lui, est
 * obligatoire — c'est le seul livrable exige par le guide a ce stade
 * (« Motif documente »).
 *
 * <p>La double-annulation (ANNULE -> ANNULE) est traitee en amont : no-op
 * idempotent dans le use case, et « transition invalide » dans la machine a
 * etats.
 */
@Component
public class AnnuleHandler implements TicketStatutHandler {

    private static final int MIN_COMMENT_LENGTH = 10;

    @Override
    public TicketStatut statut() {
        return TicketStatut.ANNULE;
    }

    @Override
    public void validateTransition(Ticket current, TransitionContext ctx) {
        if (ctx.comment() == null || ctx.comment().trim().length() < MIN_COMMENT_LENGTH) {
            throw new ValidationException(
                    "Commentaire d'annulation obligatoire (min " + MIN_COMMENT_LENGTH + " caracteres)");
        }
    }
}
