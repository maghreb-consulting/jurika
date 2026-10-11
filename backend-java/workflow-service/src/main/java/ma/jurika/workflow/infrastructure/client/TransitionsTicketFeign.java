package ma.jurika.workflow.infrastructure.client;

import ma.jurika.workflow.application.TransitionsTicket;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Lot L4c (E1) : adaptateur Feign du port {@link TransitionsTicket}. */
@Component
public class TransitionsTicketFeign implements TransitionsTicket {

    private final TicketTransitionFeignClient client;

    public TransitionsTicketFeign(TicketTransitionFeignClient client) {
        this.client = client;
    }

    @Override
    public Reponse demander(UUID workspaceId, UUID ticketId, String attendu, String cible, String raison, UUID acteurId) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("workspaceId", workspaceId);
        d.put("attendu", attendu);
        d.put("cible", cible);
        d.put("raison", raison);
        d.put("acteurId", acteurId);
        return client.transition(ticketId, d);
    }
}
