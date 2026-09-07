package ma.jurika.ticket.application;

import ma.jurika.common.audit.AuditEventEmitter;
import ma.jurika.common.notification.NotificationPublisher;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;
import ma.jurika.ticket.domain.port.TicketEventPublisher;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifie le cablage de la notification PERSISTANTE TICKET_ASSIGNED : l'assignation
 * d'un ticket a un employe (different de l'acteur) doit creer une notification pour
 * le destinataire — et JAMAIS pour l'acteur lui-meme ni sur une simple edition.
 */
@ExtendWith(MockitoExtension.class)
class UpdateTicketUseCaseNotificationTest {

    private static final UUID WS = UUID.randomUUID();
    private static final UUID TICKET = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID OLD_ASSIGNEE = UUID.randomUUID();
    private static final UUID NEW_ASSIGNEE = UUID.randomUUID();

    @Mock TicketRepository ticketRepository;
    @Mock TicketEventPublisher eventPublisher;
    @Mock AuditEventEmitter auditEmitter;
    @Mock NotificationPublisher notificationPublisher;

    private UpdateTicketUseCase useCase() {
        return new UpdateTicketUseCase(ticketRepository, eventPublisher, auditEmitter, notificationPublisher);
    }

    @Test
    void assignation_aUnAutreEmploye_creeUneNotificationTicketAssigned() {
        Ticket current = ticket(OLD_ASSIGNEE);
        when(ticketRepository.findById(WS, TICKET)).thenReturn(Optional.of(current));
        when(ticketRepository.updateAssignment(any(), any(), any(), any(), any(), any()))
                .thenReturn(ticket(NEW_ASSIGNEE));

        useCase().execute(new UpdateTicketUseCase.Command(
                WS, TICKET, "Ticket", "desc", TicketPriorite.NORMALE, NEW_ASSIGNEE, null, ACTOR));

        verify(notificationPublisher).notifyUser(eq(NEW_ASSIGNEE), eq(WS), eq("TICKET_ASSIGNED"),
                any(), any(), eq("/tickets"), anyMap());
    }

    @Test
    void autoAssignation_neNotifiePasLActeurLuiMeme() {
        Ticket current = ticket(OLD_ASSIGNEE);
        when(ticketRepository.findById(WS, TICKET)).thenReturn(Optional.of(current));
        when(ticketRepository.updateAssignment(any(), any(), any(), any(), any(), any()))
                .thenReturn(ticket(ACTOR));

        useCase().execute(new UpdateTicketUseCase.Command(
                WS, TICKET, "Ticket", "desc", TicketPriorite.NORMALE, ACTOR, null, ACTOR));

        verify(notificationPublisher, never())
                .notifyUser(any(), any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    void editionSansChangementDAssigne_neNotifiePas() {
        Ticket current = ticket(OLD_ASSIGNEE);
        when(ticketRepository.findById(WS, TICKET)).thenReturn(Optional.of(current));
        when(ticketRepository.updateAssignment(any(), any(), any(), any(), any(), any()))
                .thenReturn(ticket(OLD_ASSIGNEE));

        // Meme assigne (OLD_ASSIGNEE) -> seul le titre change.
        useCase().execute(new UpdateTicketUseCase.Command(
                WS, TICKET, "Nouveau titre", "desc", TicketPriorite.NORMALE, OLD_ASSIGNEE, null, ACTOR));

        verify(notificationPublisher, never())
                .notifyUser(any(), any(), any(), any(), any(), any(), anyMap());
    }

    private Ticket ticket(UUID assigne) {
        return new Ticket(TICKET, WS, "T-2026-00042", "Ticket", TicketType.MODIFICATION,
                TicketStatut.CREATION_TICKET, TicketPriorite.NORMALE, null, assigne, ACTOR, null, null, null, null,
                null, Instant.now());
    }
}
