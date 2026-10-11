package ma.jurika.workflow.infrastructure.client;

import ma.jurika.workflow.application.TransitionsTicket;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.UUID;

/** Lot L4c (E1) : transition d'un ticket demandee a ticket-service (route interne). */
@FeignClient(name = "ticket-service", contextId = "ticketTransition")
public interface TicketTransitionFeignClient {

    @PostMapping("/internal/tickets/{ticketId}/transition")
    TransitionsTicket.Reponse transition(@PathVariable("ticketId") UUID ticketId, @RequestBody Map<String, Object> demande);
}
