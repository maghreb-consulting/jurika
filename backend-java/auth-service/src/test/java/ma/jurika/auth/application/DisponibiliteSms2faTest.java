package ma.jurika.auth.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import ma.jurika.auth.api.CodeSmsDeTestController;
import ma.jurika.auth.domain.port.SmsSender;
import ma.jurika.auth.infrastructure.sms.BoiteSmsDeTest;
import ma.jurika.auth.infrastructure.sms.LoggerSmsSender;
import ma.jurika.common.observability.BusinessMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recette R2 (2026-10-17) : la double authentification par SMS n'est ouverte que si un
 * fournisseur reel est configure, ou, en environnement de test, si la simulation est demandee
 * (jurika.sms.simulation=true, JURIKA_SMS_SIMULATION). En profil prod, la simulation n'est
 * JAMAIS active, meme si la propriete est levee par erreur (double verrou, comme
 * RouteDonneesDeTestConfig) : l'option SMS n'est alors ni proposee ni acceptee.
 * La boite des codes de test et sa route n'existent que sous la meme double condition.
 */
class DisponibiliteSms2faTest {

    private static final SmsSender SIMULE = new LoggerSmsSender(new BusinessMetrics(new SimpleMeterRegistry()));
    private static final SmsSender REEL = (telephone, corps) -> { };

    private final ApplicationContextRunner contexte = new ApplicationContextRunner()
            .withUserConfiguration(DisponibiliteSms2fa.class, BoiteSmsDeTest.class, CodeSmsDeTestController.class);

    private ApplicationContextRunner avec(SmsSender envoi) {
        return contexte.withBean(SmsSender.class, () -> envoi);
    }

    @Test
    void sans_fournisseur_ni_simulation_le_sms_est_indisponible() {
        avec(SIMULE).run(c -> {
            DisponibiliteSms2fa d = c.getBean(DisponibiliteSms2fa.class);
            assertThat(d.disponible()).isFalse();
            assertThat(d.simule()).isFalse();
            assertThat(c).doesNotHaveBean(BoiteSmsDeTest.class).doesNotHaveBean(CodeSmsDeTestController.class);
        });
    }

    @Test
    void en_test_avec_la_simulation_le_sms_est_ouvert_et_simule() {
        avec(SIMULE).withPropertyValues("spring.profiles.active=prod-local", "jurika.sms.simulation=true").run(c -> {
            DisponibiliteSms2fa d = c.getBean(DisponibiliteSms2fa.class);
            assertThat(d.disponible()).isTrue();
            assertThat(d.simule()).isTrue();
            assertThat(c).hasSingleBean(BoiteSmsDeTest.class).hasSingleBean(CodeSmsDeTestController.class);
        });
    }

    @Test
    void en_production_la_simulation_est_refusee_meme_si_la_propriete_est_levee() {
        avec(SIMULE).withPropertyValues("spring.profiles.active=prod", "jurika.sms.simulation=true").run(c -> {
            DisponibiliteSms2fa d = c.getBean(DisponibiliteSms2fa.class);
            assertThat(d.disponible()).as("jamais simule en production").isFalse();
            assertThat(d.simule()).isFalse();
            assertThat(c).doesNotHaveBean(BoiteSmsDeTest.class).doesNotHaveBean(CodeSmsDeTestController.class);
        });
    }

    @Test
    void en_production_un_fournisseur_reel_ouvre_le_sms_sans_simulation() {
        avec(REEL).withPropertyValues("spring.profiles.active=prod").run(c -> {
            DisponibiliteSms2fa d = c.getBean(DisponibiliteSms2fa.class);
            assertThat(d.disponible()).isTrue();
            assertThat(d.simule()).isFalse();
        });
    }

    @Test
    void la_propriete_de_simulation_est_fausse_par_defaut_dans_application_yml() throws Exception {
        try (var in = getClass().getResourceAsStream("/application.yml")) {
            String yml = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(yml).contains("simulation: ${JURIKA_SMS_SIMULATION:false}");
        }
    }
}
