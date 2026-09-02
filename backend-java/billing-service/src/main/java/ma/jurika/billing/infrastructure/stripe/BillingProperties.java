package ma.jurika.billing.infrastructure.stripe;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Sprint 12 — proprietes business du module billing (TVA, emails, URLs Stripe).
 *
 * <p>RG-BL12 : TVA Maroc 20% calculee backend, pas Stripe Tax (risque archi #4).
 * RG-BL10 : email contact-sales pour plan Enterprise.
 */
@ConfigurationProperties(prefix = "jurika.billing")
@Validated
public class BillingProperties {

    @DecimalMin("0.00") @DecimalMax("30.00")
    private BigDecimal tvaRatePercent = new BigDecimal("20.00");

    @NotBlank
    private String salesEmail = "contact@jurika.ai";

    @NotBlank
    private String fromEmail = "noreply@jurika.ma";

    /**
     * BUG 14 (2026-06-07) — autorise la methode de paiement TEST_BYPASS
     * (validation instantanee sans Stripe ni virement reel). A activer
     * UNIQUEMENT en dev/staging pour tester le wizard signup de bout en
     * bout. Defaut false. En prod, garder false absolument.
     */
    private boolean testBypassEnabled = false;

    private Checkout checkout = new Checkout();
    private Portal portal = new Portal();

    public static class Checkout {
        private String successUrl;
        private String cancelUrl;
        public String getSuccessUrl() { return successUrl; }
        public void setSuccessUrl(String s) { this.successUrl = s; }
        public String getCancelUrl() { return cancelUrl; }
        public void setCancelUrl(String s) { this.cancelUrl = s; }
    }

    public static class Portal {
        private String returnUrl;
        public String getReturnUrl() { return returnUrl; }
        public void setReturnUrl(String s) { this.returnUrl = s; }
    }

    public BigDecimal getTvaRatePercent() { return tvaRatePercent; }
    public void setTvaRatePercent(BigDecimal t) { this.tvaRatePercent = t; }
    public boolean isTestBypassEnabled() { return testBypassEnabled; }
    public void setTestBypassEnabled(boolean v) { this.testBypassEnabled = v; }
    public String getSalesEmail() { return salesEmail; }
    public void setSalesEmail(String s) { this.salesEmail = s; }
    public String getFromEmail() { return fromEmail; }
    public void setFromEmail(String s) { this.fromEmail = s; }
    public Checkout getCheckout() { return checkout; }
    public void setCheckout(Checkout c) { this.checkout = c; }
    public Portal getPortal() { return portal; }
    public void setPortal(Portal p) { this.portal = p; }
}
