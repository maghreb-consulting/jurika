package ma.jurika.ticket.infrastructure.messaging;

import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.TicketEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class RabbitTicketEventPublisher implements TicketEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitTicketEventPublisher.class);
    private static final String EXCHANGE = "jurika.events";

    private final RabbitTemplate rabbitTemplate;

    public RabbitTicketEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishTicketCreated(Ticket t) {
        publish("ticket.created", baseMap(t));
    }

    @Override
    public void publishTicketStatusChanged(Ticket t, TicketStatut previous) {
        Map<String, Object> map = baseMap(t);
        map.put("previousStatut", previous.name());
        publish("ticket.status_changed", map);
    }

    @Override
    public void publishTicketAssigned(Ticket t, UUID previousAssigneId) {
        Map<String, Object> map = baseMap(t);
        map.put("previousAssigneId", previousAssigneId == null ? null : previousAssigneId.toString());
        publish("ticket.assigned", map);
    }

    private Map<String, Object> baseMap(Ticket t) {
        Map<String, Object> map = new HashMap<>();
        map.put("ticketId", t.id().toString());
        map.put("workspaceId", t.workspaceId().toString());
        map.put("reference", t.reference());
        map.put("type", t.type().name());
        map.put("statut", t.statut().name());
        map.put("assigneId", t.assigneId() == null ? null : t.assigneId().toString());
        // Dossier du ticket, publie pour les consommateurs de l'evenement. Lot L0
        // (E16a) : dataroom ne supprime plus la Data Room a l'annulation (RG-TKT-05).
        map.put("dossierId", t.dossierId() == null ? null : t.dossierId().toString());
        map.put("at", Instant.now().toString());
        return map;
    }

    private void publish(String routingKey, Map<String, ?> payload) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, routingKey, payload);
        } catch (Exception ex) {
            log.warn("Echec publication evenement {}: {}", routingKey, ex.getMessage());
        }
    }
}
