package ma.jurika.ai.workflow.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OÙ N'EST PAS LE DÉFAUT DES SIGNATAIRES — test de caractérisation.
 *
 * <p><b>Ce test passe aujourd'hui, et c'est son objet.</b> Il établit que le
 * résolveur n'a jamais été en cause : quand on lui remet la liste des signataires
 * désignés, il l'honore — pour le bloc de signature de l'article 15 <i>et</i>
 * pour {@code $SIGNATAIRE_ACCORD}, l'accord grammatical des phrases qui
 * l'encadrent.
 *
 * <pre>
 *   List&lt;Map&lt;String, Object&gt;&gt; effSignataires =
 *           (signataires != null &amp;&amp; !signataires.isEmpty()) ? signataires : gerants;
 *   put(v, "SIGNATAIRE_ACCORD", accord(effSignataires));
 * </pre>
 *
 * <p><b>Le défaut est en amont</b> : le constructeur de charge utile vivant
 * (celui de {@code Step7Generation}) ne transmet jamais {@code signataires}. Le
 * résolveur trouve une liste vide, applique son repli — « à défaut, chaque gérant
 * est signataire, qualité <i>gérant</i> » — et produit un acte complet, cohérent
 * et faux. Aucun blanc n'en sort.
 *
 * <p><b>La garde qui compte est donc ailleurs</b>, sur le constructeur unique :
 * {@code ContratChargeUtileCreationTest} exige {@code signataires[].nom} et
 * {@code signataires[].qualite} parmi les 193 chemins du contrat. Un test écrit
 * ici, qui remet lui-même les signataires au résolveur, passerait à vide et ne
 * regarderait pas le chemin vivant.
 *
 * <p><b>Le témoin est réel.</b> Le dossier {@code 627c0190-5ffe-4249-ab53-…} de
 * la base de développement — seul des 145 dossiers de création à renseigner le
 * bloc — porte <b>un</b> signataire désigné, « EL AMRANI Youssef », qualité
 * « Gerant statutaire », et <b>deux</b> gérants : EL AMRANI et BENNANI. Ses
 * statuts sortent donc aujourd'hui signés par deux personnes, en qualité de
 * « gérant », avec un paragraphe accordé au pluriel.
 */
class SignatairesDesignesNonSubstituesTest {

    private final CreationSarlMapper mapper = new CreationSarlMapper();

    @Test
    @DisplayName("Caractérisation : remis au résolveur, le signataire désigné est cité, et lui seul")
    void leResolveurHonoreLesSignatairesQuOnLuiRemet() {
        Map<String, Object> variables = mapper.map("STATUTS_SARL", payload(signatairesDesignes()));
        List<Map<String, Object>> signataires = boucleSignataires(variables);

        assertThat(signataires).hasSize(1);
        assertThat(signataires.get(0))
                .containsEntry("SIGNATAIRE_NOM", "EL AMRANI Youssef")
                .containsEntry("SIGNATAIRE_QUALITE", "Gerant statutaire");
    }

    @Test
    @DisplayName("Caractérisation : l'accord grammatical suit la liste remise")
    void lAccordSuitLaListeRemise() {
        Map<String, Object> variables = mapper.map("STATUTS_SARL", payload(signatairesDesignes()));
        assertThat(variables.get("SIGNATAIRE_ACCORD")).isEqualTo("un masculin");
    }

    @Test
    @DisplayName("LE DÉFAUT : sans la liste, deux gérants signent et le paragraphe passe au pluriel")
    void sansLaListeLesGerantsSeSubstituent() {
        // Exactement ce que produit le constructeur vivant : pas de clé « signataires ».
        Map<String, Object> variables = mapper.map("STATUTS_SARL", payload(null));
        List<Map<String, Object>> signataires = boucleSignataires(variables);

        assertThat(signataires)
                .describedAs("""
                        Le repli est JUSTE quand personne n'a été désigné, et c'est le \
                        comportement conservé. Il devient faux quand l'employé a désigné \
                        quelqu'un et que la charge utile ne le transporte pas.""")
                .hasSize(2);
        assertThat(signataires.get(0)).containsEntry("SIGNATAIRE_QUALITE", "gérant");
        assertThat(variables.get("SIGNATAIRE_ACCORD"))
                .describedAs("deux gérants => pluriel, là où un signataire désigné "
                        + "aurait donné « un masculin »")
                .isEqualTo("plusieurs");
    }

    // ------------------------------------------------------------------

    private static Map<String, Object> payload(List<Map<String, Object>> signataires) {
        Map<String, Object> societe = new LinkedHashMap<>();
        societe.put("denomination", "TEMOIN LOT C SARL");
        societe.put("formeJuridique", "SARL");
        societe.put("capitalChiffres", 100000);
        societe.put("nombreParts", 1000);
        societe.put("valeurNominalePart", 100);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("societe", societe);
        p.put("gerants", gerantsDuTemoin());
        p.put("associes", List.of());
        if (signataires != null) p.put("signataires", signataires);
        return p;
    }

    private static List<Map<String, Object>> gerantsDuTemoin() {
        List<Map<String, Object>> gerants = new ArrayList<>();
        gerants.add(gerant("Youssef", "EL AMRANI"));
        gerants.add(gerant("Karim", "BENNANI"));
        return gerants;
    }

    private static Map<String, Object> gerant(String prenom, String nom) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("civilite", "M");
        g.put("prenom", prenom);
        g.put("nom", nom);
        g.put("typePersonne", "PHYSIQUE");
        g.put("isStatutaire", Boolean.TRUE);
        return g;
    }

    private static List<Map<String, Object>> signatairesDesignes() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("nom", "EL AMRANI Youssef");
        s.put("qualite", "Gerant statutaire");
        return List.of(s);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> boucleSignataires(Map<String, Object> variables) {
        Object o = variables.get("SIGNATAIRES");
        return o instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }
}
