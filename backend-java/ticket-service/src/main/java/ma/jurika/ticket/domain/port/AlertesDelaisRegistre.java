package ma.jurika.ticket.domain.port;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lot L5 (E9, RG-COM-08) : registre des echeances de demarches deja notifiees
 * (table {@code alertes_delais_notifiees}, ticket V43).
 */
public interface AlertesDelaisRegistre {

    /** Un ticket a surveiller, dans son cabinet. */
    record TicketASurveiller(UUID workspaceId, UUID ticketId) {}

    /**
     * Tickets en cours de tous les cabinets (seul chemin hors workspace : la
     * detection planifiee n'a pas d'utilisateur ; chaque ticket est ensuite
     * traite dans son cabinet).
     */
    List<TicketASurveiller> ticketsASurveiller();

    /**
     * Retient qu'un palier d'echeance a ete notifie. Renvoie {@code true} la
     * premiere fois seulement (aucune notification en double). Le workspace
     * courant ({@code TenantContext}) doit etre celui du ticket.
     */
    boolean retenirSiNouveau(UUID workspaceId, UUID ticketId, int ordre,
                             LocalDate echeance, String severite);
}
