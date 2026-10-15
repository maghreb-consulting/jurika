package ma.jurika.common.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Lot L7 (E5), RG-PLN-05 : le forfait Sur Devis est calcule automatiquement selon l'usage reel, sur
 * un prix par dossier a fixer en coherence avec la grille annuelle (CDC 18, action 3). Le montant est
 * presente au super-admin.
 *
 * <p><b>A_DECIDER</b> (directeur) : le prix par dossier n'est pas fixe. La constante
 * {@link #PRIX_PAR_DOSSIER_HT_A_DECIDER} reste {@code null} tant qu'il ne l'est pas : le calcul est
 * pret mais rend "prix a decider", jamais un montant invente. Les reperes de la grille (prix du
 * forfait Max ramene a un dossier) sont donnes a titre d'information, calcules depuis le catalogue.
 */
public final class SurDevisCalcul {

    /**
     * A_DECIDER : prix HT par dossier et par mois d'abonnement du forfait Sur Devis (CDC 18, action 3).
     * {@code null} tant que le directeur ne l'a pas fixe.
     */
    public static final BigDecimal PRIX_PAR_DOSSIER_HT_A_DECIDER = null;

    private SurDevisCalcul() {}

    /**
     * Montant d'un mois d'abonnement Sur Devis.
     *
     * @param dossiersDuMois dossiers comptes dans le mois (RG-PLN-01)
     * @param prixParDossier prix HT par dossier ; {@code null} = non fixe
     */
    public record Resultat(long dossiersDuMois, BigDecimal prixParDossierHt, BigDecimal montantHt,
                           String motifAbsence) {
        public boolean calcule() { return montantHt != null; }
    }

    /** Calcul avec le prix courant ({@link #PRIX_PAR_DOSSIER_HT_A_DECIDER}). */
    public static Resultat calculer(long dossiersDuMois) {
        return calculer(dossiersDuMois, PRIX_PAR_DOSSIER_HT_A_DECIDER);
    }

    /** Calcul avec un prix donne (tests, et le jour ou le prix sera fixe). */
    public static Resultat calculer(long dossiersDuMois, BigDecimal prixParDossier) {
        if (dossiersDuMois < 0) throw new IllegalArgumentException("Nombre de dossiers negatif");
        if (prixParDossier == null) {
            return new Resultat(dossiersDuMois, null, null,
                    "Prix par dossier du forfait Sur Devis \u00e0 d\u00e9cider par la direction (cahier des charges, \u00a7 18, action 3).");
        }
        BigDecimal montant = prixParDossier.multiply(BigDecimal.valueOf(dossiersDuMois))
                .setScale(2, RoundingMode.HALF_UP);
        return new Resultat(dossiersDuMois, prixParDossier, montant, null);
    }

    /**
     * Repere de la grille : prix HT d'un dossier dans le forfait Max, pour le rythme donne
     * (prix de l'echeance ramene a un mois, divise par le quota mensuel de dossiers).
     */
    public static Optional<BigDecimal> repereMaxParDossier(PlanCatalog.BillingPeriod period) {
        PlanCatalog.Plan max = PlanCatalog.MAX;
        return max.prixEcheanceHt(period).map(prix -> BigDecimal.valueOf(prix)
                .divide(BigDecimal.valueOf((long) period.mois() * max.quotas().maxDossiers()), 2, RoundingMode.HALF_UP));
    }
}
