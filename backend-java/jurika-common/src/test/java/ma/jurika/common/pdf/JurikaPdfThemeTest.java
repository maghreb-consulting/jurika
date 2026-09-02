package ma.jurika.common.pdf;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resolution du nom du cabinet en en-tete des PDF (nom affiche -> denomination
 * workspace -> repli). Garantit un en-tete jamais vide.
 */
class JurikaPdfThemeTest {

    @Test
    void utilise_le_nom_affiche_si_renseigne() {
        assertThat(JurikaPdfTheme.resolveCabinetName("Cabinet Alaoui & Associés", "Maghreb Consulting"))
                .isEqualTo("Cabinet Alaoui & Associés");
    }

    @Test
    void repli_sur_la_denomination_si_nom_affiche_vide() {
        assertThat(JurikaPdfTheme.resolveCabinetName(null, "Maghreb Consulting"))
                .isEqualTo("Maghreb Consulting");
        assertThat(JurikaPdfTheme.resolveCabinetName("   ", "Maghreb Consulting"))
                .isEqualTo("Maghreb Consulting");
    }

    @Test
    void repli_final_si_tout_est_vide() {
        assertThat(JurikaPdfTheme.resolveCabinetName(null, null)).isEqualTo("Cabinet");
        assertThat(JurikaPdfTheme.resolveCabinetName("", "  ")).isEqualTo("Cabinet");
    }

    @Test
    void trim_le_nom_affiche() {
        assertThat(JurikaPdfTheme.resolveCabinetName("  Cabinet X  ", "WS")).isEqualTo("Cabinet X");
    }
}
