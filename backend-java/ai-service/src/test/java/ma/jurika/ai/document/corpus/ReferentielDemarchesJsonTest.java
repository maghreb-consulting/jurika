package ma.jurika.ai.document.corpus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L4b (E2) : contrat JSON du referentiel servi a ticket-service. Le meme texte est lu
 * par ReferentielCorpusJsonTest (ticket-service) : un champ renomme d'un cote casse l'autre.
 */
class ReferentielDemarchesJsonTest {

    static final String CONTRAT = "{\"version\":\"CORPUS_T\",\"lignes\":[{\"matrice\":\"M\",\"famille\":\"F\",\"forme\":\"SARL\",\"ordre\":1,\"decision\":\"reduction_capital\",\"sousCondition\":{\"variable\":\"REDUCTION_MOTIF\",\"operateur\":\"EGAL\",\"valeur\":\"retrait_associe\",\"texte\":null},\"statut\":\"GENERATION_DOCUMENTS\",\"libelle\":\"L\",\"organisme\":\"Cabinet\",\"condition\":{\"variable\":\"CAC_EXISTE\",\"operateur\":\"EGAL\",\"valeur\":\"oui\",\"texte\":\"T\"},\"obligatoire\":false,\"delai\":{\"texte\":\"D\",\"valeur\":30,\"unite\":\"JOURS\",\"depuisVariable\":\"X\",\"echeanceVariable\":\"Y\"},\"justificatif\":\"J\",\"modele\":\"PV_AG_SARL\",\"numeroDecision\":21,\"aCompleter\":null,\"completePlateforme\":null}],\"renvois\":[{\"matrice\":\"M\",\"numero\":1,\"section\":\"A\",\"decision\":\"D\",\"code\":\"approbation_comptes\",\"familleRenvoi\":\"C\",\"matriceRenvoi\":\"R\",\"numeroRenvoi\":1,\"regle\":\"G\"}],\"decisions\":[{\"matrice\":\"M\",\"numero\":13,\"section\":\"D\",\"code\":\"c\",\"libelle\":\"L\",\"typeDecision\":\"T\"}],\"rapport\":{\"lignesParOnglet\":{\"M\":1},\"total\":1,\"conditionsPhrases\":[],\"delaisTextuels\":[],\"erreurs\":[]}}";

    @Test
    void le_referentiel_s_ecrit_selon_le_contrat() throws Exception {
        ObjectMapper json = new ObjectMapper();
        ReferentielDemarches r = new ReferentielDemarches("CORPUS_T",
                List.of(new ReferentielDemarches.Ligne("M", "F", "SARL", 1, "reduction_capital",
                        new ReferentielDemarches.Condition("REDUCTION_MOTIF", "EGAL", "retrait_associe", null),
                        "GENERATION_DOCUMENTS", "L", "Cabinet",
                        new ReferentielDemarches.Condition("CAC_EXISTE", "EGAL", "oui", "T"), false,
                        new ReferentielDemarches.Delai("D", 30, "JOURS", "X", "Y"), "J", "PV_AG_SARL", 21, null, null)),
                List.of(new ReferentielDemarches.Renvoi("M", 1, "A", "D", "approbation_comptes", "C", "R", 1, "G")),
                List.of(new ReferentielDemarches.Decision("M", 13, "D", "c", "L", "T")),
                new ReferentielDemarches.Rapport(Map.of("M", 1), 1, List.of(), List.of(), List.of()));
        JsonNode ecrit = json.readTree(json.writeValueAsString(r));
        assertThat(ecrit).isEqualTo(json.readTree(CONTRAT));
    }
}
