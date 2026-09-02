package fr.maghreb.gje.repositories;
import fr.maghreb.gje.models.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface TicketRepository
    extends JpaRepository<Ticket, UUID> {
    List<Ticket> findByDossierId(UUID dossierId);
    List<Ticket> findByWorkspaceIdAndAssignedTo(
        UUID workspaceId, UUID userId);
    List<Ticket> findByWorkspaceId(UUID workspaceId);
}
