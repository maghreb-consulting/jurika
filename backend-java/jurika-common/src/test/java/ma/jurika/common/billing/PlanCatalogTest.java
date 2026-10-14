package ma.jurika.common.billing;

import ma.jurika.common.billing.PlanCatalog.BillingPeriod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L7 (E5) : la grille du catalogue est celle du CDC 4.3 et 4.5, chiffre pour chiffre. Le test
 * attend la donnee du cahier des charges, pas la valeur du code.
 */
class PlanCatalogTest {

    @Test
    void quatre_forfaits_dans_l_ordre_du_cahier_des_charges() {
        assertThat(PlanCatalog.ALL).extracting(PlanCatalog.Plan::internalCode)
                .containsExactly("essentiel", "professionnel", "max", "sur_devis");
        assertThat(PlanCatalog.ALL).extracting(PlanCatalog.Plan::displayLabel)
                .containsExactly("Essentiel", "Professionnel", "Max", "Sur Devis");
    }

    @Test
    void prix_hors_taxes_du_4_3() {
        assertThat(PlanCatalog.ESSENTIEL.priceYearlyMad()).isEqualTo(3750);
        assertThat(PlanCatalog.ESSENTIEL.priceMonthlyMad()).isEqualTo(375);
        assertThat(PlanCatalog.PROFESSIONNEL.priceYearlyMad()).isEqualTo(7500);
        assertThat(PlanCatalog.PROFESSIONNEL.priceMonthlyMad()).isEqualTo(750);
        assertThat(PlanCatalog.MAX.priceYearlyMad()).isEqualTo(15000);
        assertThat(PlanCatalog.MAX.priceMonthlyMad()).isEqualTo(1500);
        assertThat(PlanCatalog.SUR_DEVIS.priceYearlyMad()).isNull();
        assertThat(PlanCatalog.SUR_DEVIS.priceMonthlyMad()).isNull();
    }

    /** RG-TAR-02 : mensualite = dixieme du prix annuel ; total mensuel sur un an : 4 500, 9 000, 18 000. */
    @Test
    void mensualite_dixieme_du_prix_annuel() {
        for (PlanCatalog.Plan p : List.of(PlanCatalog.ESSENTIEL, PlanCatalog.PROFESSIONNEL, PlanCatalog.MAX)) {
            assertThat(p.priceMonthlyMad() * 10).isEqualTo(p.priceYearlyMad());
        }
        assertThat(PlanCatalog.ESSENTIEL.priceMonthlyMad() * 12).isEqualTo(4500);
        assertThat(PlanCatalog.PROFESSIONNEL.priceMonthlyMad() * 12).isEqualTo(9000);
        assertThat(PlanCatalog.MAX.priceMonthlyMad() * 12).isEqualTo(18000);
    }

    @Test
    void quotas_du_4_3() {
        assertThat(PlanCatalog.ESSENTIEL.quotas()).isEqualTo(new PlanCatalog.Quotas(1, 25, 5));
        assertThat(PlanCatalog.PROFESSIONNEL.quotas()).isEqualTo(new PlanCatalog.Quotas(2, 50, 10));
        assertThat(PlanCatalog.MAX.quotas()).isEqualTo(new PlanCatalog.Quotas(4, 100, 25));
        assertThat(PlanCatalog.SUR_DEVIS.quotas().isDossiersUnlimited()).isTrue();
    }

    @Test
    void fonctions_du_4_5() {
        assertThat(PlanCatalog.ESSENTIEL.fonctions()).isEmpty();
        assertThat(PlanCatalog.PROFESSIONNEL.fonctions())
                .containsExactlyInAnyOrder(FonctionForfait.ALERTES_DELAIS, FonctionForfait.MESSAGERIE_INTERNE);
        assertThat(PlanCatalog.MAX.fonctions()).containsExactlyInAnyOrder(FonctionForfait.ALERTES_DELAIS,
                FonctionForfait.MESSAGERIE_INTERNE, FonctionForfait.TABLEAUX_DE_BORD_PRODUCTIVITE,
                FonctionForfait.EDITION_INTEGREE_ACTES, FonctionForfait.RAPPORTS_ACTIVITE);
        assertThat(PlanCatalog.MAX.inclut(FonctionForfait.ENGAGEMENT_SERVICE)).isFalse();
        assertThat(PlanCatalog.SUR_DEVIS.fonctions()).containsExactlyInAnyOrder(FonctionForfait.values());
        assertThat(PlanLimitsService.premierForfaitAvec(FonctionForfait.MESSAGERIE_INTERNE)).isEqualTo("Professionnel");
        assertThat(PlanLimitsService.premierForfaitAvec(FonctionForfait.EDITION_INTEGREE_ACTES)).isEqualTo("Max");
    }

