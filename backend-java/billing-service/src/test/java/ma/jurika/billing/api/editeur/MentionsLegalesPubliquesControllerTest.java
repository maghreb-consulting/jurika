package ma.jurika.billing.api.editeur;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.billing.application.editeur.EditeurParametresService;
import ma.jurika.billing.application.editeur.EditeurParametresService.MentionsLegales;
import ma.jurika.billing.infrastructure.config.SecurityConfig;
import ma.jurika.common.security.JwtPublicKeyProvider;
import ma.jurika.common.security.RoleHierarchyAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lot L6b (E2) : {@code GET /api/v1/public/editeur/mentions-legales}, avec la VRAIE chaine de
 * securite de billing-service : anonyme permis, lecture seule, contenu exact (mentions legales
 * seulement, nulles quand elles manquent), cache court.
 */
@WebMvcTest(controllers = {MentionsLegalesPubliquesController.class, EditeurParametresController.class})
@Import({SecurityConfig.class, RoleHierarchyAutoConfiguration.class})
class MentionsLegalesPubliquesControllerTest {

    private static final String CHEMIN = "/api/v1/public/editeur/mentions-legales";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean EditeurParametresService service;
    @MockBean JwtPublicKeyProvider cles;

    @Test
    void anonyme_lit_les_mentions_legales_exactes() throws Exception {
        when(service.mentionsLegales()).thenReturn(new MentionsLegales(
                "Maghreb Consulting", "SARL", "100 000", "12 rue de l'Exemple", "Casablanca",
                "123456", "Casablanca", "001122334455667", null, null, "Nom du g\u00e9rant", null));

        MvcResult r = mvc.perform(get(CHEMIN)).andExpect(status().isOk()).andReturn();
        JsonNode corps = json.readTree(r.getResponse().getContentAsString());

        List<String> cles = new ArrayList<>();
        corps.fieldNames().forEachRemaining(cles::add);
        assertThat(cles).containsExactly("raisonSociale", "formeJuridique", "capitalSocial", "siegeSocial", "ville",
                "rcNumero", "rcTribunal", "ice", "identifiantFiscal", "taxeProfessionnelle",
                "representantNom", "representantQualite");
        assertThat(corps.get("raisonSociale").asText()).isEqualTo("Maghreb Consulting");
        assertThat(corps.get("ice").asText()).isEqualTo("001122334455667");
        assertThat(corps.get("representantNom").asText()).isEqualTo("Nom du g\u00e9rant");
        // Mention absente : nulle (le front affiche "non renseign\u00e9"), presente dans le corps.
        assertThat(corps.get("identifiantFiscal").isNull()).isTrue();
        assertThat(corps.get("taxeProfessionnelle").isNull()).isTrue();
        assertThat(corps.get("representantQualite").isNull()).isTrue();
        assertThat(r.getResponse().getContentAsString())
                .doesNotContain("tauxTva").doesNotContain("modifie").doesNotContain("\"id\"").doesNotContain("manquants");

        String cache = r.getResponse().getHeader("Cache-Control");
        assertThat(cache).contains("max-age=300").contains("public");
    }

    @Test
    void la_route_publique_est_en_lecture_seule() throws Exception {
        int statut = mvc.perform(put(CHEMIN).contentType("application/json").content("{\"raisonSociale\":\"X\"}"))
                .andReturn().getResponse().getStatus();
        assertThat(statut).isIn(401, 403, 405);
        verify(service, never()).modifier(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void les_parametres_complets_de_l_editeur_restent_fermes_a_l_anonyme() throws Exception {
        int statut = mvc.perform(get("/api/v1/billing/admin/editeur")).andReturn().getResponse().getStatus();
        assertThat(statut).isIn(401, 403);
        int historique = mvc.perform(get("/api/v1/billing/admin/editeur/historique")).andReturn().getResponse().getStatus();
        assertThat(historique).isIn(401, 403);
        verify(service, never()).lire();
        verify(service, never()).historique();
    }
}
