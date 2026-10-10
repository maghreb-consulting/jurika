package ma.jurika.ai.workflow.identity;

import feign.FeignException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lot L3 : clauses libres lues chez workflow-service ; un echec n'est pas avale (une clause oubliee en silence). */
@Component
public class RemoteClausesLibresProvider implements ClausesLibresProvider {

    private final WorkflowIdentityClient client;

    public RemoteClausesLibresProvider(WorkflowIdentityClient client) {
        this.client = client;
    }

    @Override
    public List<Map<String, Object>> lister(UUID workspaceId, UUID ticketId) {
        try {
            List<Map<String, Object>> l = client.clausesLibres(ticketId, workspaceId);
            return l == null ? List.of() : l;
        } catch (FeignException e) {
            throw new IdentiteSocieteIndisponibleException(
                    "Les clauses libres du ticket n'ont pas pu etre lues (ticket " + ticketId + ")", e);
        }
    }
}
