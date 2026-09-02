package ma.jurika.ticket.domain.port;

import ma.jurika.ticket.domain.model.Deadline;
import ma.jurika.ticket.domain.model.DeadlineRule;
import ma.jurika.ticket.domain.model.DeadlineStatut;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeadlineRepository {

    Deadline save(Deadline deadline);

    Optional<Deadline> findById(UUID workspaceId, UUID id);

    /**
     * Cherche une deadline AUTO existante pour ce (ticket, regle), pour eviter les doublons.
     */
    Optional<Deadline> findAutoByTicketAndRule(UUID workspaceId, UUID ticketId, DeadlineRule rule);

    List<Deadline> findByWorkspace(UUID workspaceId, DeadlineStatut statut, Instant from, Instant to, int limit);

    List<Deadline> findByTicket(UUID workspaceId, UUID ticketId);

    List<Deadline> findByDossier(UUID workspaceId, UUID dossierId);

    List<Deadline> findOverdue(UUID workspaceId, Instant now, int limit);

    long countOpen(UUID workspaceId);
}
