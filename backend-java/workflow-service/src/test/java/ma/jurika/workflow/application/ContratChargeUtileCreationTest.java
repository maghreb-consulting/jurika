package ma.jurika.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LE CONTRÔLE QUI AURAIT ÉVITÉ LE DÉFAUT DU LOT B.
 *
 * <p>Le constructeur vivant produisait les 23 documents en ayant perdu 29 des 54
 * clés que l'autre portait. Rien ne l'a signalé, parce que rien ne confrontait ce
 * qu'un constructeur PRODUIT à ce qu'un résolveur LIT. Ce test est cette
 * confrontation, et elle est mécanique des deux côtés :
 *
 * <ul>
 *   <li>le contrat est relevé sur le code des trois résolveurs
 *       ({@link ContratResolveurs}) — aucune liste écrite à la main ;</li>
 *   <li>l'entrée est le corpus réel — toutes les variables des 23 gabarits, lues
 *       au manifeste ;</li>
 *   <li>l'assertion porte sur la charge utile RÉELLEMENT produite, pas sur une
 *       table de correspondance déclarée.</li>
 * </ul>
 *
 * <p><b>Ce test doit être rouge tant que le câblage n'est pas fait.</b> Un test
 * de contrat qui passe avant qu'une clé ne soit produite ne regarde pas le
 * chemin vivant.
 */
class ContratChargeUtileCreationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("Toute clé lue par les trois résolveurs est produite par le constructeur unique")
    void leConstructeurCouvreToutLeContrat() {
        ContratResolveurs contrat = ContratResolveurs.relever();
        Map<String, Object> magasin = magasinCompletDuCorpus();

        Map<String, Object> charge = ConstructeurChargeUtileCreation.construire(magasin);

        List<String> manquantes = new ArrayList<>();

        // Blocs-objet : societe.denomination, formulaires.telephone, …
        contrat.blocs().forEach((bloc, cles) -> {
            Object valeur = charge.get(bloc);
            if (!(valeur instanceof Map<?, ?> m)) {
                cles.forEach(c -> manquantes.add(bloc + "." + c + "   (bloc absent)"));
                return;
            }
            cles.stream().filter(c -> !m.containsKey(c))
                    .forEach(c -> manquantes.add(bloc + "." + c));
        });

        // Blocs-liste : associes[].nom, gerants[].cin, signataires[].qualite, …
        contrat.listes().forEach((liste, cles) -> {
            Object valeur = charge.get(liste);
            if (!(valeur instanceof List<?> l) || l.isEmpty()) {
                cles.forEach(c -> manquantes.add(liste + "[]." + c + "   (liste absente ou vide)"));
                return;
            }
            Set<String> presentes = new TreeSet<>();
            for (Object o : l) {
                if (o instanceof Map<?, ?> occurrence) {
                    occurrence.keySet().forEach(k -> presentes.add(String.valueOf(k)));
                }
            }
            cles.stream().filter(c -> !presentes.contains(c))
                    .forEach(c -> manquantes.add(liste + "[]." + c));
        });

        // Scalaires lus à la racine de la charge utile.
        contrat.racinesScalaires().stream()
                .filter(r -> !charge.containsKey(r))
                .forEach(manquantes::add);

        assertThat(manquantes)
                .describedAs("""
                        %d clé(s) du contrat ne sont pas produites par \
                        ConstructeurChargeUtileCreation. Chacune est une valeur qui \
                        n'atteindra AUCUN document — exactement le défaut que le lot C \
                        corrige. Contrat relevé : %d chemins sur les trois résolveurs.
                        %s""",
                        manquantes.size(), contrat.chemins().size(),
                        String.join("\n  ", manquantes))
                .isEmpty();
    }

    @Test
    @DisplayName("La garde : la charge utile est profondément non modifiable")
    void laChargeUtileNeSeCorrigePas() {
        Map<String, Object> charge =
                ConstructeurChargeUtileCreation.construire(magasinCompletDuCorpus());

        assertThatThrownBy(() -> charge.put("societe", Map.of()))
                .describedAs("la racine de la charge utile doit refuser l'écriture")
                .isInstanceOf(UnsupportedOperationException.class);

        // Le premier niveau ne suffit pas : c'est DANS les blocs que les écrans
        // complétaient la charge utile chacun à sa façon.
        for (Map.Entry<String, Object> e : charge.entrySet()) {
            if (e.getValue() instanceof Map<?, ?> bloc) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) bloc;
                assertThatThrownBy(() -> m.put("intrus", "valeur"))
                        .describedAs("le bloc « %s » doit refuser l'écriture", e.getKey())
                        .isInstanceOf(UnsupportedOperationException.class);
            }
            if (e.getValue() instanceof List<?> liste && !liste.isEmpty()) {
                @SuppressWarnings("unchecked")
                List<Object> l = (List<Object>) liste;
                assertThatThrownBy(() -> l.add("intrus"))
                        .describedAs("la liste « %s » doit refuser l'écriture", e.getKey())
                        .isInstanceOf(UnsupportedOperationException.class);
                if (liste.get(0) instanceof Map<?, ?> occurrence) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> o = (Map<String, Object>) occurrence;
                    assertThatThrownBy(() -> o.put("intrus", "valeur"))
                            .describedAs("une occurrence de « %s » doit refuser l'écriture", e.getKey())
                            .isInstanceOf(UnsupportedOperationException.class);
                }
            }
        }
    }

    // ------------------------------------------------------------------

    /**
     * Un magasin peuplé de TOUTES les variables du corpus de création, lues au
     * manifeste — l'entrée la plus favorable possible au constructeur.
     *
     * <p>Une clé manquante dans la charge utile ne peut donc pas s'expliquer par
     * une entrée pauvre : c'est le constructeur qui ne la produit pas.
     */
    private static Map<String, Object> magasinCompletDuCorpus() {
        JsonNode manifeste = lireJson("ai-service/src/main/resources/templates/v2/manifest.json");
        Set<String> codesCreation = codesDuCorpusCreation();

        Map<String, Object> magasin = new LinkedHashMap<>();
        Map<String, Map<String, Object>> occurrenceParBoucle = new LinkedHashMap<>();

        for (JsonNode tpl : manifeste.path("templates")) {
            if (!codesCreation.contains(tpl.path("code").asText())) continue;
            for (JsonNode v : tpl.path("variables")) {
                magasin.putIfAbsent(v.asText(), "valeur-" + v.asText());
            }
            for (JsonNode bloc : tpl.path("blocks")) {
                String nom = bloc.path("name").asText();
                Map<String, Object> occurrence =
                        occurrenceParBoucle.computeIfAbsent(nom, k -> new LinkedHashMap<>());
                for (JsonNode v : bloc.path("variables")) {
                    occurrence.putIfAbsent(v.asText(), "valeur-" + v.asText());
                }
            }
        }
        occurrenceParBoucle.forEach((nom, occurrence) -> magasin.put(nom, List.of(occurrence)));

        if (magasin.size() < 100) {
            throw new IllegalStateException(
                    "Magasin de test trop pauvre (" + magasin.size() + " entrées) : le "
                            + "manifeste n'a pas été lu correctement. Un contrôle de contrat "
                            + "nourri d'une entrée vide passerait pour de mauvaises raisons.");
        }
        return magasin;
    }

    /** Les 23 codes du corpus de création, lus au catalogue généré. */
    private static Set<String> codesDuCorpusCreation() {
        JsonNode catalogue =
                lireJson("ai-service/src/main/resources/templates/v2/creation-champs.json");
        Set<String> codes = new TreeSet<>();
        for (JsonNode d : catalogue.path("documents")) codes.add(d.path("code").asText());
        if (codes.isEmpty()) {
            throw new IllegalStateException("Catalogue des documents de création illisible.");
        }
        return codes;
    }

    private static JsonNode lireJson(String cheminRelatifDepuisBackend) {
        Path p = localiser(cheminRelatifDepuisBackend);
        try {
            return JSON.readTree(Files.readString(p, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path localiser(String relatif) {
        Path courant = Path.of("").toAbsolutePath();
        for (Path p = courant; p != null; p = p.getParent()) {
            Path direct = p.resolve(relatif);
            if (Files.isRegularFile(direct)) return direct;
            Path viaBackend = p.resolve("backend-java").resolve(relatif);
            if (Files.isRegularFile(viaBackend)) return viaBackend;
        }
        throw new IllegalStateException(
                "Introuvable depuis " + courant + " : " + relatif
                        + " — un contrôle de contrat qui ne trouve pas sa source ne doit "
                        + "JAMAIS passer en silence.");
    }
}
