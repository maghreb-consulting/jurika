package ma.jurika.billing.api;

import ma.jurika.billing.application.BillingQueryService;
import ma.jurika.billing.application.ChangePlanUseCase;
import ma.jurika.billing.application.ContactSalesUseCase;
import ma.jurika.billing.application.PreparePaymentUseCase;
import ma.jurika.billing.application.SubscribeUseCase;
import ma.jurika.billing.application.ValidatePaymentUseCase;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lot J3 (2026-06-27) — verifie l'ouverture du parcours d'upgrade a l'EMPLOYE.
 * Lot L0 (E17) : la validation des paiements est reservee au SUPER_ADMIN.
 *
 * <p>On teste l'AUTORISATION uniquement (le corps metier est mock) :
 * <ul>
 *   <li>endpoints upgrade/souscription : EMPLOYE doit passer (pas de 403) ;</li>
 *   <li>operations sensibles (invoices, payments, validate) : EMPLOYE reste 403,
 *       SUPERVISEUR passe.</li>
 * </ul>
 * Le @PreAuthorize de methode etant evalue AVANT le corps, "pas 403" suffit a
 * prouver que l'autorisation est accordee (le statut reel 2xx/4xx/5xx du corps
 * mock importe peu ici).
 */
@WebMvcTest(controllers = BillingController.class)
@Import({RoleHierarchyAutoConfiguration.class, BillingRoleSecurityTest.MethodSecurity.class})
class BillingRoleSecurityTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {}

    @Autowired MockMvc mvc;

    @MockBean SubscribeUseCase subscribeUseCase;
    @MockBean BillingQueryService queryService;
    @MockBean ContactSalesUseCase contactSalesUseCase;
    @MockBean ChangePlanUseCase changePlanUseCase;
    @MockBean PreparePaymentUseCase preparePaymentUseCase;
    @MockBean ValidatePaymentUseCase validatePaymentUseCase;

    @BeforeEach
    void setTenant() {
        // requireWorkspace() lit TenantContext (pose en prod par JwtAuthFilter).
        TenantContext.set(UUID.randomUUID());
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static RequestPostProcessor as(Role role) {
        AuthenticatedUser principal = new AuthenticatedUser(
                UUID.randomUUID(), UUID.randomUUID(), "actor@jurika.ma", role);
        var auth = new UsernamePasswordAuthenticationToken(
                principal, "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(auth);
    }

    /** Autorisation accordee : le statut n'est PAS 403 (le corps mock fait le reste). */
    private static ResultMatcher notForbidden() {
        return result -> assertNotEquals(403, result.getResponse().getStatus(),
                "l'autorisation doit etre accordee (pas de 403)");
    }

    // ─────────────── Parcours upgrade : EMPLOYE autorise ───────────────

    @Test
    void changePlan_employe_estAutorise() throws Exception {
        mvc.perform(post("/api/v1/billing/change-plan").with(as(Role.EMPLOYE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetPlanCode\":\"business\"}"))
                .andExpect(notForbidden());
    }

    @Test
    void previewChangePlan_employe_estAutorise() throws Exception {
        mvc.perform(post("/api/v1/billing/change-plan/preview").with(as(Role.EMPLOYE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetPlanCode\":\"business\"}"))
                .andExpect(notForbidden());
    }

    @Test
    void preparePayment_employe_estAutorise() throws Exception {
        mvc.perform(post("/api/v1/billing/prepare-payment").with(as(Role.EMPLOYE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(notForbidden());
    }

    @Test
    void checkoutSession_employe_estAutorise() throws Exception {
        mvc.perform(post("/api/v1/billing/checkout-session").with(as(Role.EMPLOYE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"business\",\"contactEmail\":\"a@b.ma\",\"workspaceName\":\"WS\"}"))
                .andExpect(notForbidden());
    }

    @Test
    void getSubscription_employe_estAutorise() throws Exception {
        mvc.perform(get("/api/v1/billing/subscription").with(as(Role.EMPLOYE)))
                .andExpect(notForbidden());
    }

    @Test
    void customerPortal_employe_estAutorise() throws Exception {
        mvc.perform(post("/api/v1/billing/customer-portal").with(as(Role.EMPLOYE)).with(csrf()))
                .andExpect(notForbidden());
    }

    @Test
    void contactSales_employe_estAutorise() throws Exception {
        mvc.perform(post("/api/v1/billing/contact-sales").with(as(Role.EMPLOYE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(notForbidden());
    }

    // ─────────────── Operations sensibles : EMPLOYE refuse (403) ───────────────

    @Test
    void validatePayment_employe_estRefuse_403() throws Exception {
        mvc.perform(patch("/api/v1/billing/payments/{id}/validate", 1L).with(as(Role.EMPLOYE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    // Lot L0 (E17, RG-PAY-02) : la validation d'un paiement hors ligne est
    // reservee au super-admin (JURIKA), jamais au cabinet lui-meme.
    @Test
    void validatePayment_superviseur_estRefuse_403() throws Exception {
        mvc.perform(patch("/api/v1/billing/payments/{id}/validate", 1L).with(as(Role.SUPERVISEUR)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void validatePayment_superAdmin_estAutorise() throws Exception {
        mvc.perform(patch("/api/v1/billing/payments/{id}/validate", 1L).with(as(Role.SUPER_ADMIN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(notForbidden());
        // Le paiement est designe par son identifiant seul : le workspace du
        // super-admin n'est pas celui du cabinet qui a paye.
        org.mockito.Mockito.verify(validatePaymentUseCase).execute(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listInvoices_employe_estRefuse_403() throws Exception {
        mvc.perform(get("/api/v1/billing/invoices").with(as(Role.EMPLOYE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listInvoices_superviseur_estAutorise() throws Exception {
        mvc.perform(get("/api/v1/billing/invoices").with(as(Role.SUPERVISEUR)))
                .andExpect(notForbidden());
    }

    @Test
    void listPayments_employe_estRefuse_403() throws Exception {
        mvc.perform(get("/api/v1/billing/payments").with(as(Role.EMPLOYE)))
                .andExpect(status().isForbidden());
    }
}
