package ma.jurika.auth.infrastructure.messaging;

import ma.jurika.auth.domain.port.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class RabbitEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitEventPublisher.class);
    private static final String EXCHANGE = "jurika.events";

    private final RabbitTemplate rabbitTemplate;

    public RabbitEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishUserRegistered(UUID workspaceId, UUID userId, String email) {
        publish("user.registered", Map.of(
                "workspaceId", workspaceId.toString(),
                "userId", userId.toString(),
                "email", email,
                "at", Instant.now().toString()));
    }

    @Override
    public void publishUserLoggedIn(UUID workspaceId, UUID userId) {
        publish("user.logged_in", Map.of(
                "workspaceId", workspaceId.toString(),
                "userId", userId.toString(),
                "at", Instant.now().toString()));
    }

    @Override
    public void publishUser2faEnabled(UUID workspaceId, UUID userId) {
        publish("user.2fa_enabled", Map.of(
                "workspaceId", workspaceId.toString(),
                "userId", userId.toString(),
                "at", Instant.now().toString()));
    }

    @Override
    public void publishWorkspaceCreated(UUID workspaceId, String code) {
        publish("workspace.created", Map.of(
                "workspaceId", workspaceId.toString(),
                "code", code,
                "at", Instant.now().toString()));
    }

    private void publish(String routingKey, Map<String, ?> payload) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, routingKey, payload);
        } catch (Exception ex) {
            log.warn("Echec publication evenement {}: {}", routingKey, ex.getMessage());
        }
    }
}
