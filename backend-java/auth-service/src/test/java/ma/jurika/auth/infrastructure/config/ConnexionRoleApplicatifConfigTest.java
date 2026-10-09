package ma.jurika.auth.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L0, etape E13c : la configuration d'auth-service (application.yml) fait
 * tourner l'application en role d'execution jurika_app et Flyway avec le
 * proprietaire. Le mot de passe de jurika_app est OBLIGATOIRE, sans valeur de
 * repli : s'il manque, la propriete est irresoluble et le service ne demarre pas.
 */
class ConnexionRoleApplicatifConfigTest {

    @Test
    void application_en_jurika_app_et_flyway_en_proprietaire() throws Exception {
        StandardEnvironment env = environnement(Map.of("JURIKA_APP_PASSWORD", "mdp-app"));
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("jurika_app");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("mdp-app");
        assertThat(env.getProperty("spring.flyway.user")).isEqualTo("jurika_user");
        assertThat(env.getProperty("spring.flyway.password")).isNotBlank();
        assertThat(env.getProperty("spring.flyway.user"))
                .isNotEqualTo(env.getProperty("spring.datasource.username"));
    }

    @Test
    void sans_mot_de_passe_dedie_la_configuration_ne_se_resout_pas() throws Exception {
        StandardEnvironment env = environnement(Map.of());
        assertThatThrownBy(() -> env.getProperty("spring.datasource.password"))
                .hasMessageContaining("JURIKA_APP_PASSWORD");
    }

    private static StandardEnvironment environnement(Map<String, Object> variables) throws Exception {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        env.getPropertySources().addFirst(new MapPropertySource("variables", variables));
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                .forEach(source -> env.getPropertySources().addLast(source));
        return env;
    }
}
