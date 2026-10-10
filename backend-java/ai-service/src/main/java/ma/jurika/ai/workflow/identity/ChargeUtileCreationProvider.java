package ma.jurika.ai.workflow.identity;

import java.util.Map;
import java.util.UUID;

/**
 * Lot L3 (P2, RG-VAR-05) : charge utile de la creation, construite par workflow-service
 * depuis le magasin du ticket. ai-service ne la recoit plus du navigateur.
 */
public interface ChargeUtileCreationProvider {

    Map<String, Object> charger(UUID workspaceId, UUID ticketId, UUID employeId);
}
