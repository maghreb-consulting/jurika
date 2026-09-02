package ma.jurika.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.minio.MinioClient;
import jakarta.servlet.Filter;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the observability filters and health indicators shared by every JURIKA service.
 *
 * <ul>
 *   <li>{@link CorrelationIdFilter} — servlet-only (gateway is reactive and registers
 *       its own {@code CorrelationIdWebFilter}).</li>
 *   <li>{@link RabbitMQHealthIndicator} — registered when a Rabbit
 *       {@code ConnectionFactory} bean is present (auth/dataroom/ticket/workflow).</li>
 *   <li>{@link MinIOHealthIndicator} — registered when a {@code MinioClient} bean is
 *       present (dataroom/ticket).</li>
 * </ul>
 */
@AutoConfiguration
public class ObservabilityAutoConfiguration {

    @Configuration
    @ConditionalOnClass(Filter.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class ServletFilterConfig {

        @Bean
        public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
            FilterRegistrationBean<CorrelationIdFilter> reg =
                    new FilterRegistrationBean<>(new CorrelationIdFilter());
            reg.setOrder(CorrelationIdFilter.ORDER);
            reg.addUrlPatterns("/*");
            return reg;
        }
    }

    @Configuration
    @ConditionalOnClass(ConnectionFactory.class)
    @ConditionalOnBean(ConnectionFactory.class)
    static class RabbitHealthConfig {

        @Bean("rabbit")
        public HealthIndicator rabbitHealthIndicator(ConnectionFactory connectionFactory) {
            return new RabbitMQHealthIndicator(connectionFactory);
        }
    }

    @Configuration
    @ConditionalOnClass(MinioClient.class)
    @ConditionalOnBean(MinioClient.class)
    static class MinioHealthConfig {

        @Bean("minio")
        public HealthIndicator minioHealthIndicator(MinioClient client,
                                                    @Value("${jurika.minio.bucket:jurika}") String bucket) {
            return new MinIOHealthIndicator(client, bucket);
        }
    }

    /**
     * Sprint 2 / TASK 2 — façade {@link BusinessMetrics} pour les compteurs business
     * (jurika.auth.login.*, jurika.tickets.created, etc.). Activée quand un
     * {@link MeterRegistry} est présent (donc dès qu'actuator + prometheus sont là).
     *
     * Sprint 14 ter C1 — fallback {@link SimpleMeterRegistry} pour les profils de
     * test qui n'embarquent pas Spring Boot Actuator metrics (typiquement les IT
     * Testcontainers qui excluent les AutoConfigurations metrics pour rester legers).
     * En prod, Spring Boot actuator fournit deja un MeterRegistry plus riche
     * (Prometheus), notre fallback laisse passer la main via @ConditionalOnMissingBean.
     */
    @Configuration
    @ConditionalOnClass(MeterRegistry.class)
    static class BusinessMetricsConfig {

        @Bean
        @ConditionalOnMissingBean(MeterRegistry.class)
        public MeterRegistry fallbackSimpleMeterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        public BusinessMetrics businessMetrics(MeterRegistry registry) {
            return new BusinessMetrics(registry);
        }
    }
}
