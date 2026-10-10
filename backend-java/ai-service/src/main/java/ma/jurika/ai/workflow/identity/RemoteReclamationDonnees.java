package ma.jurika.ai.workflow.identity;

import feign.FeignException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lot L3 : reclamation enregistree chez workflow-service. Un echec n'est pas avale :
 * une donnee externe non reclamee serait oubliee sans bruit (motif 9).
 */
@Component
public class RemoteReclamationDonnees implements ReclamationDonnees {

    private final WorkflowIdentityClient client;

    public RemoteReclamationDonnees(WorkflowIdentityClient client) {
        this.client = client;
    }

    @Override
    public void enregistrer(UUID workspaceId, UUID ticketId, String workflowCode, String templateCode,
                            List<Map<String, String>> donnees) {
        try {
            client.donneesAttendues(ticketId, workspaceId,
                    Map.of("workflowCode", workflowCode, "templateCode", templateCode, "donnees", donnees));
        } catch (FeignException e) {
            throw new IdentiteSocieteIndisponibleException(
                    "La reclamation des donnees a obtenir n'a pas pu etre enregistree (ticket " + ticketId + ")", e);
        }
    }
}
