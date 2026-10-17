package ma.jurika.dataroom.api;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decision du 2026-10-17 : la route de donnees de test n'existe qu'en environnement de test.
 * Le controleur exige l'interrupteur jurika.test.seed.enabled=true ET un profil autre que prod :
 * en production (profil prod), il est absent meme si l'interrupteur est leve par erreur.
 */
class TestSeedAbsentEnProductionTest {

    private final ApplicationContextRunner contexte = new ApplicationContextRunner()
            .withBean(JdbcTemplate.class, () -> Mockito.mock(JdbcTemplate.class))
            .withUserConfiguration(TestSeedController.class);

    @Test
    void absent_par_defaut() {
        contexte.run(c -> assertThat(c).doesNotHaveBean(TestSeedController.class));
    }

    @Test
    void absent_en_production_meme_interrupteur_leve() {
        contexte.withPropertyValues("spring.profiles.active=prod", "jurika.test.seed.enabled=true")
                .run(c -> assertThat(c).doesNotHaveBean(TestSeedController.class));
    }

    @Test
    void present_en_test_avec_l_interrupteur() {
        contexte.withPropertyValues("spring.profiles.active=dev", "jurika.test.seed.enabled=true")
                .run(c -> assertThat(c).hasSingleBean(TestSeedController.class));
    }
}
