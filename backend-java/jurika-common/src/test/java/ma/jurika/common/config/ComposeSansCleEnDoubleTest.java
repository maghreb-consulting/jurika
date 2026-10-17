package ma.jurika.common.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Recette (2026-10-17) : deux branches avaient ajoute la meme cle JURIKA_TEST_SEED_ENABLED a
 * l'environnement d'auth-service dans docker-compose.services.yml. docker compose refusait le
 * fichier ("mapping key ... already defined") : la reconstruction de la pile du Z440 echouait,
 * alors que tous les tests qui lisent ce fichier passaient (SnakeYAML garde la derniere valeur par
 * defaut). Chaque fichier compose du depot est desormais lu cles en double interdites.
 */
class ComposeSansCleEnDoubleTest {

    private static final Path INFRA = Path.of("..", "..", "infrastructure");

    static Object lireStrict(String contenu) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        return new Yaml(options).load(contenu);
    }

    private static List<Path> fichiersCompose() throws IOException {
        try (Stream<Path> s = Files.walk(INFRA)) {
            return s.filter(p -> p.getFileName().toString().matches("docker-compose.*\\.ya?ml"))
                    .filter(p -> !p.getFileName().toString().contains(".old."))
                    .sorted().toList();
        }
    }

    @Test
    void aucun_fichier_compose_ne_porte_de_cle_en_double() throws IOException {
        List<Path> fichiers = fichiersCompose();
        assertThat(fichiers).as("fichiers compose trouves").isNotEmpty();
        for (Path f : fichiers) {
            String contenu = Files.readString(f, StandardCharsets.UTF_8);
            assertThatCode(() -> lireStrict(contenu)).as(f.toString()).doesNotThrowAnyException();
        }
    }

    @Test
    void une_cle_en_double_est_bien_detectee() {
        String doublon = "services:\n  auth:\n    environment:\n      A: \"1\"\n      A: \"2\"\n";
        assertThatThrownBy(() -> lireStrict(doublon)).hasMessageContaining("duplicate key");
    }
}
