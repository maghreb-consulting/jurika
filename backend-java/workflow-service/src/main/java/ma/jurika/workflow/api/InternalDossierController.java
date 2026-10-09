package ma.jurika.workflow.api;

import ma.jurika.common.security.TenantContext;
import ma.jurika.workflow.application.DossierIdentityQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Endpoint interne service-to-service : identité société complète d'un dossier.
 *
 * <p>Consommé par ai-service (qui ne lit aucune base) pour enrichir l'objet
 * {@code societe} du payload PV depuis la BD au moment de la génération. Non exposé
 * par la gateway (seuls {@code /api/v1/**} le sont) → atteignable uniquement depuis le
 * réseau interne docker, d'où {@code /internal/**} permitAll dans la SecurityConfig
 * (même schéma que les endpoints internes d'ai-service / supervision / dataroom).
 *
 * <p>Le {@code workspaceId} est fourni par l'appelant (ai-service le dérive de son
 * JWT vérifié) et refiltré en SQL ({@code WHERE workspace_id = ?}). Lot L0 : il
 * devient aussi le workspace courant, sous lequel la RLS s'applique (rôle
 * {@code jurika_app}) ; le filtre SQL reste, les deux sont toujours exigés.
 */
@RestController
@RequestMapping("/internal/dossiers")
public class InternalDossierController {

    private final DossierIdentityQueryService identityService;

    public InternalDossierController(DossierIdentityQueryService identityService) {
        this.identityService = identityService;
    }

    /**
     * Renvoie l'identité société aplatie (clés alignées sur {@code SeancePvVarsBuilder}),
     * ou une map vide si le dossier est introuvable dans le workspace.
     */
    @GetMapping("/{dossierId}/identite")
    public Map<String, Object> identity(@PathVariable UUID dossierId,
                                        @RequestParam UUID workspaceId) {
        // Lot L0 (E16, WF1) : route sans JWT. Le workspace fourni est pose AVANT
        // le proxy transactionnel du service (la RLS le lit a l'ouverture de la
        // transaction), et retire en fin d'appel.
        TenantContext.set(workspaceId);
        try {
            return identityService.identity(workspaceId, dossierId);
        } finally {
            TenantContext.clear();
        }
    }
}
