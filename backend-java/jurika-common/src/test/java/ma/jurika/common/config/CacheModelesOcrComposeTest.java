package ma.jurika.common.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L7 (E1) : les modeles docTR du service OCR sont gardes sur un volume nomme. Sans lui,
 * chaque recreation du conteneur retelechargeait ~160 Mo (12 minutes a l'activation de L6,
 * service non sain pendant ce temps).
 */
class CacheModelesOcrComposeTest {

    private static final Path INFRA = Path.of("..").toAbsolutePath().normalize().getParent().resolve("infrastructure");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> yaml(String f) throws IOException {
        return new Yaml().load(Files.readString(INFRA.resolve(f), StandardCharsets.UTF_8));
    }

    @Test
    @SuppressWarnings("unchecked")
    void le_cache_des_modeles_ocr_est_sur_un_volume_nomme_declare() throws IOException {
        Map<String, Object> services = (Map<String, Object>) yaml("docker-compose.services.yml").get("services");
        Map<String, Object> ocr = (Map<String, Object>) services.get("ocr-service");
        List<String> volumes = (List<String>) ocr.get("volumes");
        assertThat(volumes).as("volumes du service ocr").isNotNull().contains("ocr_modeles:/root/.cache/doctr");
        Map<String, Object> declares = (Map<String, Object>) yaml("docker-compose.yml").get("volumes");
        assertThat(declares).containsKey("ocr_modeles");
    }
}
