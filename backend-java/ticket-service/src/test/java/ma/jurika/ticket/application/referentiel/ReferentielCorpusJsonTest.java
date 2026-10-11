package ma.jurika.ticket.application.referentiel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L4b (E2) : contrat JSON du referentiel lu chez ai-service (meme texte que
 * ReferentielDemarchesJsonTest d'ai-service).
 */
class ReferentielCorpusJsonTest {

    static final String CONTRAT = "{\"version\":\"CORPUS_T\",\"lignes\":[{\"matrice\":\"M\",\"famille\":\"F\",\"forme\":\"SARL\",\"ordre\":1,\"decision\":\"reduction_capital\",\"sousCondition\":{\"variable\":\"REDUCTION_MOTIF\",\"operateur\":\"EGAL\",\"valeur\":\"retrait_associe\",\"texte\":null},\"statut\":\"GENERATION_DOCUMENTS\",\"libelle\":\"L\",\"organisme\":\"Cabinet\",\"condition\":{\"variable\":\"CAC_EXISTE\",\"operateur\":\"EGAL\",\"valeur\":\"oui\",\"texte\":\"T\"},\"obligatoire\":false,\"delai\":{\"texte\":\"D\",\"valeur\":30,\"unite\":\"JOURS\",\"depuisVariable\":\"X\",\"echeanceVariable\":\"Y\"},\"justificatif\":\"J\",\"modele\":\"PV_AG_SARL\",\"numeroDecision\":21,\"aCompleter\":null,\"completePlateforme\":null}],\"renvois\":[{\"matrice\":\"M\",\"numero\":1,\"section\":\"A\",\"decision\":\"D\",\"code\":\"approbation_comptes\",\"familleRenvoi\":\"C\",\"matriceRenvoi\":\"R\",\"numeroRenvoi\":1,\"regle\":\"G\"}],\"decisions\":[{\"matrice\":\"M\",\"numero\":13,\"section\":\"D\",\"code\":\"c\",\"libelle\":\"L\",\"typeDecision\":\"T\"}],\"parcoursCreation\":[],\"rapport\":{\"lignesParOnglet\":{\"M\":1},\"total\":1,\"conditionsPhrases\":[],\"delaisTextuels\":[],\"erreurs\":[]}}";

    @Test
    void le_referentiel_se_lit_selon_le_contrat() throws Exception {
        ReferentielCorpus r = new ObjectMapper().readValue(CONTRAT, ReferentielCorpus.class);
        assertThat(r.version()).isEqualTo("CORPUS_T");
        ReferentielCorpus.Ligne l = r.lignes().get(0);
        assertThat(l.sousCondition().variable()).isEqualTo("REDUCTION_MOTIF");
        assertThat(l.condition().valeur()).isEqualTo("oui");
        assertThat(l.delai().echeanceVariable()).isEqualTo("Y");
        assertThat(l.delai().depuisVariable()).isEqualTo("X");
        assertThat(l.numeroDecision()).isEqualTo(21);
        assertThat(l.obligatoire()).isFalse();
        assertThat(r.renvois().get(0).matriceRenvoi()).isEqualTo("R");
        assertThat(r.decisions().get(0).typeDecision()).isEqualTo("T");
        assertThat(r.rapport().lignesParOnglet()).containsEntry("M", 1);
        assertThat(ImportReferentielDemarches.motifDeRefus(r)).isNull();
        // Comparaison de la creation (E3) : la presentation du corpus n'est pas un ecart.
        assertThat(ImportReferentielDemarches.normaliser("\u2610  Ouverture   du ticket")).isEqualTo("Ouverture du ticket");
        assertThat(ImportReferentielDemarches.normaliser("\u2014")).isEmpty();
        assertThat(ImportReferentielDemarches.normaliser("\u2014 (proc\u00e9dure interne)")).isEqualTo("\u2014 (proc\u00e9dure interne)");
    }
}
