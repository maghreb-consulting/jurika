package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.document.GenerationRefuseeException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L4b (E7, D5) : mentions du PV des decisions de l'associe unique (approbation des
 * comptes, modele du corpus). Saisies transmises, derivations exactes, rien de suppose ; la
 * presence d'un commissaire aux comptes est exigee (avant : "non" par defaut).
 */
class ApprobationMentionsAuTest {

    private static Map<String, Object> approbation() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("dossierReference", "T-2026-00042");
        a.put("registrePvNumero", "12");
        a.put("registrePvFeuillet", "34");
        a.put("nombreOriginaux", "quatre");
        return a;
    }

    @Test
    void saisies_et_derivations_du_pv_de_l_associe_unique() {
        Map<String, Object> v = new LinkedHashMap<>();
        ApprobationComptesMapper.mentionsAssocieUnique(v, approbation(), Map.of(
                "seance", Map.of("date", "2026-06-15"),
                "gerants", List.of(Map.of("civilite", "Mme", "prenom", "Salma", "nom", "BENNANI"))));
        assertThat(v).containsEntry("DOSSIER_REFERENCE", "T-2026-00042")
                .containsEntry("REGISTRE_PV_NUMERO", "12").containsEntry("REGISTRE_PV_FEUILLET", "34")
                .containsEntry("NOMBRE_ORIGINAUX", "quatre")
                .containsEntry("DEPOT_GREFFE_DATE_LIMITE", "15/07/2026")
                .containsEntry("GERANT_QUALITE", "g\u00e9rante");
        assertThat(String.valueOf(v.get("ASSEMBLEE_ANNEE")).toLowerCase()).isEqualTo("deux mille vingt-six");
    }

    @Test
    void rien_n_est_suppose_quand_la_donnee_manque() {
        Map<String, Object> v = new LinkedHashMap<>();
        ApprobationComptesMapper.mentionsAssocieUnique(v, Map.of(), Map.of(
                "gerants", List.of(Map.of("prenom", "Sam", "nom", "IDRISSI"))));
        assertThat(v).doesNotContainKeys("DOSSIER_REFERENCE", "NOMBRE_ORIGINAUX", "ASSEMBLEE_ANNEE",
                "DEPOT_GREFFE_DATE_LIMITE", "GERANT_QUALITE");
    }

    @Test
    void la_presence_du_commissaire_aux_comptes_est_exigee() {
        Map<String, Object> a = approbation();
        a.put("quitusGerance", "oui");
        a.put("conventionsReglementees", "non");
        a.put("dividendeDistribue", "non");
        assertThatThrownBy(() -> new ApprobationComptesMapper(new AnnualReportMapper())
                .map("PV_APPROBATION_COMPTES_SARL", Map.of("approbation", a)))
                .isInstanceOfSatisfying(GenerationRefuseeException.class, e -> assertThat(e.donnees())
                        .extracting(GenerationRefuseeException.DonneeManquante::variable)
                        .containsExactly("COMMISSAIRE_COMPTES_PRESENT"));
    }
}
