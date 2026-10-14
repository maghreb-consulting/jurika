package ma.jurika.common.billing;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Catalogue des forfaits JURIKA : source unique de verite (grille publique, quotas, prix des
 * echeances, fonctions par forfait).
 *
 * <p><b>Lot L7 (E5)</b> : grille du cahier des charges, paragraphe 4.3, reprise a l'identique.
 * Tous les prix sont HORS TAXES, en dirhams entiers. Le forfait est souscrit pour un an ; il se
 * regle en une fois (annuel) ou en douze mensualites egales au dixieme du prix annuel
 * (RG-TAR-01, RG-TAR-02).
 *
 * <pre>
 *                 Essentiel   Professionnel   Max        Sur Devis
 *  dossiers/mois  25          50              100        plus de 100
 *  employes       1           2               4          selon devis
 *  stockage       5 Go        10 Go           25 Go      selon devis
 *  annuel HT      3 750       7 500           15 000     selon devis
 *  mensuel HT     375         750             1 500      selon devis
 * </pre>
 *
 * Le superviseur (un seul par cabinet) n'est pas compte dans le quota d'utilisateurs : les
 * quotas portent sur les ACCES EMPLOYES (1 superviseur + N employes). Les comptes clients ne
 * sont jamais comptes (RG-PLN-08).
 *
 * <p>Codes : {@code essentiel}, {@code professionnel}, {@code max}, {@code sur_devis} (colonne
 * {@code workspaces.selected_plan}, {@code subscriptions.plan_code}, {@code payments.plan_code}).
 * Les anciens codes (business, entreprise) sont repris par les migrations auth V44 et billing V7 :
 * aucun alias n'est garde dans le code (un code inconnu est refuse, jamais rabattu).
 */
public final class PlanCatalog {

    private PlanCatalog() {}

    /** Rythme de paiement (RG-TAR-01, RG-TAR-02). Le suffixe est la valeur stockee en base. */
    public enum BillingPeriod {
        MONTHLY("monthly", "MENSUEL", 1),
        YEARLY("yearly", "ANNUEL", 12);

        private final String suffix;
        private final String codeContrat;
        private final int mois;

        BillingPeriod(String suffix, String codeContrat, int mois) {
            this.suffix = suffix;
            this.codeContrat = codeContrat;
            this.mois = mois;
        }

        /** Valeur stockee ({@code monthly} / {@code yearly}). */
        public String suffix() { return suffix; }

        /** Valeur du contrat d'abonnement ({@code MENSUEL} / {@code ANNUEL}). */
        public String codeContrat() { return codeContrat; }

        /** Nombre de mois couverts par une echeance. */
        public int mois() { return mois; }

