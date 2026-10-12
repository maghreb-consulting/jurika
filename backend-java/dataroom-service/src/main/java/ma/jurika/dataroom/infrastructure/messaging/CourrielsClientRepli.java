package ma.jurika.dataroom.infrastructure.messaging;

import ma.jurika.dataroom.domain.port.CourrielsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Lot L5 (E9) : repli quand RabbitMQ n'est pas configure (tests d'integration
 * sans courtier). Meme schema que {@link NoopDataroomEventPublisher} : toujours
 * enregistre, sans {@code @Primary} ; l'implementation Rabbit l'emporte quand
 * elle existe. Ici, une demande de courriel n'est PAS avalee en silence : elle
 * est journalisee en WARN (le client ne sera pas averti).
 */
@Configuration
public class CourrielsClientRepli {

    private static final Logger log = LoggerFactory.getLogger(CourrielsClientRepli.class);

    @Bean(name = "repliCourrielsClient")
    public CourrielsClient repliCourrielsClient() {
        return evenement -> log.warn("Courriel client NON demande (RabbitMQ absent) : {} cabinet={} client={}",
                evenement.type(), evenement.workspaceId(), evenement.clientId());
    }
}
