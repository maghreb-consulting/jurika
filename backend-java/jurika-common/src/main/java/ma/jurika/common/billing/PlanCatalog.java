package ma.jurika.common.billing;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Sprint Beta (pricing-deploy) — Catalogue centralise des plans tarifaires JURIKA.
 *
 * <p>Source unique de verite pour :
 * <ul>
 *   <li>l'endpoint public {@code GET /api/v1/public/pricing} (auth-service)</li>
 *   <li>la resolution des price IDs Stripe (billing-service)</li>
 *   <li>l'enforcement des quotas (jurika-common {@code PlanLimitsService})</li>
 *   <li>les composants frontend PlanPicker + BillingPage + marketing pricing</li>
 * </ul>
 *
 * <p>Decision archi : statique compile-time plutot que table en base.
 * Justification : les plans changent rarement (decisions directeur),
 * un changement = re-deploy controle. Une table billing_plans aurait
 * couvert le runtime-toggle mais ajouterait :
 *  - migration + RLS + audit
 *  - synchronisation cross-service (cache invalidation)
 *  - risque d'incoherence entre selected_plan et catalogue
 * Hors-scope demo. Cf. {@code RG-BL-CATALOG}.
 *
 * <p>Naming canonique (spec directeur 2026-06-02, remplace Sprint Beta) :
 * <ul>
 *   <li>internalCode = {@code essentiel} / {@code business} / {@code entreprise}</li>
 *   <li>displayLabel = {@code Essentiel} / {@code Business} / {@code Entreprise}</li>
 * </ul>
 * Les codes pre-2026-06-02 ainsi que leurs lookup_keys Stripe sont migres
 * en DB par billing V3 + auth V23 et purges du code applicatif.
 *
 * <p>Stripe lookup_keys (cf. {@code scripts/stripe-setup-products.mjs}) :
 * <ul>
 *   <li>{@code essentiel_monthly}, {@code essentiel_yearly}</li>
 *   <li>{@code business_monthly}, {@code business_yearly}</li>
 *   <li>Entreprise : aucun price Stripe (flux /contact-sales, RG-BL10)</li>
 * </ul>
 */
public final class PlanCatalog {

    private PlanCatalog() {}

    /**
     * Periode de facturation. Sert a resoudre le Stripe price ID.
     */
    public enum BillingPeriod {
        MONTHLY("monthly"),
        YEARLY("yearly");

        private final String suffix;

        BillingPeriod(String suffix) { this.suffix = suffix; }
        public String suffix() { return suffix; }

        public static BillingPeriod fromString(String s) {
            if (s == null) return MONTHLY;
            return switch (s.toLowerCase()) {
                case "yearly", "annual", "year", "annuel" -> YEARLY;
                default -> MONTHLY;
            };
        }
    }

    /**
     * Quotas d'un plan. {@code -1} = illimite.
     */
    public record Quotas(int maxUsers, int maxDossiers, int maxStorageGb) {
        public boolean isUsersUnlimited()   { return maxUsers   < 0; }
        public boolean isDossiersUnlimited(){ return maxDossiers < 0; }
        public boolean isStorageUnlimited() { return maxStorageGb < 0; }
    }

