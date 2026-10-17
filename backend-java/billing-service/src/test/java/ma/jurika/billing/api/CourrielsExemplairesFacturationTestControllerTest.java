package ma.jurika.billing.api;

import ma.jurika.billing.application.email.BillingEmailService;
import ma.jurika.billing.infrastructure.config.SecurityConfig;
import ma.jurika.common.security.JwtPublicKeyProvider;
import ma.jurika.common.security.RoleHierarchyAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recette R3 : route d'envoi de l'exemplaire du courriel de facture
 * ({@code POST /api/v1/test/courriels/facturation}), reservee a l'environnement de test : double
 * verrou (interrupteur jurika.test.seed.enabled=true ET profil autre que prod), appel anonyme
 * permis par la VRAIE chaine de securite de billing quand elle existe.
 */
@WebMvcTest(controllers = CourrielsExemplairesFacturationTestController.class)
@Import({SecurityConfig.class, RoleHierarchyAutoConfiguration.class})
@ActiveProfiles("dev")
@TestPropertySource(properties = "jurika.test.seed.enabled=true")
class CourrielsExemplairesFacturationTestControllerTest {

    @Autowired MockMvc mvc;
    @MockBean BillingEmailService courriels;
    @MockBean JwtPublicKeyProvider cles;

    @Test
    void anonyme_envoie_l_exemplaire_de_facture_avec_son_pdf() throws Exception {
        mvc.perform(post("/api/v1/test/courriels/facturation").contentType("application/json")
                        .content("{\"destinataire\":\"verif@exemple.test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.envois[0].exemplaire").value("facture"))
                .andExpect(jsonPath("$.envois[0].envoye").value(true))
                .andExpect(jsonPath("$.envois[0].piecesJointes[0]").value("Facture-F-EXEMPLE-0001.pdf"));
        verify(courriels).envoyer(eq("verif@exemple.test"), anyString(), eq(BillingEmailService.GABARIT_FACTURE),
                anyMap(), anyList());
    }

    @Test
    void adresse_invalide_refusee_sans_envoi() throws Exception {
        mvc.perform(post("/api/v1/test/courriels/facturation").contentType("application/json")
                        .content("{\"destinataire\":\"pas-une-adresse\"}"))
                .andExpect(status().isBadRequest());
        verify(courriels, never()).envoyer(anyString(), anyString(), anyString(), anyMap(), anyList());
    }

    private static final ApplicationContextRunner CONTEXTE = new ApplicationContextRunner()
            .withBean(BillingEmailService.class, () -> Mockito.mock(BillingEmailService.class))
            .withUserConfiguration(CourrielsExemplairesFacturationTestController.class);

    @Test
    void absente_par_defaut_et_en_production_meme_interrupteur_leve() {
        CONTEXTE.run(c -> assertThat(c).doesNotHaveBean(CourrielsExemplairesFacturationTestController.class));
        CONTEXTE.withPropertyValues("spring.profiles.active=prod", "jurika.test.seed.enabled=true")
                .run(c -> assertThat(c).doesNotHaveBean(CourrielsExemplairesFacturationTestController.class));
        CONTEXTE.withPropertyValues("spring.profiles.active=dev", "jurika.test.seed.enabled=true")
                .run(c -> assertThat(c).hasSingleBean(CourrielsExemplairesFacturationTestController.class));
    }
}
