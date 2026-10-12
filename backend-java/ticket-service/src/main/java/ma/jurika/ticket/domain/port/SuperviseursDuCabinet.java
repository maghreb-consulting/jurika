package ma.jurika.ticket.domain.port;

import java.util.List;
import java.util.UUID;

/**
 * Lot L5 (E9, RG-COM-08) : superviseurs actifs d'un cabinet, destinataires de
 * certaines notifications (delai depasse). Lecture limitee au cabinet demande.
 */
public interface SuperviseursDuCabinet {

    List<UUID> actifs(UUID workspaceId);
}
