package ma.jurika.auth.api;

import ma.jurika.common.billing.PlanLimitsService;
import ma.jurika.common.billing.PlanLimitsService.UsageSnapshot;
import ma.jurika.common.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint Beta (pricing-deploy) — TASK 3.
 *
 * <p>Endpoint d'observation de la consommation du plan tarifaire courant
 * pour le workspace de l'utilisateur authentifie. Utilise par le frontend
 * pour afficher les badges "28/30 dossiers", "5/6 utilisateurs", etc., et
 * pour declencher l'upsell anticipe (banner J-3 avant la limite).
 *
 * <p>Pas d'enforcement ici, juste lecture. Sert aussi de check passif au
 * smoke-test pour valider la pipeline plan-limits sans creer d'effet de
 * bord.
 */
@RestController
@RequestMapping("/api/v1/workspace/usage")
public class UsageController {

    private final PlanLimitsService planLimitsService;

    @Autowired
    public UsageController(@Autowired(required = false) PlanLimitsService planLimitsService) {
        this.planLimitsService = planLimitsService;
    }

    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true) // Lot L0 (E13b) : compteurs sous RLS
    public ResponseEntity<UsageSnapshot> getUsage(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        if (planLimitsService == null) {
            // Enforcement coupe (tests). Renvoyer un snapshot vide est plus utile
            // qu'un 503 pour le frontend qui peut juste cacher le widget.
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(planLimitsService.snapshot(user.workspaceId()));
    }
}
