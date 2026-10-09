package ma.jurika.ticket.domain.port;

import ma.jurika.ticket.domain.model.TicketNote;

import java.util.Optional;
import java.util.UUID;

/** Lot L1 : table {@code ticket_notes} (V29). */
public interface TicketNoteRepository {

    Optional<TicketNote> find(UUID workspaceId, UUID ticketId);

    TicketNote save(UUID workspaceId, UUID ticketId, String contenu, UUID auteurId);
}
