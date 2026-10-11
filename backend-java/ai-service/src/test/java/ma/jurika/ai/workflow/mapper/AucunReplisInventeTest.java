package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.document.GenerationRefuseeException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L4a (E2) : aucune valeur pre-remplie silencieuse dans les mappers. Une donnee non
 * saisie n'est plus remplacee par un usage ("6 et 7", "demission", "duree illimitee",
 * "mensuelle", "marocaine", "exercees a titre gratuit", "volontaire"...) : elle reste
 * vide et la generation la nomme ; un drapeau qui pilote une branche du modele (quitus,
 * cessionnaire tiers) refuse la generation au lieu de prendre une branche en silence.
 */
class AucunReplisInventeTest {

    private static Map<String, Object> seance() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("formeJuridique", "SARL");
        p.put("societe", Map.of("denomination", "PARACOSME", "formeJuridique", "SARL"));
        p.put("seance", Map.of("date", "2026-05-15", "quorumAtteint", "oui"));
        p.put("quorumAtteint", "oui");
        p.put("associes", List.of(Map.of("civilite", "M.", "prenom", "Ahmed", "nom", "Alaoui",
                "nombreParts", 1000, "presence", "présent")));
        return p;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolution(Map<String, Object> vars) {
        return ((List<Map<String, Object>>) vars.get("RESOLUTIONS")).get(0);
    }

    @Test
    void articles_motif_duree_periodicite_ne_sont_plus_supposes() {
        for (Map<String, Object> r : List.<Map<String, Object>>of(
                Map.of("type", "nomination_gerant", "gerantNom", "M. Karim TAZI", "gerantSortantNom", "M. X"),
                Map.of("type", "renouvellement_gerant", "gerantNom", "M. Karim TAZI"),
                Map.of("type", "remuneration_gerant", "gerantNom", "M. Karim TAZI"),
                Map.of("type", "agrement_transmission", "cessionnaireNom", "M. Y", "cessionnaireTiers", "non"))) {
            Map<String, Object> p = seance();
            p.put("resolutions", List.of(r));
            Map<String, Object> it = resolution(ModificationDirecteurMapper.pvVars("PV_MODIFICATION_SARL", p));
            assertThat(it.toString()).doesNotContain("démission", "durée illimitée", "mensuelle",
                    "6 et 7", "une succession");
        }
    }

    @Test
    void cessionnaire_tiers_absent_refuse_et_nomme() {
        Map<String, Object> p = seance();
        p.put("resolutions", List.of(Map.of("type", "agrement_cession", "cessionnaireNom", "M. Y")));
        assertThatThrownBy(() -> ModificationDirecteurMapper.pvVars("PV_MODIFICATION_SARL", p))
                .isInstanceOf(GenerationRefuseeException.class).hasMessageContaining("tiers");
    }

    @Test
    void nationalite_des_personnes_jamais_supposee() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("societe", Map.of("denomination", "NOVA", "formeJuridique", "SARL"));
        p.put("associes", List.of(Map.of("nom", "ALAOUI", "prenom", "Ahmed")));
        p.put("gerants", List.of(Map.of("nom", "ALAOUI", "prenom", "Ahmed")));
        Map<String, Object> v = CreationDirecteurVarsBuilder.build("STATUTS_SARL", p);
        assertThat(v.toString()).doesNotContain("marocaine");
    }

    @Test
    void remuneration_du_liquidateur_et_motif_de_dissolution_jamais_supposes() {
        Map<String, Object> p = seance();
        p.put("liquidateur", Map.of("civilite", "M.", "prenom", "Karim", "nom", "TAZI"));
        // Sans nature saisie, la dissolution est refusee (avant : "volontaire" suppose).
        assertThatThrownBy(() -> new DissolutionMapper().map("PV_DISSOLUTION_LIQUIDATION_SARL", p))
                .isInstanceOf(GenerationRefuseeException.class).hasMessageContaining("Nature de la dissolution");
        p.put("dissolution", Map.of("nature", "article 86"));
        Map<String, Object> v = new DissolutionMapper().map("PV_DISSOLUTION_LIQUIDATION_SARL", p);
        assertThat(v).containsEntry("DISSOLUTION_MOTIF", "article 86");
        assertThat(v.toString()).doesNotContain("exercées à titre gratuit").doesNotContain("volontaire");
    }
}
