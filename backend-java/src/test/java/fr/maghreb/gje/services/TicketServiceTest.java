package fr.maghreb.gje.services;

import fr.maghreb.gje.dto.ticket.TicketUpdateStatutRequest;
import fr.maghreb.gje.models.*;
import fr.maghreb.gje.models.Ticket.TicketStatut;
import fr.maghreb.gje.repositories.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private HistoriqueRepository historiqueRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private TicketService ticketService;

    private UUID workspaceId;
    private UUID userId;
    private UUID ticketId;
    private Ticket mockTicket;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        userId = UUID.randomUUID();
        ticketId = UUID.randomUUID();

        mockTicket = Ticket.builder()
                .id(ticketId)
                .workspaceId(workspaceId)
                .dossierId(UUID.randomUUID())
                .auteurId(UUID.randomUUID())
                .assignedTo(userId)
                .statut(TicketStatut.OUVERT)
                .build();

        // Inject private EntityManager field
        ReflectionTestUtils.setField(ticketService, "entityManager", entityManager);

        // Support for setTenant
        Query mockQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);
    }

    @Test
    void testEmployee_OuvertToEnCours_Allowed() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(mockTicket));
        TicketUpdateStatutRequest req = new TicketUpdateStatutRequest();
        req.setStatut("EN_COURS");

        Ticket result = ticketService.updateStatut(ticketId, req, userId, workspaceId, User.Role.EMPLOYE);
        assertEquals(TicketStatut.EN_COURS, result.getStatut());
    }

    @Test
    void testEmployee_EnRevisionToTermine_Allowed() {
        mockTicket.setStatut(TicketStatut.EN_REVISION);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(mockTicket));
        TicketUpdateStatutRequest req = new TicketUpdateStatutRequest();
        req.setStatut("TERMINE");

        Ticket result = ticketService.updateStatut(ticketId, req, userId, workspaceId, User.Role.EMPLOYE);
        assertEquals(TicketStatut.TERMINE, result.getStatut());
    }

    @Test
    void testEmployee_EnRevisionToEnCours_Forbidden() {
        mockTicket.setStatut(TicketStatut.EN_REVISION);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(mockTicket));
        TicketUpdateStatutRequest req = new TicketUpdateStatutRequest();
        req.setStatut("EN_COURS");

        assertThrows(RuntimeException.class, () -> 
            ticketService.updateStatut(ticketId, req, userId, workspaceId, User.Role.EMPLOYE));
    }

    @Test
    void testEmployee_BloqueToTermine_Forbidden() {
        mockTicket.setStatut(TicketStatut.BLOQUE);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(mockTicket));
        TicketUpdateStatutRequest req = new TicketUpdateStatutRequest();
        req.setStatut("TERMINE");

        assertThrows(RuntimeException.class, () -> 
            ticketService.updateStatut(ticketId, req, userId, workspaceId, User.Role.EMPLOYE));
    }

    @Test
    void testSupervisor_EnRevisionToEnCours_Allowed_AndNotified() {
        mockTicket.setStatut(TicketStatut.EN_REVISION);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(mockTicket));
        TicketUpdateStatutRequest req = new TicketUpdateStatutRequest();
        req.setStatut("EN_COURS");

        Ticket result = ticketService.updateStatut(ticketId, req, userId, workspaceId, User.Role.SUPERVISEUR);
        assertEquals(TicketStatut.EN_COURS, result.getStatut());
        
        // Verify notification to assigned employee
        verify(notificationRepository).save(argThat(n -> 
            n.getType().equals("TICKET_CORRECTION") && n.getUserId().equals(mockTicket.getAssignedTo())
        ));
    }

    @Test
    void testSupervisor_BloqueToTermine_Allowed() {
        mockTicket.setStatut(TicketStatut.BLOQUE);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(mockTicket));
        TicketUpdateStatutRequest req = new TicketUpdateStatutRequest();
        req.setStatut("TERMINE");

        Ticket result = ticketService.updateStatut(ticketId, req, userId, workspaceId, User.Role.SUPERVISEUR);
        assertEquals(TicketStatut.TERMINE, result.getStatut());
    }

    @Test
    void testNotificationToSupervisor_WhenReviewSubmitted() {
        mockTicket.setStatut(TicketStatut.EN_COURS);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(mockTicket));
        
        User supervisor = new User();
        supervisor.setId(UUID.randomUUID());
        supervisor.setRole(User.Role.SUPERVISEUR);
        when(userRepository.findAllByWorkspaceId(workspaceId)).thenReturn(Collections.singletonList(supervisor));
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User())); // For employeNom

        TicketUpdateStatutRequest req = new TicketUpdateStatutRequest();
        req.setStatut("EN_REVISION");

        ticketService.updateStatut(ticketId, req, userId, workspaceId, User.Role.EMPLOYE);

        // Verify supervisor notified
        verify(notificationRepository).save(argThat(n -> 
            n.getType().equals("TICKET_REVISION") && n.getUserId().equals(supervisor.getId())
        ));
    }
}
