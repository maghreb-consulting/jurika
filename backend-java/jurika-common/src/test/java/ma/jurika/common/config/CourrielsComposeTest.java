package ma.jurika.common.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L5 (E2) : tout service qui envoie des courriels (spring.mail dans son
 * application.yml) recoit de Compose le reglage du relais SMTP. Defaut constate
 * sur le Z440 : seul billing recevait SMTP_*, auth retombait sur le repli de son
 * application.yml (localhost:1025) et chaque courriel de bienvenue echouait
 * ("Couldn't connect to host, port: localhost, 1025") alors que .env.local
 * designait Brevo. Le relais (Brevo aujourd'hui, Amazon SES demain) ne change
 * que par ces variables.
 */
class CourrielsComposeTest {

    static final List<String> VARIABLES = List.of(
            "SMTP_HOST", "SMTP_PORT", "SMTP_USER", "SMTP_PASSWORD",
            "SMTP_AUTH", "SMTP_STARTTLS", "SMTP_FROM");

    private static final Path BACKEND = Path.of("..").toAbsolutePath().normalize();
    private static final Path INFRA = BACKEND.getParent().resolve("infrastructure");

    /** Services dont l'application.yml declare spring.mail (nom du module = nom du service compose). */
    static Set<String> servicesQuiEnvoient() throws IOException {
        Set<String> s = new LinkedHashSet<>();
        try (Stream<Path> modules = Files.list(BACKEND)) {
            for (Path m : modules.filter(Files::isDirectory).sorted().toList()) {
                Path yml = m.resolve("src/main/resources/application.yml");
                if (!Files.isRegularFile(yml)) continue;
                if (Files.readString(yml, StandardCharsets.UTF_8).matches("(?s).*\\n  mail:\\n.*")) {
                    s.add(m.getFileName().toString());
                }
            }
        }
        return s;
    }

    /** Cles d'environnement d'un service, reunies sur les fichiers compose de la pile du serveur. */
    @SuppressWarnings("unchecked")
    static Set<String> environnement(String service) throws IOException {
        Set<String> cles = new LinkedHashSet<>();
        for (String f : List.of("docker-compose.services.yml", "docker-compose.local.yml")) {
            Map<String, Object> doc = new Yaml().load(Files.readString(INFRA.resolve(f), StandardCharsets.UTF_8));
            Map<String, Object> services = (Map<String, Object>) doc.get("services");
            if (services == null || !(services.get(service) instanceof Map<?, ?> svc)) continue;
            Object env = svc.get("environment");
            if (env instanceof Map<?, ?> m) m.keySet().forEach(k -> cles.add(String.valueOf(k)));
            if (env instanceof List<?> l) l.forEach(e -> cles.add(String.valueOf(e).split("=", 2)[0]));
        }
        return cles;
    }

    @Test
    void auth_et_billing_envoient_des_courriels() throws IOException {
        assertThat(servicesQuiEnvoient()).contains("auth-service", "billing-service");
    }

    @Test
    void chaque_service_qui_envoie_recoit_le_relais_smtp() throws IOException {
        for (String service : servicesQuiEnvoient()) {
            assertThat(environnement(service))
                    .as("variables SMTP transmises par compose a %s", service)
                    .containsAll(VARIABLES);
        }
    }

    @Test
    void auth_recoit_le_choix_du_fournisseur_et_le_nom_d_expediteur() throws IOException {
        assertThat(environnement("auth-service")).contains("JURIKA_EMAIL_PROVIDER", "SMTP_FROM_NAME");
    }

    /** Recette R3 : billing affiche aussi le nom d'expediteur "JURIKA" (avant : l'adresse seule). */
    @Test
    void billing_recoit_le_nom_d_expediteur() throws IOException {
        assertThat(environnement("billing-service")).contains("SMTP_FROM_NAME");
    }

    /**
     * Recette R3 : la route d'envoi des exemplaires de courriels (auth et billing) suit
     * l'interrupteur des donnees de test ; compose le transmet aux deux services (faux par defaut).
     */
    @Test
    void auth_et_billing_recoivent_l_interrupteur_des_donnees_de_test() throws IOException {
        assertThat(environnement("auth-service")).contains("JURIKA_TEST_SEED_ENABLED");
        assertThat(environnement("billing-service")).contains("JURIKA_TEST_SEED_ENABLED");
    }

    /**
     * Les liens des courriels d'auth (verification d'adresse du courriel de bienvenue,
     * confirmation d'un changement de compte) partent de FRONTEND_URL : sans cette
     * variable, auth ecrivait http://localhost:5173 dans le courriel.
     */
    @Test
    void auth_recoit_l_adresse_publique_du_front() throws IOException {
        assertThat(environnement("auth-service")).contains("FRONTEND_URL");
    }

    /** Chaque lien d'auth vise une route qui existe dans le front (App.tsx). */
    @Test
    void les_liens_des_courriels_visent_une_route_du_front() throws IOException {
        String yml = Files.readString(BACKEND.resolve("auth-service/src/main/resources/application.yml"), StandardCharsets.UTF_8);
        String app = Files.readString(BACKEND.getParent().resolve("frontend-react/src/App.tsx"), StandardCharsets.UTF_8);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?m)^\\s+([a-z-]+-base-url): \\$\\{FRONTEND_URL:[^}]*}(/[^\\s]*)$").matcher(yml);
        int liens = 0;
        while (m.find()) {
            liens++;
            assertThat(app).as("route du front pour %s", m.group(1)).contains("path=\"" + m.group(2) + "\"");
        }
        assertThat(liens).as("liens d'auth batis sur FRONTEND_URL").isGreaterThanOrEqualTo(2);
    }
}
