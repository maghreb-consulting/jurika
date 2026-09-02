package ma.jurika.billing.infrastructure.stripe;

import jakarta.validation.constraints.NotBlank;
import ma.jurika.common.billing.PlanCatalog;
import ma.jurika.common.billing.PlanCatalog.BillingPeriod;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Sprint 12 — proprietes Stripe + Billing chargees depuis {@code application.yml}
 * (prefixe {@code jurika.stripe}).
 *
 * <p>Sprint Beta (pricing-deploy) — passage de 2 a 4 price IDs : chaque
 * plan (Essentiel / Business) a desormais une variante mensuelle ET
 * annuelle. La source de verite du nommage est {@link PlanCatalog}.
 *
 * <p>Validate via {@code @Validated} : si {@code STRIPE_SECRET_KEY} est absente
 * en environnement non-dev, le contexte echoue au demarrage (fail-fast).
 *
 * <p>En dev, les placeholders {@code pk_test_PLACEHOLDER} / {@code sk_test_PLACEHOLDER}
 * passent la validation @NotBlank mais aucun appel Stripe reel ne fonctionnera.
 * C'est intentionnel pour ne pas bloquer le build avant que l'utilisateur ait
 * cree son compte Stripe TEST mode et lance {@code scripts/stripe-setup-products.mjs}.
 */
@ConfigurationProperties(prefix = "jurika.stripe")
@Validated
public class StripeProperties {

    @NotBlank
    private String publishableKey;
    @NotBlank
    private String secretKey;
    @NotBlank
    private String webhookSecret;
    private String checkoutLocale = "fr";
    private Prices prices = new Prices();

    /**
     * IDs Stripe (price_xxx) injectes depuis {@code STRIPE_PRICE_*} via
     * {@code application.yml}. Chaque plan paye a une variante mensuelle
     * et annuelle. Entreprise est sur devis, hors Stripe (RG-BL10).
     */
    public static class Prices {
        private PlanPrices essentiel = new PlanPrices();
        private PlanPrices business = new PlanPrices();

        public PlanPrices getEssentiel() { return essentiel; }
        public void setEssentiel(PlanPrices e) { this.essentiel = e; }
        public PlanPrices getBusiness() { return business; }
        public void setBusiness(PlanPrices b) { this.business = b; }
    }

    public static class PlanPrices {
        private String monthly;
        private String yearly;

        public String getMonthly() { return monthly; }
        public void setMonthly(String m) { this.monthly = m; }
        public String getYearly() { return yearly; }
        public void setYearly(String y) { this.yearly = y; }
    }

    /**
     * Resoud le price Stripe pour un (planCode, period).
     *
     * <p>Source canonique : {@code workspace.selected_plan} (spec directeur
     * 2026-06-02 : {@code 'essentiel' | 'business' | 'entreprise'}). Les
     * codes pre-2026-06-02 restent toleres via
     * {@link PlanCatalog#normalize(String)} pour transition.
     *
     * @throws IllegalArgumentException pour {@code entreprise}/{@code enterprise}
     *         (RG-BL10, sur devis hors Stripe) ou pour tout plan inconnu.
     */
    public String resolvePriceId(String planCode, BillingPeriod period) {
        if (planCode == null) {
            throw new IllegalArgumentException("planCode null");
        }
        String normalized = PlanCatalog.normalize(planCode);
        BillingPeriod p = period != null ? period : BillingPeriod.MONTHLY;
        return switch (normalized) {
            case "essentiel"  -> p == BillingPeriod.YEARLY ? prices.essentiel.yearly : prices.essentiel.monthly;
            case "business"   -> p == BillingPeriod.YEARLY ? prices.business.yearly  : prices.business.monthly;
            case "entreprise" -> throw new IllegalArgumentException(
                    "Plan entreprise hors Stripe (RG-BL10) — utiliser /api/v1/billing/contact-sales");
            default -> throw new IllegalArgumentException("Plan inconnu: " + planCode);
        };
    }

    /**
     * Variante retro-compatible Sprint 12 (sans periode) → defaut MONTHLY.
     * Conservee pour les call sites non encore migres.
     */
    public String resolvePriceId(String planCode) {
        return resolvePriceId(planCode, BillingPeriod.MONTHLY);
    }

    /**
     * Normalise un plan code vers son ID canonique (celui stocke dans
     * {@code workspace.selected_plan}). Garantit qu'on persiste un code
     * spec-2026-06-02 et non un ancien alias en DB billing.
     *
     * @deprecated utiliser {@link PlanCatalog#normalize(String)}.
     */
    @Deprecated
    public static String normalize(String planCode) {
        return PlanCatalog.normalize(planCode);
    }

    public String getPublishableKey() { return publishableKey; }
    public void setPublishableKey(String s) { this.publishableKey = s; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String s) { this.secretKey = s; }
    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String s) { this.webhookSecret = s; }
    public String getCheckoutLocale() { return checkoutLocale; }
    public void setCheckoutLocale(String s) { this.checkoutLocale = s; }
    public Prices getPrices() { return prices; }
    public void setPrices(Prices p) { this.prices = p; }
}
