package ma.jurika.ai.workflow.identity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lot L3 (regle des variables) : la plateforme RECLAME les donnees externes manquantes
 * d'un document genere ; celles qui ne manquent plus sont closes (document regenere).
 */
public interface ReclamationDonnees {

    void enregistrer(UUID workspaceId, UUID ticketId, String workflowCode, String templateCode,
                     List<Map<String, String>> donnees);
}
