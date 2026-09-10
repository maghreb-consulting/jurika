package ma.jurika.ai.workflow.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Durée de la gérance — <b>scopée par document</b> (2026-08-18).
 *
 * <h2>Le défaut corrigé</h2>
 * La boucle {@code GERANTS} est filtrée par modèle depuis le fix A10 : l'acte de
 * nomination ne liste que les gérants NON statutaires. Mais {@code $DUREE_GERANCE},
 * lui, restait un agrégat GLOBAL. L'acte pouvait donc annoncer
 * « pour une durée de 3 année(s) pour M. ALAOUI et illimitée pour Mme BENJELLOUN : »
 * puis ne lister que Mme BENJELLOUN — une durée attribuée à quelqu'un qui ne figure
 * pas dans l'acte.
 *
 * <h2>La règle</h2>
 * Chaque document porte la durée des SEULS gérants qu'il nomme :
 * statuts → tous ; acte de nomination → les non statutaires. Mandats identiques →
 * la valeur commune ; mandats divergents → énumération nominative.
 *
 * <h2>Lot A (2026-09-10) — pourquoi des codes qui n'existent plus</h2>
 * Le test appelle {@code CreationDirecteurVarsBuilder} DIRECTEMENT, sans passer
 * par le manifeste. Les chaînes {@code STATUTS_SARL_DIRECTEUR} et
 * {@code ACTE_NOMINATION_GERANT_DIRECTEUR} ne sont plus des codes de modèle — ce
 * sont les arguments que le builder lit pour savoir quel document il alimente.
 * La branche « statuts » sert encore la refonte MODIFICATION ; la branche
 * « acte », elle, n'a plus d'appelant tant que le lot B n'a pas recâblé la
 * création. La règle qu'elles protègent, elle, reste vraie.
 */
class DureeGeranceScopeeTest {

    private static Map<String, Object> gerant(String prenom, String nom, boolean statutaire,
                                              String dureeMandat) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("typePersonne", "PHYSIQUE");
        g.put("civilite", "M");
        g.put("prenom", prenom);
        g.put("nom", nom);
        g.put("pieceNumero", "BE111111");
        g.put("adresse", "45 BD ZERKTOUNI");
        g.put("isStatutaire", statutaire);
        g.put("statutaire", statutaire);
        g.put("dureeMandat", dureeMandat);
        return g;
    }

    private static Map<String, Object> payload(List<Map<String, Object>> gerants,
                                               String dureeAgregeeFront) {
        Map<String, Object> societe = new LinkedHashMap<>();
        societe.put("denomination", "NOVA INDUSTRIE");
        societe.put("capitalChiffres", 100_000);
        societe.put("siegeSocial", "12 RUE DES FOULES, CASABLANCA");
        societe.put("dureeGerance", dureeAgregeeFront);
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("societe", societe);
        p.put("gerants", gerants);
        p.put("associes", List.of(Map.of("typePersonne", "PHYSIQUE", "civilite", "M",
                "prenom", "Ahmed", "nom", "ALAOUI", "nombreParts", 1000)));
        return p;
    }

    private static String duree(String templateCode, Map<String, Object> payload) {
        return String.valueOf(
                CreationDirecteurVarsBuilder.build(templateCode, payload).get("DUREE_GERANCE"));
    }

    @Test
    @DisplayName("Mandats identiques : la valeur commune, dans les deux documents")
    void mandatsIdentiques() {
        Map<String, Object> p = payload(List.of(
                gerant("Ahmed", "ALAOUI", true, "3 année(s)"),
                gerant("Salma", "BENJELLOUN", false, "3 année(s)")), "3 année(s)");

        assertThat(duree("STATUTS_SARL_DIRECTEUR", p)).isEqualTo("3 année(s)");
        assertThat(duree("ACTE_NOMINATION_GERANT_DIRECTEUR", p)).isEqualTo("3 année(s)");
    }

    @Test
    @DisplayName("Mandats divergents : l'ACTE ne porte QUE la durée du gérant qu'il nomme")
    void acteNePorteQueSonGroupe() {
        // Le statutaire est nommé par les statuts, le non statutaire par l'acte.
        Map<String, Object> p = payload(List.of(
                gerant("Ahmed", "ALAOUI", true, "3 année(s)"),
                gerant("Salma", "BENJELLOUN", false, "illimitée (jusqu'à révocation)")),
                "3 année(s) pour M. Ahmed ALAOUI et illimitée (jusqu'à révocation) pour M. Salma BENJELLOUN");

        // C'ÉTAIT LE DÉFAUT : l'acte annonçait les DEUX durées en ne listant qu'un gérant.
        String acte = duree("ACTE_NOMINATION_GERANT_DIRECTEUR", p);
        // « illimitée » est normalisée en formule lisible après « pour une durée de »
        // (fix A10) : c'est le rendu attendu, pas le libellé brut de la saisie.
        assertThat(acte)
                .describedAs("l'acte ne nomme que le gérant non statutaire")
                .isEqualTo("la société, soit jusqu'à révocation")
                .doesNotContain("ALAOUI");
    }

    @Test
    @DisplayName("Mandats divergents dans un MÊME groupe : énumération nominative")
    void memeGroupeDivergent() {
        // Deux non statutaires aux mandats distincts : l'acte les nomme tous les deux,
        // il doit donc attribuer chaque durée — sans quoi l'une serait fausse.
        Map<String, Object> p = payload(List.of(
                gerant("Ahmed", "ALAOUI", false, "3 année(s)"),
                gerant("Karim", "BENNANI", false, "6 année(s)")), "");

        assertThat(duree("ACTE_NOMINATION_GERANT_DIRECTEUR", p))
                .isEqualTo("3 année(s) pour M. Ahmed ALAOUI et 6 année(s) pour M. Karim BENNANI");
    }

    @Test
    @DisplayName("Payload antérieur (aucun mandat par gérant) : le repli du front est conservé")
    void repliPayloadAncien() {
        Map<String, Object> p = payload(List.of(
                gerant("Ahmed", "ALAOUI", true, ""),
                gerant("Salma", "BENJELLOUN", true, "")), "99 années");

        assertThat(duree("STATUTS_SARL_DIRECTEUR", p)).isEqualTo("99 années");
    }

    @Test
    @DisplayName("« illimitée » reste grammatical après « pour une durée de »")
    void illimiteeResteGrammaticale() {
        Map<String, Object> p = payload(List.of(
                gerant("Ahmed", "ALAOUI", true, "illimitée")), "illimitée");

        // Fix A10 : « pour une durée de illimitée » était le rendu d'origine.
        String d = duree("STATUTS_SARL_DIRECTEUR", p);
        assertThat("nommé premier gérant, pour une durée de " + d)
                .isEqualTo("nommé premier gérant, pour une durée de la société, soit jusqu'à révocation");
    }
}
