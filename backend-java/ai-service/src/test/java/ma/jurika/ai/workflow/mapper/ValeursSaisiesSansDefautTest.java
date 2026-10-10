package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.document.GenerationRefuseeException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L3 (D6) : aucune valeur par defaut silencieuse.
 *
 * <p>Vote, quorum et presence sont saisis : absents, rien n'est suppose (la variable reste
 * vide et la generation la nomme, ou le mapper refuse en nommant la donnee). Les valeurs
 * d'usage (nombre d'originaux, heure, duree de 99 ans, mandat du CAC, piece CIN) sont
 * proposees a l'ecran et ne sont transmises qu'une fois confirmees : le mapper ne les
 * invente plus.
 */
class ValeursSaisiesSansDefautTest {

    private static Map<String, Object> seance() {
        return new LinkedHashMap<>(Map.of("type", "extraordinaire", "date", "2026-05-15",
                "heure", "10H00", "lieu", "SIEGE SOCIAL",
                "presidentNom", "M. Ahmed Alaoui", "presidentQualite", "gerant"));
    }

    private static Map<String, Object> societe() {
        return Map.of("denomination", "PARACOSME", "formeJuridique", "SARL", "capitalChiffres", 100_000,
                "siegeSocial", "12 RUE DES FOULES, CASABLANCA", "nombreParts", 1_000);
    }

    private static Map<String, Object> associe(String presence) {
        Map<String, Object> a = new LinkedHashMap<>(Map.of("civilite", "M.", "prenom", "Ahmed", "nom", "Alaoui",
                "nombreParts", 1_000));
        if (presence != null) a.put("presence", presence);
        return a;
    }

    @Test
    void quorum_absent_la_modification_est_refusee_et_nommee() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("formeJuridique", "SARL");
        p.put("societe", societe());
        p.put("seance", seance());
        p.put("associes", List.of(associe("présent")));
        p.put("resolutions", List.of(Map.of("type", "modification_denomination", "nouvelleDenomination", "NOVA")));

        assertThatThrownBy(() -> ModificationDirecteurMapper.pvVars("PV_MODIFICATION_SARL", p))
                .isInstanceOf(GenerationRefuseeException.class)
                .satisfies(e -> assertThat(((GenerationRefuseeException) e).donnees())
                        .extracting(GenerationRefuseeException.DonneeManquante::variable)
                        .contains("QUORUM_ATTEINT"));
    }

    @Test
    void associe_unique_pas_de_quorum_a_saisir() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("formeJuridique", "SARL_AU");
        p.put("associeUnique", true);
        p.put("societe", societe());
        p.put("seance", seance());
        p.put("associes", List.of(associe(null)));
        p.put("resolutions", List.of(Map.of("type", "modification_denomination", "nouvelleDenomination", "NOVA")));

        // Aucun refus : le PV de l'associe unique n'imprime pas de quorum.
        assertThat(String.valueOf(ModificationDirecteurMapper.pvVars("PV_MODIFICATION_SARL_AU", p)
                .getOrDefault("QUORUM_ATTEINT", ""))).isEmpty();
    }

    @Test
    void presence_inconnue_le_pv_de_seance_est_refuse() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("societe", societe());
        p.put("seance", seance());
        p.put("associes", List.of(associe(null)));

        assertThatThrownBy(() -> SeancePvVarsBuilder.build("PV_IRREGULARITE_CONVOCATION_SARL", p))
                .isInstanceOf(GenerationRefuseeException.class)
                .hasMessageContaining("Alaoui");
    }

    @Test
    @SuppressWarnings("unchecked")
    void vote_absent_l_approbation_n_imprime_ni_adoptee_ni_voix() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("societe", societe());
        p.put("seance", seance());
        p.put("associes", List.of(associe("présent")));
        p.put("approbation", Map.of("exerciceClosDate", "2025-12-31", "resultatType", "bénéfice",
                "resultatNet", 1000L));

        Map<String, Object> v = new ApprobationComptesMapper(new AnnualReportMapper())
                .map("PV_APPROBATION_COMPTES_SARL", p);

        List<Map<String, Object>> res = (List<Map<String, Object>>) v.get("RESOLUTIONS");
        assertThat(res).isNotEmpty();
        assertThat(res).allSatisfy(r -> {
            assertThat(String.valueOf(r.getOrDefault("RESOLUTION_RESULTAT", ""))).isBlank();
            assertThat(String.valueOf(r.getOrDefault("RESOLUTION_VOIX_POUR", ""))).isBlank();
        });
    }

    @Test
    void valeurs_d_usage_non_confirmees_ne_sont_pas_inventees() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("societe", Map.of("denomination", "NOVA", "formeJuridique", "SARL", "capitalChiffres", 100_000));
        p.put("associes", List.of(Map.of("civilite", "M.", "prenom", "Ahmed", "nom", "Alaoui",
                "pieceNumero", "BE123456")));

        Map<String, Object> v = CreationDirecteurVarsBuilder.build("STATUTS_SARL", p);

        for (String cle : List.of("DUREE_SOCIETE", "NOMBRE_ORIGINAUX", "HEURE_ACTE", "EXERCICE_DEBUT", "EXERCICE_FIN")) {
            assertThat(String.valueOf(v.getOrDefault(cle, ""))).as(cle).isBlank();
        }
    }
}
