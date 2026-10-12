package ma.jurika.ai.document;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L4d (E10), decision du directeur du 11 octobre 2026 : l'employe choisit l'emplacement
 * de la clause dans le PV ; numerotation et renvois suivent, sinon la generation est refusee.
 */
class ClausesLibresEmplacementTest {

    private static Map<String, Object> resolution(String numero, String intitule, String texte) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("RESOLUTION_NUMERO", numero);
        r.put("RESOLUTION_INTITULE", intitule);
        r.put("RESOLUTION_TEXTE", texte);
        return r;
    }

    private static Map<String, Object> pv(String texteTroisieme) {
        List<Map<String, Object>> l = new ArrayList<>();
        l.add(resolution("PREMI\u00c8RE R\u00c9SOLUTION", "Approbation des comptes", "L'assembl\u00e9e approuve les comptes."));
        l.add(resolution("DEUXI\u00c8ME R\u00c9SOLUTION", "Affectation du r\u00e9sultat", "Sous r\u00e9serve de l'adoption de la premi\u00e8re r\u00e9solution, ..."));
        l.add(resolution("TROISI\u00c8ME R\u00c9SOLUTION", "Pouvoirs", texteTroisieme));
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("RESOLUTIONS", l);
        return v;
    }

    private static Map<String, Object> clause(String emplacement) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("titre", "Clause libre");
        c.put("texte", "Texte de la clause.");
        c.put("emplacement", emplacement);
        return c;
    }

    @Test
    @SuppressWarnings("unchecked")
    void avant_la_deuxieme_resolution_renumerote_et_met_a_jour_les_renvois() {
        Map<String, Object> v = ClausesLibres.inserer("PV_X", pv("En cons\u00e9quence de la deuxi\u00e8me r\u00e9solution, tous pouvoirs."),
                List.of(clause("AVANT_RESOLUTION:2")));
        List<Map<String, Object>> r = (List<Map<String, Object>>) v.get("RESOLUTIONS");
        assertThat(r).extracting(m -> m.get("RESOLUTION_INTITULE"))
                .containsExactly("Approbation des comptes", "Clause libre", "Affectation du r\u00e9sultat", "Pouvoirs");
        assertThat(r).extracting(m -> m.get("RESOLUTION_NUMERO"))
                .containsExactly("PREMI\u00c8RE R\u00c9SOLUTION", "DEUXI\u00c8ME R\u00c9SOLUTION", "TROISI\u00c8ME R\u00c9SOLUTION", "QUATRI\u00c8ME R\u00c9SOLUTION");
        // La premiere ne bouge pas ; la deuxieme devient la troisieme.
        assertThat(r.get(2).get("RESOLUTION_TEXTE")).isEqualTo("Sous r\u00e9serve de l'adoption de la premi\u00e8re r\u00e9solution, ...");
        assertThat(r.get(3).get("RESOLUTION_TEXTE")).isEqualTo("En cons\u00e9quence de la troisi\u00e8me r\u00e9solution, tous pouvoirs.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void a_la_suite_rien_ne_change_dans_les_resolutions_du_modele() {
        Map<String, Object> v = ClausesLibres.inserer("PV_X", pv("Voir la deuxi\u00e8me r\u00e9solution."),
                List.of(clause("A_LA_SUITE_DES_RESOLUTIONS")));
        List<Map<String, Object>> r = (List<Map<String, Object>>) v.get("RESOLUTIONS");
        assertThat(r).hasSize(4);
        assertThat(r.get(2).get("RESOLUTION_TEXTE")).isEqualTo("Voir la deuxi\u00e8me r\u00e9solution.");
        assertThat(r.get(3).get("RESOLUTION_NUMERO")).isEqualTo("QUATRI\u00c8ME R\u00c9SOLUTION");
    }

    @Test
    void un_renvoi_en_chiffres_decale_bloque_avec_message() {
        assertThatThrownBy(() -> ClausesLibres.inserer("PV_X", pv("Conform\u00e9ment \u00e0 la r\u00e9solution n\u00b0 2, ..."),
                List.of(clause("AVANT_RESOLUTION:1"))))
                .isInstanceOf(ClauseLibreImpossibleException.class)
                .hasMessageContaining("r\u00e9solution n\u00b0 2");
    }

    @Test
    void un_emplacement_hors_des_resolutions_bloque_avec_message() {
        assertThatThrownBy(() -> ClausesLibres.inserer("PV_X", pv("x"), List.of(clause("AVANT_RESOLUTION:9"))))
                .isInstanceOf(ClauseLibreImpossibleException.class)
                .hasMessageContaining("1 \u00e0 3");
    }
}
