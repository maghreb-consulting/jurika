package ma.jurika.ticket.domain.port;

import ma.jurika.ticket.domain.model.Debours;
import ma.jurika.ticket.domain.model.DeboursSaisie;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeboursRepository {

    Optional<Debours> findById(UUID workspaceId, UUID id);

    /** Lignes du ticket, dans un ordre stable (date d'engagement, creation, identifiant). */
    List<Debours> findByTicket(UUID workspaceId, UUID ticketId);

    Debours create(UUID workspaceId, UUID ticketId, DeboursSaisie saisie, UUID createdById);

    /** Remplace la saisie de la ligne (piece comprise : null la retire). */
    Debours update(UUID workspaceId, UUID id, DeboursSaisie saisie);

    void delete(UUID workspaceId, UUID id);
}
