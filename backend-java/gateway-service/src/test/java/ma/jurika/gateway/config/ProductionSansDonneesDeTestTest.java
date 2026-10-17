package ma.jurika.gateway.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decision du 2026-10-17 : la route de donnees de test est absente de la version de production.
 * Le fichier de deploiement de production impose, a la passerelle ET a dataroom-service, le
 * profil prod et l'interrupteur JURIKA_TEST_SEED_ENABLED a faux (double verrou : voir
 * RouteDonneesDeTestTest et TestSeedAbsentEnProductionTest).
 */
class ProductionSansDonneesDeTestTest {

    private static final Path PROD = Path.of("..", "..", "infrastructure", "production", "docker-compose.prod.yml");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> environnement(String service) throws Exception {
        assertThat(PROD).as("fichier de production").exists();
        try (InputStream in = Files.newInputStream(PROD)) {
            Map<String, Object> racine = new Yaml().load(in);
            Map<String, Object> services = (Map<String, Object>) racine.get("services");
            Map<String, Object> s = (Map<String, Object>) services.get(service);
            assertThat(s).as("service " + service).isNotNull();
            Map<String, Object> env = (Map<String, Object>) s.get("environment");
            assertThat(env).as("environnement de " + service).isNotNull();
            return env;
        }
    }

    /**
     * Recette R3 : auth-service porte la route d'envoi des exemplaires de courriels (meme double
     * verrou) : profil prod et interrupteur a faux en production. billing-service n'est pas
     * deploye par ce fichier ; s'il l'est un jour, memes reglages exiges.
     */
    @Test
    @SuppressWarnings("unchecked")
    void services_des_exemplaires_de_courriels_sans_donnees_de_test() throws Exception {
        Map<String, Object> env = environnement("auth");
        assertThat(env.get("SPRING_PROFILES_ACTIVE")).as("auth : profil").isEqualTo("prod");
        assertThat(String.valueOf(env.get("JURIKA_TEST_SEED_ENABLED"))).as("auth : interrupteur").isEqualTo("false");
        try (InputStream in = Files.newInputStream(PROD)) {
            Map<String, Object> services = (Map<String, Object>) ((Map<String, Object>) new Yaml().load(in)).get("services");
            if (services.containsKey("billing")) {
                Map<String, Object> b = environnement("billing");
                assertThat(b.get("SPRING_PROFILES_ACTIVE")).isEqualTo("prod");
                assertThat(String.valueOf(b.get("JURIKA_TEST_SEED_ENABLED"))).isEqualTo("false");
            }
        }
    }

    @Test
    void passerelle_et_dataroom_en_profil_prod_sans_donnees_de_test() throws Exception {
        for (String service : new String[] {"gateway", "dataroom"}) {
            Map<String, Object> env = environnement(service);
            assertThat(env.get("SPRING_PROFILES_ACTIVE")).as(service + " : profil").isEqualTo("prod");
            assertThat(String.valueOf(env.get("JURIKA_TEST_SEED_ENABLED"))).as(service + " : interrupteur").isEqualTo("false");
        }
    }
}
