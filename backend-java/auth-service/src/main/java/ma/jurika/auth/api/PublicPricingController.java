package ma.jurika.auth.api;

import ma.jurika.common.billing.PlanCatalog;
import ma.jurika.common.billing.PlanCatalog.Plan;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 11 TASK 4 — Endpoint public servant la grille pricing au SPA
 * (page /app/billing) ET au marketing-site (fallback si bundle JS rate).
 *
 * <p>Sprint Beta (pricing-deploy) — refacto pour lire depuis
 * {@link PlanCatalog} (source unique de verite cross-stack). Les valeurs
 * MAD HT sont validees par directeur. Les quotas et lookup_keys Stripe
 * sont exposes pour permettre au frontend d'afficher les usages et de
 * brancher le PlanPicker sans dupliquer le catalogue.
 *
 * <p>Reponse schema :
 * <pre>{@code
 * {
 *   "tiers": [
 *     {
 *       "id": "essentiel",                     // workspace.selected_plan
 *       "code": "essentiel",                   // alias deprecate
 *       "label": "Essentiel",                  // affichage commercial (spec 2026-06-02)
 *       "target": "Petites structures juridiques",
 *       "price": 499,                          // mensuel HT (deprecate, garde Sprint 11)
 *       "annualPrice": 4999,                   // annuel HT (deprecate, garde Sprint 11)
 *       "currency": "MAD",
 *       "period": "mois",                      // deprecate, garde Sprint 11
 *       "monthly": { "priceMad": 499, "stripeLookupKey": "essentiel_monthly" },
 *       "yearly":  { "priceMad": 4999, "stripeLookupKey": "essentiel_yearly" },
 *       "quotas":  { "maxUsers": 2, "maxDossiers": 30, "maxStorageGb": 5 },
 *       "features": [...],
 *       "featured": false,
 *       "ribbon": null,
 *       "quoteOnly": false
 *     },
 *     ...
 *   ],
 *   "currency": "MAD",
 *   "trialDays": 14
 * }
 * }</pre>
 */
@RestController
@RequestMapping("/api/v1/public/pricing")
public class PublicPricingController {

    @GetMapping
    public Map<String, Object> getPricing() {
        List<Map<String, Object>> tiers = PlanCatalog.ALL.stream()
                .map(PublicPricingController::toTierDto)
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tiers", tiers);
        body.put("currency", "MAD");
        body.put("trialDays", 14);
        return body;
    }

    private static Map<String, Object> toTierDto(Plan p) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", p.internalCode());
        dto.put("code", p.internalCode());
        dto.put("label", p.displayLabel());
        dto.put("target", p.marketingTarget());
        dto.put("currency", "MAD");
        // Legacy fields (Sprint 11 contract — frontend marketing s'en sert encore).
        dto.put("price", p.priceMonthlyMad());
        dto.put("annualPrice", p.priceYearlyMad());
        dto.put("period", "mois");
        // Nouvelles structures monthly/yearly (Sprint Beta).
        if (p.hasStripePrice()) {
            dto.put("monthly", Map.of(
                    "priceMad", p.priceMonthlyMad(),
                    "stripeLookupKey", p.stripeLookupKeyMonthly()));
            dto.put("yearly", Map.of(
                    "priceMad", p.priceYearlyMad(),
                    "stripeLookupKey", p.stripeLookupKeyYearly()));
        }
        // Quotas exposes (-1 = illimite).
        Map<String, Object> quotas = new HashMap<>();
        quotas.put("maxUsers", p.quotas().maxUsers());
        quotas.put("maxDossiers", p.quotas().maxDossiers());
        quotas.put("maxStorageGb", p.quotas().maxStorageGb());
        quotas.put("usersUnlimited", p.quotas().isUsersUnlimited());
        quotas.put("dossiersUnlimited", p.quotas().isDossiersUnlimited());
        quotas.put("storageUnlimited", p.quotas().isStorageUnlimited());
        dto.put("quotas", quotas);
        dto.put("features", p.features());
        dto.put("featured", p.featured());
        if (p.ribbon() != null) dto.put("ribbon", p.ribbon());
        dto.put("quoteOnly", p.quoteOnly());
        return dto;
    }
}
