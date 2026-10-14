package ma.jurika.auth.api;

import ma.jurika.common.billing.FonctionForfait;
import ma.jurika.common.billing.PlanCatalog;
import ma.jurika.common.billing.PlanCatalog.Plan;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Grille tarifaire publique, lue dans {@link PlanCatalog} (source unique).
 *
 * <p>Lot L7 (E5) : grille du CDC 4.3 (prix HORS TAXES, annuel et mensuel) et fonctions du 4.5.
 * Chaque forfait expose ses quotas (dossiers par mois d'abonnement, acces employes en plus du
 * superviseur, stockage) et, pour chaque fonction distinctive, si elle est incluse.
 *
 * <pre>{@code
 * { "tiers": [ { "code": "essentiel", "label": "Essentiel", "rang": 1, "quoteOnly": false,
 *                "monthly": { "priceMad": 375 }, "yearly": { "priceMad": 3750 },
 *                "totalAnnuelEnMensuelMad": 4500,
 *                "quotas": { "maxUsers": 1, "maxDossiers": 25, "maxStorageGb": 5, ... },
 *                "fonctions": [ { "code": "ALERTES_DELAIS", "libelle": "...", "incluse": false }, ... ] } ],
 *   "currency": "MAD", "prixHorsTaxes": true }
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
        body.put("prixHorsTaxes", true);
        return body;
    }

    private static Map<String, Object> toTierDto(Plan p) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", p.internalCode());
        dto.put("code", p.internalCode());
        dto.put("label", p.displayLabel());
        dto.put("rang", p.rang());
        dto.put("currency", "MAD");
        dto.put("quoteOnly", p.quoteOnly());
        if (!p.quoteOnly()) {
            dto.put("monthly", Map.of("priceMad", p.priceMonthlyMad()));
            dto.put("yearly", Map.of("priceMad", p.priceYearlyMad()));
            dto.put("totalAnnuelEnMensuelMad", p.priceMonthlyMad() * 12);
        }
        Map<String, Object> quotas = new LinkedHashMap<>();
        quotas.put("maxUsers", p.quotas().maxUsers());
        quotas.put("maxDossiers", p.quotas().maxDossiers());
        quotas.put("maxStorageGb", p.quotas().maxStorageGb());
        quotas.put("usersUnlimited", p.quotas().isUsersUnlimited());
        quotas.put("dossiersUnlimited", p.quotas().isDossiersUnlimited());
        quotas.put("storageUnlimited", p.quotas().isStorageUnlimited());
        dto.put("quotas", quotas);
        dto.put("fonctions", Arrays.stream(FonctionForfait.values())
                .map(f -> Map.<String, Object>of("code", f.name(), "libelle", f.libelle(), "incluse", p.inclut(f)))
                .toList());
        return dto;
    }
}
