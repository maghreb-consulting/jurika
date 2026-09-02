package ma.jurika.dataroom.infrastructure.messaging;

import ma.jurika.dataroom.domain.port.DataroomEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Sprint 7 / TASK 5.2 -- Publisher RabbitMQ pour dataroom.events.
 *
 * Routing key : "dossier.{dossierId}" pour permettre au realtime-service
 * de binder une queue specifique a un dossier ou un wildcard "dossier.*".
 *
 * Best-effort : si Rabbit est down ou non configure, l'echec est logge mais
 * pas propage (le cas d'usage realtime ne doit pas casser un upload).
 *
 * Sprint 14 ter (C1 bugfix) : @ConditionalOnBean(RabbitTemplate) -- evite
 * BeanCreationException quand RabbitAutoConfiguration est exclu (cf. IT excluant amqp).
 * Un NoopDataroomEventPublisher prend le relai en l'absence de Rabbit.
 */
@Component
@ConditionalOnBean(RabbitTemplate.class)
@Primary
public class RabbitDataroomEventPublisher implements DataroomEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitDataroomEventPublisher.class);

    private final RabbitTemplate template;

    public RabbitDataroomEventPublisher(
            @Qualifier("dataroomEventsRabbitTemplate") RabbitTemplate template) {
        this.template = template;
    }

    @Override
    public void publish(DataroomDocumentEvent event) {
        try {
            String routingKey = "dossier." + event.dossierId();
            template.convertAndSend(routingKey, event);
            log.debug("Dataroom event {} publie : routingKey={}", event.kind(), routingKey);
        } catch (AmqpException ex) {
            log.warn("Echec publication dataroom event {} pour dossier {} : {}",
                    event.kind(), event.dossierId(), ex.getMessage());
        }
    }
}
