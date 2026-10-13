package ma.jurika.ticket.domain.state;

import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.TransitionChecks;

import java.util.List;

/**
 * Socle commun aux quatre statuts du parcours nominal.
 *
 * <p>Chaque statut du parcours est atteignable de deux facons seulement :
 * <ul>
 *   <li>depuis son PREDECESSEUR, sous reserve des points de controle du guide
 *       (onglet 2), evalues par {@link TransitionChecks} ;</li>
 *   <li>depuis ANNULE, en REPRISE, avec motif obligatoire.</li>
 * </ul>
 * Toute autre provenance est refusee.
 */
abstract class ParcoursHandler implements TicketStatutHandler {

    private final TransitionChecks checks;

    protected ParcoursHandler(TransitionChecks checks) {
        this.checks = checks;
    }

    /** Statut dont on peut venir dans le parcours nominal ; null pour le premier. */
    protected abstract TicketStatut predecesseur();

    @Override
    public void validateTransition(Ticket current, TransitionContext ctx) {
        TicketStatut source = current.statut();

        if (source == TicketStatut.ANNULE) {
            // Reprise d'un ticket annule : transition sensible, motif obligatoire.
            if (ctx.comment() == null || ctx.comment().trim().isEmpty()) {
                throw new ValidationException("Motif obligatoire pour reprendre un ticket annul\u00e9");
            }
            return;
        }
        if (predecesseur() == null || source != predecesseur()) {
            throw new ConflictException("Seul un ticket au statut \u00ab "
                    + (predecesseur() == null ? TicketStatut.ANNULE : predecesseur()).libelle()
                    + " \u00bb peut passer au statut \u00ab " + statut().libelle() + " \u00bb (statut actuel : \u00ab "
                    + source.libelle() + " \u00bb).");
        }
        List<String> obstacles = checks.obstacles(current, statut());
        if (!obstacles.isEmpty()) {
            throw new ConflictException("Passage au statut \u00ab " + statut().libelle() + " \u00bb impossible : "
                    + String.join(" | ", obstacles));
        }
    }
}
