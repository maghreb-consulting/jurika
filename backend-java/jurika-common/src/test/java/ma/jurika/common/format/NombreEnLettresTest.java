package ma.jurika.common.format;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lot L5 (E6) : montants en toutes lettres de l'etat des debours (RG-DEB-02). */
class NombreEnLettresTest {

    @Test
    void entiers() {
        assertThat(NombreEnLettres.entier(0)).isEqualTo("Z\u00c9RO");
        assertThat(NombreEnLettres.entier(71)).isEqualTo("SOIXANTE ET ONZE");
        assertThat(NombreEnLettres.entier(80)).isEqualTo("QUATRE-VINGTS");
        assertThat(NombreEnLettres.entier(200)).isEqualTo("DEUX CENTS");
        assertThat(NombreEnLettres.entier(200_000)).isEqualTo("DEUX CENT MILLE");
        assertThat(NombreEnLettres.entier(1_234)).isEqualTo("MILLE DEUX CENT TRENTE-QUATRE");
        assertThat(NombreEnLettres.entier(999_999_999)).isEqualTo(
                "NEUF CENT QUATRE-VINGT-DIX-NEUF MILLIONS NEUF CENT QUATRE-VINGT-DIX-NEUF MILLE "
                        + "NEUF CENT QUATRE-VINGT-DIX-NEUF");
    }

    @Test
    void dirhams_et_centimes() {
        assertThat(NombreEnLettres.montantDirhams(new BigDecimal("1234.50")))
                .isEqualTo("MILLE DEUX CENT TRENTE-QUATRE DIRHAMS ET CINQUANTE CENTIMES");
        assertThat(NombreEnLettres.montantDirhams(new BigDecimal("1234.00")))
                .isEqualTo("MILLE DEUX CENT TRENTE-QUATRE DIRHAMS");
        assertThat(NombreEnLettres.montantDirhams(new BigDecimal("80.80")))
                .isEqualTo("QUATRE-VINGTS DIRHAMS ET QUATRE-VINGTS CENTIMES");
    }

    @Test
    void singulier_et_de_apres_les_millions() {
        assertThat(NombreEnLettres.montantDirhams(new BigDecimal("1.01"))).isEqualTo("UN DIRHAM ET UN CENTIME");
        assertThat(NombreEnLettres.montantDirhams(new BigDecimal("0.05"))).isEqualTo("Z\u00c9RO DIRHAM ET CINQ CENTIMES");
        assertThat(NombreEnLettres.montantDirhams(new BigDecimal("2000000")))
                .isEqualTo("DEUX MILLIONS DE DIRHAMS");
        assertThat(NombreEnLettres.montantDirhams(new BigDecimal("2000001")))
                .isEqualTo("DEUX MILLIONS UN DIRHAMS");
    }

    @Test
    void arrondi_au_centime_et_refus_hors_plage() {
        assertThat(NombreEnLettres.montantDirhams(new BigDecimal("10.005"))).isEqualTo("DIX DIRHAMS ET UN CENTIME");
        assertThatThrownBy(() -> NombreEnLettres.montantDirhams(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NombreEnLettres.montantDirhams(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NombreEnLettres.entier(1_000_000_000L)).isInstanceOf(IllegalArgumentException.class);
    }
}
