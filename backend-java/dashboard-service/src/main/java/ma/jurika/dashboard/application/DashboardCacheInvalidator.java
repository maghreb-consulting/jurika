package ma.jurika.dashboard.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Sprint 10 RG-DASH-03 -- listener events RabbitMQ qui invalide les
 * caches dashboard d'un workspace impacte.
 *
 * Exchange ecoute : dataroom.events (cf Sprint 7 RabbitDataroomEventPublisher).
 * Pour les events ticket.* on ne fait rien V1 (pas d exchange dedie) : la
 * safety net TTL 60s rattrape automatiquement.
 */
@Component
public class DashboardCacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(DashboardCacheInvalidator.class);
    public static final String QUEUE = "dashboard.cache-invalidation";
    public static final String EXCHANGE = "dataroom.events";

    private final DashboardQueryService queryService;

    public DashboardCacheInvalidator(DashboardQueryService queryService) {
        this.queryService = queryService;
    }

    @Bean
    public Queue dashboardInvalidationQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public TopicExchange dataroomEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Binding dashboardBinding(Queue dashboardInvalidationQueue,
                                      TopicExchange dataroomEventsExchange) {
        return BindingBuilder.bind(dashboardInvalidationQueue)
                .to(dataroomEventsExchange).with("#"); // catch all
    }

    @RabbitListener(queues = QUEUE)
    public void onEvent(Map<String, Object> event) {
        try {
            Object wsRaw = event.get("workspaceId");
            if (wsRaw == null) return;
            UUID ws = UUID.fromString(wsRaw.toString());
            long n = queryService.invalidateWorkspace(ws);
            log.debug("Dashboard cache invalidated workspace={} keys_deleted={}", ws, n);
        } catch (Exception e) {
            log.warn("Dashboard invalidation failed : {}", e.getMessage());
        }
    }
}
