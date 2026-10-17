package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.document.GenerationRefuseeException;
import ma.jurika.ai.workflow.identity.RepriseFicheSociete;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static ma.jurika.ai.workflow.mapper.DissolutionMapperTest.bloc;
import static ma.jurika.ai.workflow.mapper.DissolutionMapperTest.fixture;
import static ma.jurika.ai.workflow.mapper.DissolutionMapperTest.txt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L6b (E19, decision du 2026-10-17) : le resultat de la liquidation (boni, mali ou nul) a
 * UNE seule source, le rapport final du liquidateur (actif realise - passif regle - frais -
 * impot, remboursement du capital a concurrence du disponible). Le PV de cloture et le rapport
 * l'impriment a l'identique ; l'avis de cloture ne le publie pas et ne porte plus de "boni"
 * suppose (actif - passif) ; un sens ou un montant envoye par l'ecran est ignore.
 */
class ResultatLiquidationUniqueTest {

    private final LiquidationMapper mapper = new LiquidationMapper();

    /**
     * Charge utile de l'ecran : comptes de l'etape 1, frais du PV, lignes du rapport final, et
     * le "resultat" que l'ecran envoyait (actif - passif), faux des que frais ou capital comptent.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(long actif, long passif, long frais) {
        Map<String, Object> p = fixture("liquidation.json");
        Map<String, Object> c = bloc(p, "cloture");
        c.put("actifRealise", actif);
        c.put("passifRegle", passif);
        c.put("frais", frais);
        long solde = actif - passif;
        c.put("resultatSens", "boni");
        c.put("resultatType", "boni");
        c.put("resultatMontant", solde);
        c.put("boniExiste", "oui");
        c.put("boniMontant", solde);
        c.put("retenueAssiette", 25_000);
        c.put("retenueMontant", 3_750);
        Map<String, Object> r = bloc(p, "rapportLiquidateur");
        r.put("realisations", new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("designation", "Fonds de commerce",
                "mode", "cession de gre a gre", "acquereur", "ATLAS DISTRIBUTION SARL", "prix", actif,
                "date", "2026-05-29", "valeurInventaire", actif, "observations", "neant")))));
        r.put("apurements", new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("creancier", "BANQUE POPULAIRE",
                "nature", "emprunt bancaire", "montantAdmis", passif, "montantRegle", passif,
                "dateReglement", "2026-06-02", "modeReglement", "virement bancaire", "observations", "neant")))));
        r.put("frais", new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("nature", "Honoraires de l'expert-comptable",
                "beneficiaire", "CABINET FIDUCIAIRE MAGHREB", "montant", frais, "date", "2026-06-08", "statut", "regle")))));
        return p;
    }

    private Map<String, Object> vars(String code, Map<String, Object> p) {
        return RepriseFicheSociete.completer(mapper.map(code, p), p);
    }

    @Test
    void mali_120000_20000_5000_capital_100000_donne_mali_5000_au_pv_et_au_rapport() {
        Map<String, Object> p = payload(120_000, 20_000, 5_000);
        Map<String, Object> pv = vars(LiquidationMapper.TPL_PV_SARL, p);
        Map<String, Object> rapport = vars(LiquidationMapper.TPL_RAPPORT, p);
        for (Map<String, Object> v : List.of(pv, rapport)) {
            assertThat(v.get("LIQUIDATION_RESULTAT_TYPE")).isEqualTo("mali");
            assertThat(txt(v.get("LIQUIDATION_MALI_CHIFFRES"))).isEqualTo("5 000");
            assertThat(v).doesNotContainKey("LIQUIDATION_BONI_CHIFFRES");
        }
    }

    @Test
    void boni_150000_20000_5000_capital_100000_donne_boni_25000_au_pv_et_au_rapport() {
        Map<String, Object> p = payload(150_000, 20_000, 5_000);
        Map<String, Object> pv = vars(LiquidationMapper.TPL_PV_SARL, p);
        Map<String, Object> rapport = vars(LiquidationMapper.TPL_RAPPORT, p);
        for (Map<String, Object> v : List.of(pv, rapport)) {
            assertThat(v.get("LIQUIDATION_RESULTAT_TYPE")).isEqualTo("boni");
            assertThat(txt(v.get("LIQUIDATION_BONI_CHIFFRES"))).isEqualTo("25 000");
            assertThat(v).doesNotContainKey("LIQUIDATION_MALI_CHIFFRES");
        }
    }

    /** L'avis du corpus ne publie pas le resultat : aucune variable de "boni" = actif - passif. */
    @Test
    void annonce_ne_porte_plus_le_solde_actif_moins_passif_comme_resultat() {
        Map<String, Object> v = vars(LiquidationAnnonceVarsBuilder.TPL_SARL, payload(120_000, 20_000, 5_000));
        assertThat(v).doesNotContainKeys("RESULTAT_LIQUIDATION_TYPE", "BONI_LIQUIDATION_CHIFFRES",
                "MALI_LIQUIDATION_CHIFFRES");
    }

    /** Le PV prend son compte au rapport final : un total de l'etape 1 different est refuse et nomme. */
    @Test
    void pv_refuse_un_actif_de_l_etape_1_different_du_rapport_final() {
        Map<String, Object> p = payload(120_000, 20_000, 5_000);
        bloc(p, "cloture").put("actifRealise", 130_000);
        assertThatThrownBy(() -> vars(LiquidationMapper.TPL_PV_SARL, p))
                .isInstanceOf(GenerationRefuseeException.class)
                .hasMessageContaining("Total des r\u00e9alisations de l'actif");
    }

    /** Sans rapport final, le PV ne calcule pas de resultat : la donnee est nommee. */
    @Test
    void pv_sans_rapport_final_refuse_et_nomme_le_rapport() {
        Map<String, Object> p = payload(120_000, 20_000, 5_000);
        p.remove("rapportLiquidateur");
        assertThatThrownBy(() -> vars(LiquidationMapper.TPL_PV_SARL, p))
                .isInstanceOf(GenerationRefuseeException.class)
                .hasMessageContaining("rapport final");
    }
}
