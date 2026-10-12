package ma.jurika.dataroom.infrastructure.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sprint 7 / TASK 5.2 -- Config Rabbit pour l'exchange dataroom.events.
 *
 * Topic exchange distinct de jurika.events (audit log Sprint 2) pour
 * permettre un binding cote realtime-service plus simple :
 *
 *   dataroom.events  --routing key "dossier.{id}"-->  realtime push
 *
 * Le messageConverter Jackson2Json est local pour ne pas entrer en conflit
 * avec celui declare ailleurs si dataroom-service est demarre avec un
 * autre service dans le meme jar (improbable, mais robustesse).
 */
@Configuration
@ConditionalOnBean(ConnectionFactory.class)
public class DataroomRabbitConfig {

    public static final String EXCHANGE_DATAROOM = "dataroom.events";

    @Bean
    public TopicExchange dataroomExchange() {
        return new TopicExchange(EXCHANGE_DATAROOM, true, false);
    }

    @Bean(name = "dataroomEventsRabbitTemplate")
    public RabbitTemplate dataroomEventsRabbitTemplate(ConnectionFactory cf) {
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(jacksonConverter());
        template.setExchange(EXCHANGE_DATAROOM);
        return template;
    }

    /**
     * Lot L5 (E9, RG-COM-09) : echange partage {@code jurika.events} (declare aussi
     * par auth-service ; la declaration est idempotente), sur lequel
     * {@link RabbitCourrielsClient} publie les demandes de courriel au client avec
     * le gabarit ci-dessus (echange explicite a l'envoi). Pas de second
     * RabbitTemplate : l'emetteur d'audit s'injecte par type et deviendrait ambigu.
     */
    @Bean
    public TopicExchange jurikaEventsExchange() {
        return new TopicExchange(ma.jurika.common.courriel.CourrielClientEvenement.ECHANGE, true, false);
    }

    private MessageConverter jacksonConverter() {
        // Jackson2JsonMessageConverter ne s'auto-discover pas si un autre converter
        // est deja primary -- on en cree un local pour ce template.
        return new Jackson2JsonMessageConverter();
    }
}
