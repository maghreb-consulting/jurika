package ma.jurika.billing.api.contrat;

import ma.jurika.billing.api.editeur.EditeurParametresController;
import ma.jurika.billing.application.contrat.ContratService;
import ma.jurika.billing.application.editeur.EditeurParametresService;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.RoleHierarchyAutoConfiguration;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lot L7 (E6, P6) : droits du contrat et des parametres de l'editeur, verifies par le serveur.
 * Acceptation, resiliation, historique et exemplaire : superviseur. Activation, resiliation
 * d'office, versions et parametres de l'editeur : super-admin seulement. L'employe ne lit que
 * l'etat reduit (il doit attendre l'acceptation du superviseur).
 */
@WebMvcTest(controllers = {ContratCabinetController.class, ContratAdminController.class,
        EditeurParametresController.class})
@Import({RoleHierarchyAutoConfiguration.class, ContratRolesSecurityTest.MethodSecurity.class,
        ContratExceptionHandler.class})
class ContratRolesSecurityTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {}

    @Autowired MockMvc mvc;

    @MockBean ContratService service;
    @MockBean EditeurParametresService editeur;

    private static RequestPostProcessor as(Role role) {
        AuthenticatedUser principal = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "a@jurika.ma", role);
        return authentication(new UsernamePasswordAuthenticationToken(principal, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private static ResultMatcher autorise() {
        return r -> assertNotEquals(403, r.getResponse().getStatus(), "autorisation attendue");
    }

    @Test
    void employe_ne_peut_ni_accepter_ni_resilier_ni_telecharger() throws Exception {
        mvc.perform(post("/api/v1/billing/contrat/accepter").with(as(Role.EMPLOYE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/billing/contrat/resilier").with(as(Role.EMPLOYE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/billing/contrat/historique").with(as(Role.EMPLOYE))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/billing/contrat/acceptations/" + UUID.randomUUID() + "/exemplaire")
                .with(as(Role.EMPLOYE))).andExpect(status().isForbidden());
    }

    @Test
    void employe_lit_un_etat_reduit_sans_conditions_ni_prix() throws Exception {
        when(service.etat(any())).thenReturn(new ContratService.Etat("EN_ATTENTE", true, "CONDITIONS_PARTICULIERES",
                null, null, null, null, null, null, null, false, null));
        mvc.perform(get("/api/v1/billing/contrat/etat").with(as(Role.EMPLOYE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.acceptationRequise").value(true))
                .andExpect(jsonPath("$.enAttente").doesNotExist());
    }

    @Test
    void superviseur_accede_a_l_ecran_du_contrat() throws Exception {
        mvc.perform(post("/api/v1/billing/contrat/accepter").with(as(Role.SUPERVISEUR)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(autorise());
        mvc.perform(get("/api/v1/billing/contrat/historique").with(as(Role.SUPERVISEUR))).andExpect(autorise());
    }

    @Test
    void superviseur_et_client_n_ont_pas_les_ecrans_du_super_admin() throws Exception {
        for (Role r : List.of(Role.SUPERVISEUR, Role.EMPLOYE, Role.CLIENT)) {
            mvc.perform(get("/api/v1/billing/admin/contrats").with(as(r))).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/billing/admin/contrats/" + UUID.randomUUID() + "/conditions").with(as(r)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/billing/admin/editeur").with(as(r))).andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/billing/admin/editeur").with(as(r)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/billing/contrat/etat").with(as(Role.CLIENT))).andExpect(status().isForbidden());
    }

    @Test
    void super_admin_accede_aux_contrats_et_aux_parametres_de_l_editeur() throws Exception {
        mvc.perform(get("/api/v1/billing/admin/contrats").with(as(Role.SUPER_ADMIN))).andExpect(autorise());
        mvc.perform(get("/api/v1/billing/admin/editeur").with(as(Role.SUPER_ADMIN))).andExpect(autorise());
        mvc.perform(get("/api/v1/billing/admin/editeur/historique").with(as(Role.SUPER_ADMIN))).andExpect(autorise());
    }
}
