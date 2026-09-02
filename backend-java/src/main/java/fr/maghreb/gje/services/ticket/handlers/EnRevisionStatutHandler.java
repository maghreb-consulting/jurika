package fr.maghreb.gje.services.ticket.handlers;

import fr.maghreb.gje.models.Notification;
import fr.maghreb.gje.models.Ticket;
import fr.maghreb.gje.models.User;
import fr.maghreb.gje.repositories.NotificationRepository;
import fr.maghreb.gje.repositories.UserRepository;
import fr.maghreb.gje.services.ticket.TicketStatutHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * State: EN_REVISION
 * When an employee submits a ticket for review, supervisors are notified.
 */
@Component("handler_EN_REVISION")
public class EnRevisionStatutHandler implements TicketStatutHandler {

    @Override
    public void handle(Ticket ticket, UUID userId, User.Role role, UUID workspaceId,
                       UserRepository userRepository, NotificationRepository notificationRepository) {

        if (role == User.Role.EMPLOYE) {
            String employeNom = userRepository.findById(userId)
                .map(User::getFullName)
                .orElse("Un employé");

            List<User> superviseurs = userRepository.findAllByWorkspaceId(workspaceId)
                .stream()
                .filter(u -> u.getRole() == User.Role.SUPERVISEUR)
                .toList();

            for (User sup : superviseurs) {
                notificationRepository.save(Notification.builder()
                    .workspaceId(workspaceId)
                    .userId(sup.getId())
                    .type("TICKET_REVISION")
                    .message("Ticket [" + ticket.getId().toString().substring(0, 8)
                        + "] soumis pour révision par " + employeNom)
                    .isRead(false)
                    .build());
            }
        }
    }
}
