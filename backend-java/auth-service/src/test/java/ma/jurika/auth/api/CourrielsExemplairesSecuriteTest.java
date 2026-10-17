package ma.jurika.auth.api;

import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.infrastructure.config.SecurityConfig;
import ma.jurika.auth.infrastructure.security.ChangePasswordEnforcer;
import ma.jurika.auth.infrastructure.security.Setup2faRequiredEnforcer;
import ma.jurika.common.security.JwtPublicKeyProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recette R3 : en environnement de test, la route des exemplaires de courriels d'auth est appelable
 * sans jeton par la VRAIE chaine de securite d'auth-service (l'orchestrateur l'appelle apres
 * redeploiement). Hors test, le controleur n'existe pas (CourrielsExemplairesTestControllerTest).
 */
@WebMvcTest(controllers = CourrielsExemplairesTestController.class)
@Import({SecurityConfig.class, ChangePasswordEnforcer.class, Setup2faRequiredEnforcer.class})
@ActiveProfiles("dev")
@TestPropertySource(properties = "jurika.test.seed.enabled=true")
class CourrielsExemplairesSecuriteTest {

    @Autowired MockMvc mvc;
    @MockBean EmailSender sender;
    @MockBean JwtPublicKeyProvider cles;

    @Test
    void anonyme_envoie_les_cinq_exemplaires() throws Exception {
        mvc.perform(post("/api/v1/test/courriels/auth").contentType("application/json")
                        .content("{\"destinataire\":\"verif@exemple.test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.envois.length()").value(5));
        verify(sender, times(5)).sendTemplated(eq("verif@exemple.test"), anyString(), anyString(), anyMap());
    }
}
