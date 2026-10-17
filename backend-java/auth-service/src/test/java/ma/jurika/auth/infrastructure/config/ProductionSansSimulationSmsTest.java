package ma.jurika.auth.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recette R2 (2026-10-17) : en production, la double authentification par SMS n'est jamais
 * simulee. Le fichier de deploiement de production impose a auth-service le profil prod ET
 * l'interrupteur JURIKA_SMS_SIMULATION a faux (double verrou : voir DisponibiliteSms2faTest).
 * La pile commune (docker-compose.services.yml) le transmet avec un defaut faux.
 */
class ProductionSansSimulationSmsTest {

    private static final Path INFRA = Path.of("..", "..", "infrastructure");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> environnement(Path fichier, String service) throws Exception {
        assertThat(fichier).as("fichier " + fichier).exists();
        try (InputStream in = Files.newInputStream(fichier)) {
            Map<String, Object> racine = new Yaml().load(in);
            Map<String, Object> services = (Map<String, Object>) racine.get("services");
            Map<String, Object> s = (Map<String, Object>) services.get(service);
            assertThat(s).as("service " + service).isNotNull();
            Map<String, Object> env = (Map<String, Object>) s.get("environment");
            assertThat(env).as("environnement de " + service).isNotNull();
            return env;
        }
    }

    @Test
    void production_auth_en_profil_prod_sans_simulation_sms() throws Exception {
        Map<String, Object> env = environnement(INFRA.resolve("production/docker-compose.prod.yml"), "auth");
        assertThat(env.get("SPRING_PROFILES_ACTIVE")).as("profil").isEqualTo("prod");
        assertThat(String.valueOf(env.get("JURIKA_SMS_SIMULATION"))).as("simulation").isEqualTo("false");
    }

    @Test
    void pile_commune_transmet_la_simulation_avec_un_defaut_faux() throws Exception {
        Map<String, Object> env = environnement(INFRA.resolve("docker-compose.services.yml"), "auth-service");
        assertThat(String.valueOf(env.get("JURIKA_SMS_SIMULATION"))).isEqualTo("${JURIKA_SMS_SIMULATION:-false}");
    }
}
