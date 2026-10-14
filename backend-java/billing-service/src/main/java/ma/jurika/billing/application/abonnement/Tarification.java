package ma.jurika.billing.application.abonnement;

import ma.jurika.common.billing.PeriodeAbonnement;
import ma.jurika.common.billing.PlanCatalog;
import ma.jurika.common.billing.PlanCatalog.BillingPeriod;
import ma.jurika.common.billing.PlanCatalog.Plan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * Lot L7 (E5) : calculs de prix, sans etat (CDC 4.3, RG-TAR-01 a RG-TAR-04). Montants en
 * centimes de dirham ; prix du catalogue HORS TAXES ; TVA au taux des parametres de l'editeur,
 * arrondie au centime le plus proche (demi vers le haut).
 */
public final class Tarification {

    private Tarification() {}

    /** Montants d'un paiement : HT, taux, TVA, TTC (TTC = HT + TVA). */
    public record Montants(long htCentimes, BigDecimal tauxTva, long tvaCentimes, long ttcCentimes) {}

    public static Montants depuisHt(long htCentimes, BigDecimal tauxTva) {
        if (tauxTva == null) throw new IllegalStateException("Taux de TVA absent des parametres de l'editeur");
        long tva = BigDecimal.valueOf(htCentimes).multiply(tauxTva)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact();
        return new Montants(htCentimes, tauxTva, tva, htCentimes + tva);
    }

    /**
     * Prix HT d'une echeance : grille du catalogue, ou prix convenu par devis (Sur Devis, en
     * centimes). Sur Devis sans prix convenu : refus nomme.
     */
    public static long prixEcheanceHt(Plan plan, BillingPeriod rythme, Long prixDevisCentimes) {
        if (plan.quoteOnly()) {
            if (prixDevisCentimes == null) {
                throw new IllegalStateException("Forfait Sur Devis : prix de l'\u00e9ch\u00e9ance non convenu (\u00e0 saisir par JURIKA).");
            }
            return prixDevisCentimes;
        }
        return plan.prixEcheanceHt(rythme).orElseThrow() * 100L;
    }

    /** Fin (exclue) de la periode payee par une echeance commencee le {@code debut}. */
    public static LocalDate finPeriode(LocalDate ancre, LocalDate debut, BillingPeriod rythme) {
        long rang = ChronoUnit.MONTHS.between(YearMonth.from(ancre), YearMonth.from(debut));
        return PeriodeAbonnement.echeance(ancre, rang + rythme.mois());
    }

    /**
     * RG-TAR-04, paiement annuel : difference entre les deux prix annuels, au prorata des mois
     * restants de l'annee d'abonnement. Le mois en cours est compte (les nouveaux quotas s'appliquent
     * des la confirmation du paiement) ; resultat arrondi au centime (demi vers le haut).
     *
     * @return {@code moisRestants} et le montant HT en centimes
     */
    public record Prorata(int moisRestants, long htCentimes) {}

    public static Prorata prorataHausseAnnuelle(Plan depuis, Plan vers, LocalDate engagementDebut,
                                                LocalDate engagementFin, LocalDate jour) {
        if (!PlanCatalog.estHausse(depuis, vers)) {
            throw new IllegalArgumentException("Le prorata ne concerne qu'un passage a un forfait superieur");
        }
        LocalDate debutMois = PeriodeAbonnement.contenant(engagementDebut, jour).debut();
        int mois = (int) ChronoUnit.MONTHS.between(YearMonth.from(debutMois), YearMonth.from(engagementFin));
        if (mois <= 0) mois = 0;
        long difference = (vers.priceYearlyMad() - depuis.priceYearlyMad()) * 100L;
        long ht = BigDecimal.valueOf(difference).multiply(BigDecimal.valueOf(mois))
                .divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP).longValueExact();
        return new Prorata(mois, ht);
    }
}
