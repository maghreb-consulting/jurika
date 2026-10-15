package ma.jurika.billing.application.abonnement;

import ma.jurika.common.billing.PlanCatalog;
import ma.jurika.common.billing.PlanCatalog.BillingPeriod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lot L7 (E5) : prix HT, TVA, periodes et prorata (CDC 4.3, RG-TAR-01 a RG-TAR-04). */
class TarificationTest {

    @Test
    void tva_au_taux_de_l_editeur_arrondie_au_centime() {
        Tarification.Montants m = Tarification.depuisHt(375_00, new BigDecimal("20.00"));
        assertThat(m.tvaCentimes()).isEqualTo(75_00);
        assertThat(m.ttcCentimes()).isEqualTo(450_00);
        assertThat(Tarification.depuisHt(1, new BigDecimal("20.00")).tvaCentimes()).isZero();
        assertThat(Tarification.depuisHt(3, new BigDecimal("20.00")).tvaCentimes()).isEqualTo(1);
        assertThat(Tarification.depuisHt(1000_00, new BigDecimal("10")).ttcCentimes()).isEqualTo(1100_00);
    }

    @Test
    void prix_d_une_echeance() {
        assertThat(Tarification.prixEcheanceHt(PlanCatalog.ESSENTIEL, BillingPeriod.YEARLY, null)).isEqualTo(3750_00);
        assertThat(Tarification.prixEcheanceHt(PlanCatalog.MAX, BillingPeriod.MONTHLY, null)).isEqualTo(1500_00);
        assertThat(Tarification.prixEcheanceHt(PlanCatalog.SUR_DEVIS, BillingPeriod.MONTHLY, 2400_00L)).isEqualTo(2400_00);
        assertThatThrownBy(() -> Tarification.prixEcheanceHt(PlanCatalog.SUR_DEVIS, BillingPeriod.MONTHLY, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("non convenu");
    }

    @Test
    void periode_d_une_echeance_au_jour_anniversaire() {
        LocalDate ancre = LocalDate.of(2026, 1, 31);
        assertThat(Tarification.finPeriode(ancre, ancre, BillingPeriod.MONTHLY)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(Tarification.finPeriode(ancre, LocalDate.of(2026, 2, 28), BillingPeriod.MONTHLY)).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(Tarification.finPeriode(ancre, ancre, BillingPeriod.YEARLY)).isEqualTo(LocalDate.of(2027, 1, 31));
    }

    /**
     * RG-TAR-04 : Essentiel vers Professionnel en annuel, abonnement du 15/01/2026 au 15/01/2027,
     * demande le 20/04/2026 : mois en cours (15/04-15/05) compris, 9 mois restants ;
     * (7 500 - 3 750) x 9 / 12 = 2 812,50 DH HT.
     */
    @Test
    void prorata_de_la_hausse_en_annuel() {
        Tarification.Prorata p = Tarification.prorataHausseAnnuelle(PlanCatalog.ESSENTIEL, PlanCatalog.PROFESSIONNEL,
                LocalDate.of(2026, 1, 15), LocalDate.of(2027, 1, 15), LocalDate.of(2026, 4, 20));
        assertThat(p.moisRestants()).isEqualTo(9);
        assertThat(p.htCentimes()).isEqualTo(2812_50);
        assertThatThrownBy(() -> Tarification.prorataHausseAnnuelle(PlanCatalog.MAX, PlanCatalog.ESSENTIEL,
                LocalDate.of(2026, 1, 15), LocalDate.of(2027, 1, 15), LocalDate.of(2026, 4, 20)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
