package ma.jurika.auth.api;

import ma.jurika.auth.infrastructure.sms.BoiteSmsDeTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recette R2 (2026-10-17) : route reservee a l'environnement de test qui rend le dernier code
 * SMS simule de la double authentification envoye a un numero (parcours de bout en bout, sans
 * acces aux journaux). Sa presence (simulation + hors prod) est prouvee par DisponibiliteSms2faTest.
 */
class CodeSmsDeTestControllerTest {

    private final BoiteSmsDeTest boite = new BoiteSmsDeTest();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CodeSmsDeTestController(boite)).build();

    @Test
    void rend_le_dernier_code_depose_pour_ce_numero() throws Exception {
        boite.deposer("+212611223344", "111111", Instant.now().plusSeconds(300));
        boite.deposer("+212611223344", "222222", Instant.now().plusSeconds(300));
        mvc.perform(get("/api/v1/test/auth-2fa-sms/dernier-code").param("telephone", "+212611223344"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("222222"))
                .andExpect(jsonPath("$.telephone").value("+212611223344"));
    }

    @Test
    void numero_sans_code_404() throws Exception {
        mvc.perform(get("/api/v1/test/auth-2fa-sms/dernier-code").param("telephone", "+212600000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void code_expire_n_est_plus_rendu() throws Exception {
        boite.deposer("+212633445566", "333333", Instant.now().minusSeconds(1));
        mvc.perform(get("/api/v1/test/auth-2fa-sms/dernier-code").param("telephone", "+212633445566"))
                .andExpect(status().isNotFound());
    }
}
