package ma.jurika.common.observability;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMQHealthIndicatorTest {

    @Test
    void reportsUpWhenConnectionOpens() {
        ConnectionFactory factory = Mockito.mock(ConnectionFactory.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(factory.createConnection()).thenReturn(connection);
        Mockito.when(connection.isOpen()).thenReturn(true);
        Mockito.when(factory.getHost()).thenReturn("localhost");
        Mockito.when(factory.getPort()).thenReturn(5672);

        Health health = new RabbitMQHealthIndicator(factory).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("host", "localhost").containsEntry("port", 5672);
        Mockito.verify(connection).close();
    }

    @Test
    void reportsDownWhenConnectionClosed() {
        ConnectionFactory factory = Mockito.mock(ConnectionFactory.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(factory.createConnection()).thenReturn(connection);
        Mockito.when(connection.isOpen()).thenReturn(false);

        Health health = new RabbitMQHealthIndicator(factory).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "connection-not-open");
    }

    @Test
    void reportsDownWhenConnectionFactoryThrows() {
        ConnectionFactory factory = Mockito.mock(ConnectionFactory.class);
        Mockito.when(factory.createConnection())
                .thenThrow(new org.springframework.amqp.AmqpConnectException(new java.net.ConnectException("refused")));
        Mockito.when(factory.getHost()).thenReturn("localhost");
        Mockito.when(factory.getPort()).thenReturn(5672);

        Health health = new RabbitMQHealthIndicator(factory).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }
}