        /**
         * Lecture stricte : {@code monthly}/{@code mensuel}, {@code yearly}/{@code annuel}.
         * Une valeur absente ou inconnue est REFUSEE (aucun rythme par defaut).
         */
        public static BillingPeriod fromString(String s) {
            if (s == null || s.isBlank()) {
                throw new IllegalArgumentException("Rythme de paiement absent : mensuel ou annuel.");
            }
            return switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "yearly", "annuel", "annual" -> YEARLY;
                case "monthly", "mensuel" -> MONTHLY;
                default -> throw new IllegalArgumentException("Rythme de paiement inconnu : " + s);
            };
        }
    }

    /**
     * Quotas d'un forfait. {@code -1} = sans limite fixe (Sur Devis : "selon devis").
     *
     * @param maxUsers     nombre d'acces EMPLOYES (le superviseur n'est pas compte)
     * @param maxDossiers  dossiers ouverts par MOIS d'abonnement (RG-PLN-01)
     * @param maxStorageGb stockage, en Go
     */
    public record Quotas(int maxUsers, int maxDossiers, int maxStorageGb) {
        public boolean isUsersUnlimited()   { return maxUsers   < 0; }
        public boolean isDossiersUnlimited(){ return maxDossiers < 0; }
        public boolean isStorageUnlimited() { return maxStorageGb < 0; }
    }

    /**
     * Definition d'un forfait. Prix HT en dirhams entiers ; {@code null} pour Sur Devis (prix
     * fixe par devis, RG-PLN-05).
     */
    public record Plan(
            String internalCode,
            String displayLabel,
            int rang,
            Integer priceMonthlyMad,
            Integer priceYearlyMad,
            Quotas quotas,
            Set<FonctionForfait> fonctions,
            boolean quoteOnly
    ) {
        public Plan {
            fonctions = fonctions.isEmpty() ? Set.of() : Set.copyOf(fonctions);
        }

        /** Le forfait donne-t-il acces a la fonction ? */
        public boolean inclut(FonctionForfait f) {
            return fonctions.contains(f);
        }

        /** Code du contrat d'abonnement ({@code ESSENTIEL}, {@code PROFESSIONNEL}, {@code MAX}, {@code SUR_DEVIS}). */
        public String codeContrat() {
            return internalCode.toUpperCase(Locale.ROOT);
        }

        /** Prix HT d'une echeance du rythme donne ; vide pour Sur Devis. */
        public Optional<Integer> prixEcheanceHt(BillingPeriod period) {
            return Optional.ofNullable(period == BillingPeriod.YEARLY ? priceYearlyMad : priceMonthlyMad);
        }
    }

    // ---------------------------------------------------------------- grille du CDC 4.3 et 4.5

    public static final Plan ESSENTIEL = new Plan(
            "essentiel", "Essentiel", 1,
            375, 3750,
            new Quotas(1, 25, 5),
            EnumSet.noneOf(FonctionForfait.class),
            false);

    public static final Plan PROFESSIONNEL = new Plan(
            "professionnel", "Professionnel", 2,
            750, 7500,
            new Quotas(2, 50, 10),
            EnumSet.of(FonctionForfait.ALERTES_DELAIS, FonctionForfait.MESSAGERIE_INTERNE),
            false);

    public static final Plan MAX = new Plan(
            "max", "Max", 3,
            1500, 15000,
            new Quotas(4, 100, 25),
            EnumSet.of(FonctionForfait.ALERTES_DELAIS, FonctionForfait.MESSAGERIE_INTERNE,
                    FonctionForfait.TABLEAUX_DE_BORD_PRODUCTIVITE, FonctionForfait.EDITION_INTEGREE_ACTES,
                    FonctionForfait.RAPPORTS_ACTIVITE),
            false);

    /**
     * Sur Devis : plus de 100 dossiers ; utilisateurs et stockage selon devis. Les quotas d'un devis
     * ne sont pas encore saisissables (A_DECIDER) : aucune limite fixe n'est appliquee, la
     * facturation suit l'usage reel (RG-PLN-05, {@link SurDevisCalcul}).
     */
    public static final Plan SUR_DEVIS = new Plan(
            "sur_devis", "Sur Devis", 4,
            null, null,
            new Quotas(-1, -1, -1),
            EnumSet.allOf(FonctionForfait.class),
            true);

    /** Ordre d'affichage public. */
    public static final List<Plan> ALL = List.of(ESSENTIEL, PROFESSIONNEL, MAX, SUR_DEVIS);

    private static final Map<String, Plan> BY_CODE = ALL.stream()
            .collect(Collectors.toUnmodifiableMap(Plan::internalCode, p -> p));

    /** Forfait par code exact (insensible a la casse). Code inconnu ou ancien : vide. */
    public static Optional<Plan> findByCode(String code) {
        if (code == null) return Optional.empty();
        return Optional.ofNullable(BY_CODE.get(code.trim().toLowerCase(Locale.ROOT)));
    }

    /** Forfait par code, ou refus nomme. */
    public static Plan exiger(String code) {
        return findByCode(code).orElseThrow(() -> new IllegalArgumentException("Forfait inconnu : " + code));
    }

    /** Code canonique (minuscules, sans espaces) ; ne rabat aucun ancien code. */
    public static String normalize(String code) {
        return code == null ? null : code.trim().toLowerCase(Locale.ROOT);
    }

    /** Vrai si {@code vers} est un forfait superieur a {@code depuis} (RG-TAR-04). */
    public static boolean estHausse(Plan depuis, Plan vers) {
        return vers.rang() > depuis.rang();
    }

    /** Vrai si {@code vers} est un forfait inferieur a {@code depuis} (RG-TAR-05). */
    public static boolean estBaisse(Plan depuis, Plan vers) {
        return vers.rang() < depuis.rang();
    }
}
