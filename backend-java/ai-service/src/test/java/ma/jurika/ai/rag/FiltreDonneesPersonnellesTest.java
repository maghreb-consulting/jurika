package ma.jurika.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L5 E8 (RG-COM-03, RG-COM-04) : filtre des donnees personnelles, eprouve par le jeu de
 * cas partage avec l'ecran ({@code tests/chatbot/cas-donnees-personnelles.json}).
 */
class FiltreDonneesPersonnellesTest {

    /** Le jeu de cas est a la racine du depot : ai-service -> backend-java -> racine. */
    private static final Path CAS = Path.of("..", "..", "tests", "chatbot", "cas-donnees-personnelles.json");

    private static JsonNode cas() throws IOException {
        return new ObjectMapper().readTree(Files.readString(CAS));
    }

    static Stream<Arguments> bloques() throws IOException {
        List<Arguments> out = new ArrayList<>();
        for (JsonNode c : cas().get("bloques")) {
            out.add(Arguments.of(c.get("question").asText(), c.get("nature").asText()));
        }
        return out.stream();
    }

    static Stream<String> autorises() throws IOException {
        List<String> out = new ArrayList<>();
        for (JsonNode c : cas().get("autorises")) out.add(c.asText());
        return out.stream();
    }

    @ParameterizedTest
    @MethodSource("bloques")
    void question_avec_donnee_personnelle_bloquee(String question, String nature) {
        assertThat(FiltreDonneesPersonnelles.detecter(question))
                .extracting(Enum::name)
                .contains(nature);
    }

    @ParameterizedTest
    @MethodSource("autorises")
    void question_juridique_normale_non_bloquee(String question) {
        assertThat(FiltreDonneesPersonnelles.detecter(question)).isEmpty();
    }

    @Test
    void le_jeu_de_cas_couvre_chaque_nature() throws IOException {
        List<String> natures = new ArrayList<>();
        for (JsonNode c : cas().get("bloques")) natures.add(c.get("nature").asText());
        for (FiltreDonneesPersonnelles.Nature n : FiltreDonneesPersonnelles.Nature.values()) {
            assertThat(natures).as("cas bloque pour " + n).contains(n.name());
        }
        assertThat(cas().get("autorises").size()).isGreaterThanOrEqualTo(10);
    }

    @Test
    void question_vide_ou_nulle_sans_nature() {
        assertThat(FiltreDonneesPersonnelles.detecter(null)).isEmpty();
        assertThat(FiltreDonneesPersonnelles.detecter("  ")).isEmpty();
    }
}
