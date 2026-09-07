package ma.jurika.ticket.api;

import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.RoleHierarchyAutoConfiguration;
import ma.jurika.ticket.application.CreateTicketUseCase;
import ma.jurika.ticket.application.DossierTransferService;
import ma.jurika.ticket.application.SearchTicketsUseCase;
import ma.jurika.ticket.application.TransitionTicketUseCase;
import ma.jurika.ticket.application.UpdateTicketUseCase;
import ma.jurika.ticket.domain.model.DossierTransfertRequest;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;
import ma.jurika.ticket.domain.model.TransfertStatut;
import ma.jurika.ticket.domain.port.CommentRepository;
import ma.jurika.ticket.domain.port.DossierRepository;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifie le verrou "SUPERVISEUR = oversight only" : la hierarchie des roles
 * (SUPERVISEUR &gt; EMPLOYE) autoriserait sinon le superviseur sur les ecritures.
 * Le denial explicite {@code hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')}
 * renvoie 403 au superviseur (et au super_admin), tandis que l'employe reste
 * autorise (non-regression).
 */
@WebMvcTest(controllers = {TicketController.class, DossierTransferController.class})
@Import({RoleHierarchyAutoConfiguration.class, TicketWriteSecurityTest.MethodSecurity.class})
class TicketWriteSecurityTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {}

    @Autowired MockMvc mvc;

    @MockBean CreateTicketUseCase createTicketUseCase;
    @MockBean UpdateTicketUseCase updateTicketUseCase;
    @MockBean TransitionTicketUseCase transitionTicketUseCase;
    @MockBean SearchTicketsUseCase searchTicketsUseCase;
    @MockBean TicketRepository ticketRepository;
    @MockBean DossierRepository dossierRepository;
    @MockBean CommentRepository commentRepository;
    @MockBean DossierTransferService dossierTransferService;
    @MockBean ma.jurika.ticket.infrastructure.persistence.UserViewJpaRepository userDirectory;
    @MockBean ma.jurika.ticket.infrastructure.persistence.DossierJpaRepository dossierDirectory;

    private static RequestPostProcessor as(Role role) {
        AuthenticatedUser principal = new AuthenticatedUser(
                UUID.randomUUID(), UUID.randomUUID(), "actor@jurika.ma", role);
        var auth = new UsernamePasswordAuthenticationToken(
                principal, "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(auth);
    }

    private Ticket sampleTicket() {
        return new Ticket(UUID.randomUUID(), UUID.randomUUID(), "T-2026-00001", "Ticket",
                TicketType.MODIFICATION, TicketStatut.CREATION_TICKET, TicketPriorite.NORMALE,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, null, null, null, null, Instant.now());
    }

    private DossierTransfertRequest sampleRequest() {
        return new DossierTransfertRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), TransfertStatut.EN_ATTENTE, false,
                "motif", Instant.now(), null);
    }

    // ---------------------------------------------------------------- create

    @Test
    void create_superviseur_estRefuse_403() throws Exception {
        mvc.perform(post("/api/v1/tickets").with(as(Role.SUPERVISEUR)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titre\":\"T\",\"type\":\"MODIFICATION\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_superAdmin_estRefuse_403() throws Exception {
        mvc.perform(post("/api/v1/tickets").with(as(Role.SUPER_ADMIN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titre\":\"T\",\"type\":\"MODIFICATION\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_employe_estAutorise_201() throws Exception {
        when(createTicketUseCase.execute(any())).thenReturn(sampleTicket());
        mvc.perform(post("/api/v1/tickets").with(as(Role.EMPLOYE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titre\":\"T\",\"type\":\"MODIFICATION\"}"))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------ transition

    @Test
    void transition_superviseur_estRefuse_403() throws Exception {
        mvc.perform(post("/api/v1/tickets/{id}/transition", UUID.randomUUID())
                        .with(as(Role.SUPERVISEUR)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"GENERATION_DOCUMENTS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void transition_employe_estAutorise_200() throws Exception {
        when(transitionTicketUseCase.execute(any())).thenReturn(sampleTicket());
        mvc.perform(post("/api/v1/tickets/{id}/transition", UUID.randomUUID())
                        .with(as(Role.EMPLOYE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"GENERATION_DOCUMENTS\"}"))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------- transfer

    @Test
    void transferRequest_superviseur_estRefuse_403() throws Exception {
        mvc.perform(post("/api/v1/dossiers/{id}/transfer-requests", UUID.randomUUID())
                        .with(as(Role.SUPERVISEUR)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void transferRequest_employe_estAutorise_201() throws Exception {
        when(dossierTransferService.requestTransfer(any(), any(), any(), any(), any()))
                .thenReturn(sampleRequest());
        mvc.perform(post("/api/v1/dossiers/{id}/transfer-requests", UUID.randomUUID())
                        .with(as(Role.EMPLOYE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void update_superviseur_estRefuse_403() throws Exception {
        mvc.perform(patch("/api/v1/tickets/{id}", UUID.randomUUID())
                        .with(as(Role.SUPERVISEUR)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titre\":\"T\"}"))
                .andExpect(status().isForbidden());
    }

    // ---- VOIE B (transfert direct superviseur) retiree : l'endpoint n'existe plus

    @Test
    void directTransfer_endpointRetire_404() throws Exception {
        mvc.perform(post("/api/v1/dossiers/{id}/transfer", UUID.randomUUID())
                        .with(as(Role.SUPERVISEUR)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }
}
