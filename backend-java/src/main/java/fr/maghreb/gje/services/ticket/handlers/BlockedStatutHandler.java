package fr.maghreb.gje.services.ticket.handlers;

import fr.maghreb.gje.models.Ticket;
import fr.maghreb.gje.models.User;
import fr.maghreb.gje.models.Notification;
import fr.maghreb.gje.repositories.NotificationRepository;
import fr.maghreb.gje.repositories.UserRepository;
import fr.maghreb.gje.services.ticket.TicketStatutHandler;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

/**
 * State: BLOQUE
 * When a ticket enters the BLOCKED state, supervisors are notified.
 */
@Component("handler_BLOQUE")
public class BlockedStatutHandler implements TicketStatutHandler {

    @Override
    public void handle(Ticket ticket, UUID userId, User.Role role, UUID workspaceId,
                       UserRepository userRepository, NotificationRepository notificationRepository) {
        ticket.setBlockedSince(LocalDateTime.now());

        List<User> superviseurs = userRepository.findAllByWorkspaceId(workspaceId)
            .stream()
            .filter(u -> u.getRole() == User.Role.SUPERVISEUR)
            .toList();

        for (User sup : superviseurs) {
            notificationRepository.save(Notification.builder()
                .workspaceId(workspaceId)
                .userId(sup.getId())
                .type("TICKET_BLOQUE")
                .message("Ticket #" + ticket.getId().toString().substring(0, 8)
                    + " bloqué : " + ticket.getBlockedReason())
                .isRead(false)
                .build());
        }
    }
}
