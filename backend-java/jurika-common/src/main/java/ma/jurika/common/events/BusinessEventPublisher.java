package ma.jurika.common.events;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Sprint 12 — Feign client de publication d'evenements business backend → backend.
 *
 * <p>Sprint 11 TASK 6 a cree {@code POST /internal/events} cote supervision-service
 * pour ingerer les business_events. Cote frontend, c'est appele via
 * {@code /api/v1/public/events} rewrite. Cote backend (billing webhook handlers,
 * SubscribeUseCase, etc.), on appelle directement supervision-service via Feign.
 *
 * <p>Optionnel : header X-Internal-Token (active si
 * {@code jurika.internal.token-required=true} cote supervision).
 */
@FeignClient(name = "supervision-service", url = "${jurika.supervision.internal-url:}")
public interface BusinessEventPublisher {

    @PostMapping("/internal/events")
    void publish(@RequestBody EventPayload payload,
                  @RequestHeader(value = "X-Internal-Token", required = false) String token);

    /** Payload aligne avec {@code InternalEventsController.EventPayload}. */
    class EventPayload {
        public String eventType;
        public UUID workspaceId;
        public Map<String, Object> properties;
        public Instant occurredAt;
        public String source = "backend";

        public static EventPayload of(String type, UUID workspaceId, Map<String, Object> props) {
            EventPayload p = new EventPayload();
            p.eventType = type;
            p.workspaceId = workspaceId;
            p.properties = props;
            p.occurredAt = Instant.now();
            return p;
        }
    }
}
