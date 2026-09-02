package ma.jurika.common.observability;

import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Readiness probe for RabbitMQ. Opens a short-lived connection via the configured
 * {@link ConnectionFactory} and reports {@code UP} only if the connection is open.
 *
 * <p>Registered automatically by {@link ObservabilityAutoConfiguration} whenever a
 * {@code ConnectionFactory} bean is present. Bean name is {@code rabbit} so it can be
 * referenced in {@code management.endpoint.health.group.readiness.include}.
 */
public class RabbitMQHealthIndicator implements HealthIndicator {

    private final ConnectionFactory connectionFactory;

    public RabbitMQHealthIndicator(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        try (Connection connection = connectionFactory.createConnection()) {
            if (connection.isOpen()) {
                return Health.up()
                        .withDetail("host", connectionFactory.getHost())
                        .withDetail("port", connectionFactory.getPort())
                        .build();
            }
            return Health.down().withDetail("reason", "connection-not-open").build();
        } catch (Exception ex) {
            return Health.down(ex)
                    .withDetail("host", connectionFactory.getHost())
                    .withDetail("port", connectionFactory.getPort())
                    .build();
        }
    }
}
