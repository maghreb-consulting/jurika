package ma.jurika.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chantier outillage/secrets-z440 : aucun secret n'a de valeur de repli publiee
 * dans le depot. Les identifiants du Z440 etaient egaux a ces replis : la pile ne
 * marchait que par coincidence, et quiconque lisait le depot connaissait les mots
 * de passe. Un service doit refuser de demarrer si son secret manque.
 *
 * <p>Fichiers controles : application*.yml de chaque service, fichiers compose,
 * backend-node/src/index.js. Permis : pas de repli, {@code :?} (compose), repli
 * vide, repli vers une autre variable elle-meme conforme.
 */
class ReplisSecretsPubliesTest {

    private static final Set<String> SECRETS = Set.of(
            "POSTGRES_PASSWORD", "JURIKA_APP_PASSWORD", "RABBITMQ_PASSWORD",
            "MINIO_ROOT_PASSWORD", "MINIO_SECRET_KEY", "JWT_SECRET",
            "AES_SECRET_KEY", "REDIS_PASSWORD");

    /** Repertoire backend-java (les tests de ce module tournent dans jurika-common). */
    private static final Path BACKEND = Path.of("..").toAbsolutePath().normalize();
    private static final Path RACINE = BACKEND.getParent();

    private static List<Path> fichiers() throws IOException {
        List<Path> f = new ArrayList<>();
        try (Stream<Path> s = Files.list(BACKEND)) {
            for (Path module : s.filter(Files::isDirectory).toList()) {
                Path res = module.resolve("src/main/resources");
                if (!Files.isDirectory(res)) continue;
                try (Stream<Path> y = Files.list(res)) {
                    y.filter(p -> p.getFileName().toString().matches("application.*\\.ya?ml")).forEach(f::add);
                }
            }
        }
        try (Stream<Path> s = Files.list(RACINE.resolve("infrastructure"))) {
            s.filter(p -> p.getFileName().toString().matches("docker-compose.*\\.yml"))
             .filter(p -> !p.getFileName().toString().contains("old"))
             .forEach(f::add);
        }
        f.add(RACINE.resolve("backend-node/src/index.js"));
        return f;
    }

    /** Ecarts d'un texte : NOM -> valeur de repli litterale non vide. */
    static List<String> ecarts(String texte) {
        List<String> e = new ArrayList<>();
        int i = 0;
        while ((i = texte.indexOf("${", i)) >= 0) {
            int prof = 0;
            int k = i;
            for (; k < texte.length(); k++) {
                if (texte.startsWith("${", k)) { prof++; k++; continue; }
                if (texte.charAt(k) == '}' && --prof == 0) break;
            }
            String dedans = texte.substring(i + 2, Math.min(k, texte.length()));
            Matcher m = Pattern.compile("^([A-Z0-9_]+)(.*)$", Pattern.DOTALL).matcher(dedans);
            if (m.matches() && SECRETS.contains(m.group(1))) {
                String reste = m.group(2);
                if (!reste.isEmpty() && !reste.startsWith(":?")) {
                    String repli = reste.startsWith(":-") ? reste.substring(2) : reste.substring(1);
                    if (!repli.isEmpty() && !repli.startsWith("${")) {
                        e.add(m.group(1) + " (ligne " + (texte.substring(0, i).split("\n", -1).length) + ")");
                    }
                }
            }
            i += 2;
        }
        Matcher js = Pattern.compile("process\\.env\\.([A-Z0-9_]+)\\s*(\\|\\||\\?\\?)\\s*['\"`]").matcher(texte);
        while (js.find()) {
            if (SECRETS.contains(js.group(1))) {
                e.add(js.group(1) + " (js, ligne " + (texte.substring(0, js.start()).split("\n", -1).length) + ")");
            }
        }
        return e;
    }

    @Test
    void le_controle_detecte_un_repli_publie() {
        assertThat(ecarts("password: ${POSTGRES_PASSWORD:valeur}")).hasSize(1);
        assertThat(ecarts("PASS: ${RABBITMQ_PASSWORD:-valeur}")).hasSize(1);
        assertThat(ecarts("k: ${MINIO_SECRET_KEY:${MINIO_ROOT_PASSWORD:valeur}}")).hasSize(1);
        assertThat(ecarts("const s = process.env.JWT_SECRET || 'valeur';")).hasSize(1);
        assertThat(ecarts("p: ${POSTGRES_PASSWORD}\nq: ${POSTGRES_PASSWORD:?absent}\nr: ${JWT_SECRET:}\n"
                + "u: ${POSTGRES_USER:jurika_user}\nk: ${MINIO_SECRET_KEY:${MINIO_ROOT_PASSWORD}}")).isEmpty();
    }

    @Test
    void aucun_secret_n_a_de_repli_publie() throws IOException {
        List<Path> f = fichiers();
        assertThat(f).as("fichiers controles").hasSizeGreaterThan(10);
        List<String> tous = new ArrayList<>();
        for (Path p : f) {
            for (String e : ecarts(Files.readString(p, StandardCharsets.UTF_8))) {
                tous.add(RACINE.relativize(p) + " : " + e);
            }
        }
        assertThat(tous).isEmpty();
    }
}
