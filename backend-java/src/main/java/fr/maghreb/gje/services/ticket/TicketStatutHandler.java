package fr.maghreb.gje.services.ticket;

import fr.maghreb.gje.models.Ticket;
import fr.maghreb.gje.models.User;
import fr.maghreb.gje.repositories.NotificationRepository;
import fr.maghreb.gje.repositories.UserRepository;

import java.util.UUID;

/**
 * State Pattern - OCP Compliant.
 * Each ticket status has its own handler class.
 * Adding a new status = creating a new class. ZERO modification of existing code.
 */
public interface TicketStatutHandler {

    /**
     * Validates and applies the side effects of transitioning TO this status.
     * @param ticket the ticket being modified
     * @param userId the user performing the action
     * @param role   the role of the user performing the action
     * @param workspaceId the workspace context
     * @param userRepository for notifications
     * @param notificationRepository for creating notifications
     */
    void handle(Ticket ticket,
                UUID userId,
                User.Role role,
                UUID workspaceId,
                UserRepository userRepository,
                NotificationRepository notificationRepository);
}
