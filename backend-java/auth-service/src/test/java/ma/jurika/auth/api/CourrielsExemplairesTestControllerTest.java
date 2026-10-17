package ma.jurika.auth.api;

import ma.jurika.auth.domain.port.EmailSender;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Recette R3 : route d'envoi des exemplaires de courriels ({@code POST /api/v1/test/courriels/auth}),
 * reservee a l'environnement de test. Double verrou, comme le seed de dataroom
 * (TestSeedAbsentEnProductionTest) : interrupteur jurika.test.seed.enabled=true ET profil autre
 * que prod ; absente par defaut et en production meme interrupteur leve.
 */
class CourrielsExemplairesTestControllerTest {

    private final ApplicationContextRunner contexte = new ApplicationContextRunner()
            .withBean(EmailSender.class, () -> Mockito.mock(EmailSender.class))
            .withUserConfiguration(CourrielsExemplairesTestController.class);

    @Test
    void absente_par_defaut() {
        contexte.run(c -> assertThat(c).doesNotHaveBean(CourrielsExemplairesTestController.class));
    }

    @Test
    void absente_en_production_meme_interrupteur_leve() {
        contexte.withPropertyValues("spring.profiles.active=prod", "jurika.test.seed.enabled=true")
                .run(c -> assertThat(c).doesNotHaveBean(CourrielsExemplairesTestController.class));
    }

    @Test
    void presente_en_test_avec_l_interrupteur() {
        contexte.withPropertyValues("spring.profiles.active=dev", "jurika.test.seed.enabled=true")
                .run(c -> assertThat(c).hasSingleBean(CourrielsExemplairesTestController.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void envoie_un_exemplaire_de_chaque_courriel_a_l_adresse_donnee() {
        EmailSender sender = mock(EmailSender.class);
        ResponseEntity<Map<String, Object>> r = new CourrielsExemplairesTestController(sender)
                .envoyer(Map.of("destinataire", "verif@exemple.test"));
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        List<Map<String, Object>> envois = (List<Map<String, Object>>) r.getBody().get("envois");
        assertThat(envois).extracting(m -> m.get("exemplaire")).containsExactly(
                "bienvenue", "identifiants-employe", "identifiants-client", "reinitialisation", "notification-client");
        assertThat(envois).allSatisfy(m -> assertThat(m.get("envoye")).isEqualTo(true));
        verify(sender, times(5)).sendTemplated(eq("verif@exemple.test"), anyString(), anyString(), anyMap());
    }

    @Test
    void adresse_absente_ou_invalide_refusee_sans_envoi() {
        EmailSender sender = mock(EmailSender.class);
        CourrielsExemplairesTestController c = new CourrielsExemplairesTestController(sender);
        assertThat(c.envoyer(Map.of()).getStatusCode().value()).isEqualTo(400);
        assertThat(c.envoyer(Map.of("destinataire", "pas-une-adresse")).getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(sender);
    }
}
