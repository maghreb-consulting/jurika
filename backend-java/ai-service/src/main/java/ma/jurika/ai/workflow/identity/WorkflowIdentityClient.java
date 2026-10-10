package ma.jurika.ai.workflow.identity;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;
import java.util.UUID;

/**
 * Client Feign vers workflow-service : identité société complète d'un dossier.
 *
 * <p>Cible l'endpoint interne {@code GET /internal/dossiers/{dossierId}/identite}
 * (permitAll côté workflow-service, non exposé par la gateway). Résolution du host
 * via Eureka (nom logique {@code workflow-service}).
 */
@FeignClient(name = "workflow-service")
public interface WorkflowIdentityClient {

    @GetMapping("/internal/dossiers/{dossierId}/identite")
    Map<String, Object> identity(@PathVariable("dossierId") UUID dossierId,
                                 @RequestParam("workspaceId") UUID workspaceId);

    /** Lot L3 (P2) : charge utile de la creation, construite depuis le magasin du ticket. */
    @GetMapping("/internal/tickets/{ticketId}/charge-utile-creation")
    Map<String, Object> chargeUtileCreation(@PathVariable("ticketId") UUID ticketId,
                                            @RequestParam("workspaceId") UUID workspaceId,
                                            @RequestParam("employeId") UUID employeId);
}
