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

    private MessageConverter jacksonConverter() {
        // Jackson2JsonMessageConverter ne s'auto-discover pas si un autre converter
        // est deja primary -- on en cree un local pour ce template.
        return new Jackson2JsonMessageConverter();
    }
}
