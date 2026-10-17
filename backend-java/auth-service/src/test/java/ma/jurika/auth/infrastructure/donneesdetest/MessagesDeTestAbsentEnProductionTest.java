package ma.jurika.auth.infrastructure.donneesdetest;

import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.SmsSender;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recette R1 : la lecture des messages des identites de test suit le double verrou de la route
 * de donnees de test (TestSeedAbsentEnProductionTest de dataroom) : absente par defaut, absente
 * en profil prod meme interrupteur leve, presente hors prod avec l'interrupteur. Sans elle, les
 * expediteurs ne sont pas enveloppes.
 */
class MessagesDeTestAbsentEnProductionTest {

    private final ApplicationContextRunner contexte = new ApplicationContextRunner()
            .withBean(JdbcTemplate.class, () -> Mockito.mock(JdbcTemplate.class))
            .withBean(SmsSender.class, () -> (t, b) -> { })
            .withBean(EmailSender.class, () -> (to, s, g, v) -> { })
            .withUserConfiguration(CaptureMessagesDeTest.class, MessagesDeTestController.class);

    @Test
    void absente_par_defaut() {
        contexte.run(c -> {
            assertThat(c).doesNotHaveBean(MessagesDeTestController.class);
            assertThat(c).doesNotHaveBean(CaptureMessagesDeTest.class);
            assertThat(c.getBean(SmsSender.class).getClass().getName()).doesNotContain("Capture");
        });
    }

    @Test
    void absente_en_production_meme_interrupteur_leve() {
        contexte.withPropertyValues("spring.profiles.active=prod", "jurika.test.seed.enabled=true")
                .run(c -> {
                    assertThat(c).doesNotHaveBean(MessagesDeTestController.class);
                    assertThat(c).doesNotHaveBean(CaptureMessagesDeTest.class);
                    assertThat(c.getBean(EmailSender.class).getClass().getName()).doesNotContain("Capture");
                });
    }

    @Test
    void presente_en_test_avec_l_interrupteur() {
        contexte.withPropertyValues("spring.profiles.active=dev", "jurika.test.seed.enabled=true")
                .run(c -> {
                    assertThat(c).hasSingleBean(MessagesDeTestController.class);
                    c.getBean(SmsSender.class).send("+212600000001", "code 123456");
                    assertThat(c.getBean(CaptureMessagesDeTest.class).smsPour("+212600000001"))
                            .extracting(CaptureMessagesDeTest.Sms::texte).containsExactly("code 123456");
                });
    }
}
