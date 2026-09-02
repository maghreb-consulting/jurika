package ma.jurika.dataroom.infrastructure.messaging;

import ma.jurika.dataroom.domain.port.DataroomEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sprint 14 ter (C1) -- fallback inconditionnel "no-op" pour DataroomEventPublisher.
 *
 * Pattern : ce bean est TOUJOURS enregistre (pas de @ConditionalOn...) mais
 * sans @Primary. L'implementation Rabbit ({@link RabbitDataroomEventPublisher})
 * est marquee @Primary ET @ConditionalOnBean(RabbitTemplate.class) :
 *   - Rabbit disponible -> 2 beans coexistent, autowiring prend le @Primary Rabbit
 *   - Rabbit absent      -> 1 seul bean (Noop), pas d'ambiguite
 *
 * Plus deterministe que @ConditionalOnMissingBean qui souffre d'ordering
 * lors du scan @Component (Spring n'evalue pas les conditions des components dans
 * un ordre garanti, donc Missing peut se declencher au mauvais moment).
 */
@Configuration
public class NoopDataroomEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoopDataroomEventPublisher.class);

    @Bean(name = "fallbackDataroomEventPublisher")
    public DataroomEventPublisher fallbackDataroomEventPublisher() {
        log.info("DataroomEventPublisher Noop bean enregistre (fallback IT ou absence Rabbit)");
        return event -> {
            if (log.isDebugEnabled()) {
                log.debug("Noop dataroom event {} pour dossier {} ignore",
                        event.kind(), event.dossierId());
            }
        };
    }
}
