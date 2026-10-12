package ma.jurika.auth.infrastructure.messaging;

import ma.jurika.common.courriel.CourrielClientEvenement;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Lot L5 (E9, RG-COM-09) : file des courriels a envoyer aux clients.
 *
 * <pre>
 *   jurika.events --courriel.client.#--> auth.courriels-client
 *   (refus ou echec d'envoi) --dead-letter--> auth.courriels-client.echecs
 * </pre>
 *
 * Un courriel qui ne part pas n'est ni avale ni rejoue en boucle : il est
 * journalise (ERROR) puis range, intact, dans la file d'echecs, ou il reste
 * consultable et rejouable depuis la console RabbitMQ.
 */
@Configuration
public class CourrielsClientRabbitConfig {

    public static final String FILE = "auth.courriels-client";
    public static final String FILE_ECHECS = "auth.courriels-client.echecs";

    @Bean
    public Queue courrielsClientFile() {
        return QueueBuilder.durable(FILE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", FILE_ECHECS)
                .build();
    }

    @Bean
    public Queue courrielsClientFileEchecs() {
        return QueueBuilder.durable(FILE_ECHECS).build();
    }

    @Bean
    public Binding courrielsClientLiaison(@Qualifier("courrielsClientFile") Queue courrielsClientFile,
                                          @Qualifier("eventsExchange") TopicExchange eventsExchange) {
        return BindingBuilder.bind(courrielsClientFile).to(eventsExchange)
                .with(CourrielClientEvenement.ROUTAGE_PREFIXE + "#");
    }
}
