package ma.jurika.common.audit;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Active automatiquement {@link AuditAspect} dans tous les services qui
 * dependent de jurika-common (a condition que spring-aop soit present).
 * <p>
 * Sprint 2 / TASK 6 :
 * <ul>
 *   <li>Si {@code RabbitTemplate} est present → publisher async sur exchange {@code audit.events}.</li>
 *   <li>JdbcTemplate present → consumer ecrit dans {@code audit_log} local + fallback direct.</li>
 * </ul>
 * Si {@code jurika.audit.async-enabled=false}, on revient au mode JDBC direct.
 */
@AutoConfiguration(after = {JdbcTemplateAutoConfiguration.class, RabbitAutoConfiguration.class})
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
public class AuditAutoConfiguration {

    @Bean
    public AuditAspect auditAspect(ObjectProvider<AuditEventEmitter> emitterProvider,
                                   @Value("${spring.application.name:jurika}") String sourceService) {
        return new AuditAspect(emitterProvider.getIfAvailable(), sourceService);
    }

    /**
     * Defaut {@link JdbcAuditEventEmitter} : ecriture directe en base. Toujours
     * cree quand un JdbcTemplate existe — il sert de fallback au publisher Rabbit.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcTemplate.class)
    static class JdbcAuditEventEmitterAutoConfiguration {

        // Conditional sur l'absence d'un AuditEventEmitter deja defini par le
        // service (ex: workflow-service / dataroom-service ont historiquement
        // leur propre @Bean auditEventEmitter -> ne pas creer de doublon).
        @Bean("jdbcAuditEventEmitter")
        @ConditionalOnMissingBean(AuditEventEmitter.class)
        public AuditEventEmitter jdbcAuditEventEmitter(JdbcTemplate jdbc) {
            return new JdbcAuditEventEmitter(jdbc);
        }

        @Bean
        @ConditionalOnMissingBean(AuditEventConsumer.class)
        public AuditEventConsumer auditEventConsumer(JdbcTemplate jdbc) {
            return new AuditEventConsumer(jdbc);
        }
    }

    /**
     * Mode asynchrone (par defaut). Le publisher Rabbit devient le bean primary
     * — l'AuditAspect l'utilise. Le fallback Jdbc reste injectable comme bean nomme.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RabbitTemplate.class)
    @ConditionalOnBean(RabbitTemplate.class)
    @ConditionalOnProperty(name = "jurika.audit.async-enabled", havingValue = "true", matchIfMissing = true)
    static class RabbitAuditEventEmitterAutoConfiguration {

        @Bean
        @Primary
        public AuditEventEmitter rabbitAuditEventEmitter(
                RabbitTemplate rabbitTemplate,
                @Qualifier("jdbcAuditEventEmitter") ObjectProvider<AuditEventEmitter> jdbcFallback) {
            // Injection ciblee par nom de bean -> evite le cycle d'auto-reference
            // qu'un ObjectProvider<AuditEventEmitter> generique declencherait
            // (Spring tente alors d'instancier le rabbit emitter pour le candidats list).
            return new RabbitAuditEventEmitter(rabbitTemplate, jdbcFallback.getIfAvailable());
        }

        @Bean
        public TopicExchange auditEventsExchange() {
            return new TopicExchange(AuditExchangeConfig.EXCHANGE, true, false);
        }

        @Bean
        public Queue auditQueue(@Value("${spring.application.name:jurika}") String serviceName) {
            return new Queue(AuditExchangeConfig.ROUTING_KEY_PREFIX + serviceName, true);
        }

        @Bean
        public Binding auditBinding(Queue auditQueue, TopicExchange auditEventsExchange,
                                    @Value("${spring.application.name:jurika}") String serviceName) {
            return BindingBuilder.bind(auditQueue).to(auditEventsExchange)
                    .with(AuditExchangeConfig.ROUTING_KEY_PREFIX + serviceName + ".#");
        }

        @Bean
        public AuditQueueListener auditQueueListener(AuditEventConsumer consumer) {
            return new AuditQueueListener(consumer);
        }
    }

    /**
     * Listener cree par programme pour eviter de mettre {@code @RabbitListener} dans
     * une classe configurable a chaud (le nom de queue depend de spring.application.name).
     */
    public static class AuditQueueListener {

        private final AuditEventConsumer consumer;

        AuditQueueListener(AuditEventConsumer consumer) {
            this.consumer = consumer;
        }

        @RabbitListener(queues = "#{auditQueue.name}")
        public void onMessage(AuditEventEmitter.AuditEvent event) {
            consumer.onMessage(event);
        }
    }
}
