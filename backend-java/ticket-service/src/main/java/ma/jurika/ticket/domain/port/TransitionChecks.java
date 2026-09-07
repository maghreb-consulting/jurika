package ma.jurika.ticket.domain.port;

import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketStatut;

import java.util.List;

/**
 * Les « points de controle avant passage au statut suivant » de l'onglet 2 du
 * guide, evalues COTE SERVEUR.
 *
 * <p>Ce ne sont pas des libelles affiches : chaque obstacle retourne bloque
 * effectivement la transition. Retourner une liste vide vaut feu vert.
 *
 * <p>Un workflow sans referentiel charge (tous sauf CREATION a ce jour) ne
 * produit aucun obstacle : les autres workflows gardent donc exactement le
 * comportement qu'ils avaient.
 */
public interface TransitionChecks {

    /** Obstacles, en francais, a la transition {@code ticket.statut() -> cible}. */
    List<String> obstacles(Ticket ticket, TicketStatut cible);
}
