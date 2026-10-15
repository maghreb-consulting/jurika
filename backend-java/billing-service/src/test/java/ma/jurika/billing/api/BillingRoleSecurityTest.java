package ma.jurika.billing.api;

import ma.jurika.billing.application.AdministrationFacturationService;
import ma.jurika.billing.application.ContactSalesUseCase;
import ma.jurika.billing.application.abonnement.AbonnementService;
import ma.jurika.billing.application.facture.FactureQueryService;
import ma.jurika.billing.application.paiement.PaiementService;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.RoleHierarchyAutoConfiguration;
import ma.jurika.common.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lot L7 (E5) : droits des points d'entree de la facturation (P6, controle cote serveur).
 * Lecture de l'abonnement : superviseur et employe ; changement de forfait, paiement, factures :
 * superviseur ; enregistrement des paiements hors ligne, factures de tous les cabinets, Sur Devis :
 * super-admin seul (RG-PAY-02). Le corps est simule : "pas 403" suffit a prouver l'autorisation.
 */
@WebMvcTest(controllers = {BillingController.class, AdminBillingController.class})
@Import({RoleHierarchyAutoConfiguration.class, BillingRoleSecurityTest.MethodSecurity.class})
class BillingRoleSecurityTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {}

    @Autowired MockMvc mvc;

    @MockBean AbonnementService abonnement;
    @MockBean PaiementService paiements;
    @MockBean FactureQueryService factures;
    @MockBean ContactSalesUseCase contactSales;
    @MockBean AdministrationFacturationService admin;

    @BeforeEach
    void setTenant() {
        TenantContext.set(UUID.randomUUID());
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static RequestPostProcessor as(Role role) {
        AuthenticatedUser principal = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "actor@jurika.ma", role);
        var auth = new UsernamePasswordAuthenticationToken(principal, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(auth);
    }

    private static ResultMatcher notForbidden() {
        return result -> assertNotEquals(403, result.getResponse().getStatus(), "l'autorisation doit etre accordee");
    }

    @Test
    void abonnement_lisible_par_l_employe() throws Exception {
        mvc.perform(get("/api/v1/billing/abonnement").with(as(Role.EMPLOYE))).andExpect(notForbidden());
    }

    @Test
    void changement_de_forfait_reserve_au_superviseur() throws Exception {
        mvc.perform(post("/api/v1/billing/changement-forfait").with(as(Role.EMPLOYE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/billing/changement-forfait").with(as(Role.SUPERVISEUR)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(notForbidden());
    }

    @Test
    void paiement_et_factures_reserves_au_superviseur() throws Exception {
        mvc.perform(post("/api/v1/billing/paiements").with(as(Role.EMPLOYE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/billing/factures").with(as(Role.EMPLOYE))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/billing/factures").with(as(Role.SUPERVISEUR))).andExpect(notForbidden());
        mvc.perform(get("/api/v1/billing/paiements").with(as(Role.SUPERVISEUR))).andExpect(notForbidden());
    }

    /** RG-PAY-02 : le cabinet ne valide jamais son propre paiement hors ligne. */
    @Test
    void enregistrement_des_paiements_reserve_au_super_admin() throws Exception {
        mvc.perform(post("/api/v1/billing/admin/paiements/{id}/valider", 1L).with(as(Role.SUPERVISEUR)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/billing/admin/paiements").with(as(Role.SUPERVISEUR)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/billing/admin/factures").with(as(Role.SUPERVISEUR))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/billing/admin/paiements/{id}/valider", 1L).with(as(Role.SUPER_ADMIN)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(notForbidden());
        mvc.perform(get("/api/v1/billing/admin/abonnements").with(as(Role.SUPER_ADMIN))).andExpect(notForbidden());
    }
}