    /** Les anciens codes sont repris en base (auth V44, billing V7), jamais rabattus en silence. */
    @Test
    void ancien_code_refuse() {
        assertThat(PlanCatalog.findByCode("business")).isEmpty();
        assertThat(PlanCatalog.findByCode("entreprise")).isEmpty();
        assertThat(PlanCatalog.findByCode("PROFESSIONNEL")).contains(PlanCatalog.PROFESSIONNEL);
        assertThatThrownBy(() -> PlanCatalog.exiger("starter")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rythme_lu_strictement() {
        assertThat(BillingPeriod.fromString("annuel")).isEqualTo(BillingPeriod.YEARLY);
        assertThat(BillingPeriod.fromString("monthly")).isEqualTo(BillingPeriod.MONTHLY);
        assertThatThrownBy(() -> BillingPeriod.fromString(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BillingPeriod.fromString("trimestriel")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hausse_et_baisse_par_rang() {
        assertThat(PlanCatalog.estHausse(PlanCatalog.ESSENTIEL, PlanCatalog.PROFESSIONNEL)).isTrue();
        assertThat(PlanCatalog.estBaisse(PlanCatalog.MAX, PlanCatalog.ESSENTIEL)).isTrue();
        assertThat(PlanCatalog.estHausse(PlanCatalog.MAX, PlanCatalog.MAX)).isFalse();
    }

    /** RG-PLN-01 : mois d'abonnement ancre sur le jour de debut, borne au dernier jour du mois. */
    @Test
    void mois_d_abonnement() {
        LocalDate ancre = LocalDate.of(2026, 1, 31);
        assertThat(PeriodeAbonnement.contenant(ancre, LocalDate.of(2026, 2, 27)))
                .isEqualTo(new PeriodeAbonnement(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28)));
        assertThat(PeriodeAbonnement.contenant(ancre, LocalDate.of(2026, 2, 28)))
                .isEqualTo(new PeriodeAbonnement(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31)));
        assertThat(PeriodeAbonnement.contenant(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 14)))
                .isEqualTo(new PeriodeAbonnement(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 15)));
        assertThat(PeriodeAbonnement.contenant(null, LocalDate.of(2026, 10, 14)))
                .isEqualTo(new PeriodeAbonnement(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)));
        assertThat(PeriodeAbonnement.contenant(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 14)).cle()).isEqualTo("2026-09");
    }

    /** RG-PLN-05 : calcul pret, prix A_DECIDER : aucun montant invente. */
    @Test
    void sur_devis_prix_a_decider() {
        assertThat(SurDevisCalcul.PRIX_PAR_DOSSIER_HT_A_DECIDER).isNull();
        SurDevisCalcul.Resultat r = SurDevisCalcul.calculer(130);
        assertThat(r.calcule()).isFalse();
        assertThat(r.motifAbsence()).contains("d\u00e9cider");
        SurDevisCalcul.Resultat avecPrix = SurDevisCalcul.calculer(130, new BigDecimal("12.50"));
        assertThat(avecPrix.montantHt()).isEqualByComparingTo("1625.00");
        assertThat(SurDevisCalcul.repereMaxParDossier(BillingPeriod.MONTHLY)).contains(new BigDecimal("15.00"));
        assertThat(SurDevisCalcul.repereMaxParDossier(BillingPeriod.YEARLY)).contains(new BigDecimal("12.50"));
    }
}
