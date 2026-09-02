package ma.jurika.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Emitter asynchrone qui publie chaque evenement d'audit sur l'exchange topic
 * {@link AuditExchangeConfig#EXCHANGE} (Sprint 2 / TASK 6 commit 3).
 *
 * <p>Le worker {@code AuditEventConsumer} de chaque service consomme la queue
 * dediee et ecrit dans la table {@code audit_log} locale via
 * {@link JdbcAuditEventEmitter}. Le decoupage publisher/consumer respecte la
 * contrainte du plan : l'emission d'audit ne doit jamais bloquer la transaction
 * metier (latence cible &lt; 5ms par appel @Auditable).
 */
public class RabbitAuditEventEmitter implements AuditEventEmitter {

    private static final Logger log = LoggerFactory.getLogger(RabbitAuditEventEmitter.class);

    private final RabbitTemplate rabbitTemplate;
    private final AuditEventEmitter fallback;

    public RabbitAuditEventEmitter(RabbitTemplate rabbitTemplate, AuditEventEmitter fallback) {
        this.rabbitTemplate = rabbitTemplate;
        this.fallback = fallback;
    }

    @Override
    public void emit(AuditEvent event) {
        String routingKey = AuditExchangeConfig.routingKey(event.sourceService(), event.action());
        try {
            rabbitTemplate.convertAndSend(AuditExchangeConfig.EXCHANGE, routingKey, event);
        } catch (AmqpException ex) {
            log.warn("audit.publish failed action={} rk={} : {} -- fallback to direct write",
                    event.action(), routingKey, ex.getMessage());
            if (fallback != null) {
                fallback.emit(event);
            }
        }
    }
}
