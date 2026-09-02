package fr.maghreb.gje.services.ticket.handlers;

import fr.maghreb.gje.models.Ticket;
import fr.maghreb.gje.models.User;
import fr.maghreb.gje.repositories.NotificationRepository;
import fr.maghreb.gje.repositories.UserRepository;
import fr.maghreb.gje.services.ticket.TicketStatutHandler;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * State: TERMINE
 * When a ticket is closed, record the closing timestamp. No notification needed.
 */
@Component("handler_TERMINE")
public class TermineStatutHandler implements TicketStatutHandler {

    @Override
    public void handle(Ticket ticket, UUID userId, User.Role role, UUID workspaceId,
                       UserRepository userRepository, NotificationRepository notificationRepository) {
        ticket.setClosedAt(LocalDateTime.now());
        ticket.setBlockedSince(null);
    }
}
