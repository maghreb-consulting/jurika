package ma.jurika.workflow.api;

import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.RoleHierarchyAutoConfiguration;
import ma.jurika.workflow.application.DossierIdentityQueryService;
import ma.jurika.workflow.application.MagasinVariables;
import ma.jurika.workflow.application.WorkflowUseCases;
import ma.jurika.workflow.domain.model.WorkflowProgress;
import ma.jurika.workflow.domain.model.WorkflowStatut;
import ma.jurika.workflow.domain.model.WorkflowType;
import ma.jurika.workflow.domain.strategy.StepResult;
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
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifie le verrou "SUPERVISEUR = oversight only" cote workflow : aucune
 * progression (start / save / execute-step / pieces) n'est permise au superviseur
 * malgre la hierarchie des roles. L'employe reste autorise (non-regression).
 */
@WebMvcTest(controllers = WorkflowController.class)
@Import({RoleHierarchyAutoConfiguration.class, WorkflowWriteSecurityTest.MethodSecurity.class})
class WorkflowWriteSecurityTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {}

    @Autowired MockMvc mvc;

    @MockBean WorkflowUseCases useCases;
    @MockBean DossierIdentityQueryService dossierIdentity;
    /** Lot C — le controleur lit le magasin de variables du dossier. */
    @MockBean MagasinVariables magasin;
    @MockBean ma.jurika.workflow.application.DonneesAttenduesService donneesAttendues; // lot L3
    @MockBean ma.jurika.workflow.application.ClausesLibresService clausesLibres; // lot L3
    @MockBean ma.jurika.workflow.application.EmployeEnCharge employeEnCharge; // lot L3

    private static RequestPostProcessor as(Role role) {
        AuthenticatedUser principal = new AuthenticatedUser(
                UUID.randomUUID(), UUID.randomUUID(), "actor@jurika.ma", role);
        var auth = new UsernamePasswordAuthenticationToken(
                principal, "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(auth);
    }

    private WorkflowProgress sampleProgress() {
        return new WorkflowProgress(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                WorkflowType.CREATION, 1, 9, Map.of(), WorkflowStatut.EN_COURS,
                UUID.randomUUID(), null, Instant.now());
    }

    // ------------------------------------------------------------------ start

    @Test
    void start_superviseur_estRefuse_403() throws Exception {
        mvc.perform(post("/api/v1/workflows/{id}/start", UUID.randomUUID())
                        .with(as(Role.SUPERVISEUR)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CREATION\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void start_superAdmin_estRefuse_403() throws Exception {
        mvc.perform(post("/api/v1/workflows/{id}/start", UUID.randomUUID())
                        .with(as(Role.SUPER_ADMIN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CREATION\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void start_employe_estAutorise_201() throws Exception {
        when(useCases.startOrResume(any(), any(), any(), any())).thenReturn(sampleProgress());
        mvc.perform(post("/api/v1/workflows/{id}/start", UUID.randomUUID())
                        .with(as(Role.EMPLOYE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CREATION\"}"))
                .andExpect(status().isCreated());
    }

    // ----------------------------------------------------------- execute-step

    @Test
    void executeStep_superviseur_estRefuse_403() throws Exception {
        mvc.perform(post("/api/v1/workflows/{id}/execute-step", UUID.randomUUID())
                        .with(as(Role.SUPERVISEUR)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"step\":1,\"payload\":{}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void executeStep_employe_estAutorise_200() throws Exception {
        when(useCases.executeStep(any(), any(), anyInt(), any(), any()))
                .thenReturn(new WorkflowUseCases.StepExecutionResult(
                        sampleProgress(), StepResult.ok(Map.of())));
        mvc.perform(post("/api/v1/workflows/{id}/execute-step", UUID.randomUUID())
                        .with(as(Role.EMPLOYE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"step\":1,\"payload\":{}}"))
                .andExpect(status().isOk());
    }
}