    /**
     * Definition immutable d'un plan tarifaire. Le {@code priceMonthlyMad}
     * est exprime en dirhams entiers HT (TVA 20% appliquee par Stripe Tax
     * en checkout cf. RG-BL12). {@code null} pour Entreprise.
     */
    public record Plan(
            String internalCode,
            String displayLabel,
            String marketingTarget,
            Integer priceMonthlyMad,
            Integer priceYearlyMad,
            String stripeLookupKeyMonthly,
            String stripeLookupKeyYearly,
            Quotas quotas,
            List<String> features,
            boolean featured,
            String ribbon,
            boolean quoteOnly
    ) {
        public boolean hasStripePrice() {
            return !quoteOnly && stripeLookupKeyMonthly != null;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  CATALOGUE — Prix valides directeur Maghreb Consulting 2026-06-01
    // ─────────────────────────────────────────────────────────────────

    public static final Plan ESSENTIEL = new Plan(
            "essentiel",
            "Essentiel",
            "Petites structures juridiques",
            499,
            4999,
            "essentiel_monthly",
            "essentiel_yearly",
            new Quotas(2, 30, 5),
            List.of(
                    "2 utilisateurs",
                    "30 dossiers actifs",
                    "Tous les workflows",
                    "Data Room 5 Go",
                    "Chat client-employe",
                    "Generation de documents par IA"
            ),
            false,
            null,
            false
    );

    public static final Plan BUSINESS = new Plan(
            "business",
            "Business",
            "Cabinets en croissance",
            1199,
            11999,
            "business_monthly",
            "business_yearly",
            new Quotas(6, 100, 20),
            List.of(
                    "6 utilisateurs",
                    "100 dossiers actifs",
                    "Tous les workflows",
                    "Data Room 20 Go",
                    "Chat client-employe",
                    "Generation de documents par IA",
                    "Chatbot RAG (assistant juridique conversationnel)"
            ),
            true,
            "Recommande",
            false
    );

    public static final Plan ENTREPRISE = new Plan(
            "entreprise",
            "Entreprise",
            "Reseaux & cabinets multi-sites",
            null,
            null,
            null,
            null,
            new Quotas(-1, -1, -1),
            List.of(
                    "Utilisateurs illimites",
                    "Plus de 100 dossiers",
                    "Tous les workflows",
                    "Data Room sur mesure",
                    "Chat client-employe",
                    "Generation de documents par IA",
                    "Chatbot RAG",
                    "Accompagnement dedie"
            ),
            false,
            null,
            true
    );

    /** Ordre d'affichage public (Essentiel -> Business -> Entreprise). */
    public static final List<Plan> ALL = List.of(ESSENTIEL, BUSINESS, ENTREPRISE);

    private static final Map<String, Plan> BY_CODE = ALL.stream()
            .collect(Collectors.toUnmodifiableMap(Plan::internalCode, p -> p));

    private static final Map<String, Plan> BY_LOOKUP_KEY = ALL.stream()
            .filter(Plan::hasStripePrice)
            .flatMap(p -> java.util.stream.Stream.of(
                    Map.entry(p.stripeLookupKeyMonthly(), p),
                    Map.entry(p.stripeLookupKeyYearly(), p)))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    /**
     * Recherche un plan par code interne (insensible a la casse) en
     * acceptant les anciens alias pre-2026-06-02 pour la compat retro
     * tests + lignes DB pre-migration V23/V3.
     */
    public static Optional<Plan> findByCode(String code) {
        if (code == null) return Optional.empty();
        String normalized = normalize(code);
        return Optional.ofNullable(BY_CODE.get(normalized));
    }

    /**
     * Normalise un code vers son ID canonique (spec directeur 2026-06-02).
     *
     * <p>Tous les anciens noms (pre-2026-06-02) sont rabattus sur les
     * codes canoniques pour lisser la transition. Le code applicatif ne
     * doit jamais produire d'ancien nom — ces alias servent uniquement
     * de filet pour les lignes DB ou requetes retardees. Les lignes
     * "case" ci-dessous sont marquees {@code pricing-purge:ok} pour le
     * garde-fou anti-regression.
     */
    public static String normalize(String code) {
        if (code == null) return null;
        return switch (code.toLowerCase()) {
            case "starter", "essentiel"      -> "essentiel"; // pricing-purge:ok
            case "professionnel", "business" -> "business";  // pricing-purge:ok
            case "enterprise", "entreprise"  -> "entreprise";
            default -> code.toLowerCase();
        };
    }

    /**
     * Recherche le plan associe a un lookup_key Stripe (webhook lookups,
     * scripts setup).
     */
    public static Optional<Plan> findByStripeLookupKey(String lookupKey) {
        if (lookupKey == null) return Optional.empty();
        return Optional.ofNullable(BY_LOOKUP_KEY.get(lookupKey));
    }

    /**
     * Resout le lookup_key Stripe pour un (planCode, period). Leve
     * {@link IllegalArgumentException} si le plan est en sur-devis
     * ({@code entreprise}) ou inconnu.
     */
    public static String resolveStripeLookupKey(String planCode, BillingPeriod period) {
        Plan plan = findByCode(planCode).orElseThrow(
                () -> new IllegalArgumentException("Plan inconnu : " + planCode));
        if (plan.quoteOnly()) {
            throw new IllegalArgumentException(
                    "Plan " + plan.internalCode() + " sur devis — utiliser /contact-sales (RG-BL10)");
        }
        return period == BillingPeriod.YEARLY
                ? plan.stripeLookupKeyYearly()
                : plan.stripeLookupKeyMonthly();
    }
}
