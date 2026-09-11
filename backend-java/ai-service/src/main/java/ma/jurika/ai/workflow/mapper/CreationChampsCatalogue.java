package ma.jurika.ai.workflow.mapper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Le catalogue des champs du parcours de création — <b>fichier généré</b>,
 * {@code templates/v2/creation-champs.json}, produit par
 * {@code scripts/lotB/derive-champs-creation.mjs}.
 *
 * <p>Il dit, pour les 23 modèles du corpus du 9 septembre :
 * <ul>
 *   <li>quel document est produit à quelle ligne du parcours, sous quelle
 *       condition, et lequel est coché par défaut ;</li>
 *   <li>quelle variable est <b>saisie</b> — et par quel(s) document(s) elle est
 *       consommée. C'est ce qui permet la règle du parcours : un champ
 *       n'apparaît que si le document qui le consomme est retenu ;</li>
 *   <li>quelle variable est <b>dérivable</b> — aucun champ n'est ouvert pour
 *       elle, {@link CreationCorpusVarsBuilder} la calcule ;</li>
 *   <li>quelle variable est <b>sans source</b> — aucun champ, aucune valeur
 *       inventée, et un report au cabinet.</li>
 * </ul>
 *
 * <p>Le catalogue est <b>dérivé</b> du parcours, du manifeste et de l'analyse
 * d'écart du lot A, jamais recopié : le cabinet livrera d'autres versions du
 * corpus, et une recopie ne se resynchronise pas.
 *
 * <p>Chargé une fois, à la demande, et mis en cache : c'est une donnée de
 * référence immuable pendant la vie du processus.
 */
public final class CreationChampsCatalogue {

    private static final String RESOURCE = "templates/v2/creation-champs.json";

    private static volatile Catalogue cache;

    private CreationChampsCatalogue() {}

    public static Catalogue get() {
        Catalogue local = cache;
        if (local == null) {
            synchronized (CreationChampsCatalogue.class) {
                local = cache;
                if (local == null) {
                    cache = local = charger();
                }
            }
        }
        return local;
    }

    private static Catalogue charger() {
        try (InputStream in = CreationChampsCatalogue.class.getClassLoader()
                .getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Catalogue des champs de creation introuvable au classpath : " + RESOURCE
                                + ". Le regenerer avec "
                                + "node scripts/lotB/derive-champs-creation.mjs");
            }
            Catalogue c = new ObjectMapper().readValue(in, Catalogue.class);
            if (c.documents() == null || c.documents().isEmpty()) {
                throw new IllegalStateException("Catalogue vide : " + RESOURCE);
            }
            return c;
        } catch (IOException e) {
            throw new IllegalStateException("Lecture impossible de " + RESOURCE, e);
        }
    }

    // =====================================================================
    //  Modele
    // =====================================================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Catalogue(
            @JsonProperty("genere_le") String genereLe,
            List<Document> documents,
            @JsonProperty("choix_statut_2") List<Choix> choixStatut2,
            List<Champ> champs,
            List<Boucle> boucles,
            @JsonProperty("sans_source") List<SansSource> sansSource,
            List<Derivable> derivables) {

        /** Les codes des 23 modeles du corpus, dans l'ordre du parcours. */
        public Set<String> codes() {
            return documents.stream().map(Document::code)
                    .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        }

        /** Les champs de saisie qu'un modele donne consomme, boucles comprises. */
        public List<Champ> champsDe(String code) {
            return champs.stream().filter(c -> c.documents().contains(code)).toList();
        }

        /** Les boucles qu'un modele donne porte. */
        public List<Boucle> bouclesDe(String code) {
            return boucles.stream().filter(b -> b.documents().contains(code)).toList();
        }

        /** Index boucle -> ses champs, pour l'expansion. */
        public Map<String, List<Champ>> champsParBoucle() {
            Map<String, List<Champ>> out = new LinkedHashMap<>();
            for (Champ c : champs) {
                if (c.boucle() == null) continue;
                out.computeIfAbsent(c.boucle(), k -> new java.util.ArrayList<>()).add(c);
            }
            return out;
        }

        /** Les variables sans source qui concernent ce modele. */
        public List<String> sansSourceDe(String code) {
            return sansSource.stream()
                    .filter(s -> s.documents().contains(code))
                    .map(SansSource::variable).toList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(
            String code,
            @JsonProperty("ligneGeneration") Integer ligneGeneration,
            @JsonProperty("statutGeneration") String statutGeneration,
            String libelle,
            String condition,
            @JsonProperty("cochePar_defaut") boolean cocheParDefaut,
            String emploi,
            List<Reprise> reprises,
            String note) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Reprise(Integer ligne, String statut, String emploi, String libelle) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choix(
            Integer ligne,
            String libelle,
            @JsonProperty("documentProduit") String documentProduit,
            String condition,
            @JsonProperty("cochePar_defaut") boolean cocheParDefaut,
            List<String> codes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Champ(
            String variable,
            String cle,
            String label,
            String type,
            List<String> options,
            String aide,
            String section,
            List<String> documents,
            String boucle) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Boucle(String nom, String label, List<String> documents, List<String> champs) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SansSource(String variable, List<String> documents, String section, String aide) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Derivable(String variable, List<String> documents) {}
}
