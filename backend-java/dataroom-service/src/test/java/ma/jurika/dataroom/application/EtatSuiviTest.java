package ma.jurika.dataroom.application;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Lot L5 (E9, RG-COM-07) : correspondance explicite vers les trois etats de suivi. */
class EtatSuiviTest {

    @ParameterizedTest
    @CsvSource({
            "EMPLOYE_TO_CLIENT, OUVERTE, NON_TRAITEE",
            "EMPLOYE_TO_CLIENT, REPONDUE, EN_COURS",
            "EMPLOYE_TO_CLIENT, A_COMPLETER, EN_COURS",
            "EMPLOYE_TO_CLIENT, CLOTUREE, TRAITEE",
            "CLIENT_TO_EMPLOYE, NON_TRAITEE, NON_TRAITEE",
            "CLIENT_TO_EMPLOYE, EN_COURS, EN_COURS",
            "CLIENT_TO_EMPLOYE, TRAITEE, TRAITEE",
    })
    void chaque_statut_connu_a_son_etat(String direction, String statut, EtatSuivi attendu) {
        assertThat(EtatSuivi.of(direction, statut)).isEqualTo(attendu);
    }

    @Test
    void une_demande_sans_direction_est_une_demande_du_client() {
        assertThat(EtatSuivi.of(null, "EN_COURS")).isEqualTo(EtatSuivi.EN_COURS);
    }

    @Test
    void un_statut_inconnu_ne_recoit_aucun_etat() {
        assertThat(EtatSuivi.of("EMPLOYE_TO_CLIENT", "NON_TRAITEE")).isNull();
        assertThat(EtatSuivi.of("CLIENT_TO_EMPLOYE", "OUVERTE")).isNull();
        assertThat(EtatSuivi.of("CLIENT_TO_EMPLOYE", null)).isNull();
    }
}
