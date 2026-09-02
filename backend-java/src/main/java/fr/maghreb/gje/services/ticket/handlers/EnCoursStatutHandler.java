package fr.maghreb.gje.services.ticket.handlers;

import fr.maghreb.gje.models.Notification;
import fr.maghreb.gje.models.Ticket;
import fr.maghreb.gje.models.User;
import fr.maghreb.gje.repositories.NotificationRepository;
import fr.maghreb.gje.repositories.UserRepository;
import fr.maghreb.gje.services.ticket.TicketStatutHandler;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * State: EN_COURS
 * When superviseur sends ticket back to EN_COURS from EN_REVISION, notify the employee.
 */
@Component("handler_EN_COURS")
public class EnCoursStatutHandler implements TicketStatutHandler {

    @Override
    public void handle(Ticket ticket, UUID userId, User.Role role, UUID workspaceId,
                       UserRepository userRepository, NotificationRepository notificationRepository) {
        // Clear blocked state if coming from BLOQUE
        ticket.setBlockedSince(null);
        ticket.setBlockedReason(null);

        // If supervisor sends back from EN_REVISION -> notify the assigned employee
        if (role == User.Role.SUPERVISEUR || role == User.Role.SUPER_ADMIN) {
            notificationRepository.save(Notification.builder()
                .workspaceId(workspaceId)
                .userId(ticket.getAssignedTo())
                .type("TICKET_CORRECTION")
                .message("Corrections demandées sur ticket [" + ticket.getId().toString().substring(0, 8) + "]")
                .isRead(false)
                .build());
        }
    }
}
