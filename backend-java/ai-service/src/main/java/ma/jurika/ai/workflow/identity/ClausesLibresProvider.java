package ma.jurika.ai.workflow.identity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lot L3 (RG-GEN-06) : clauses libres du ticket, relues au magasin a chaque generation. */
public interface ClausesLibresProvider {

    List<Map<String, Object>> lister(UUID workspaceId, UUID ticketId);
}
