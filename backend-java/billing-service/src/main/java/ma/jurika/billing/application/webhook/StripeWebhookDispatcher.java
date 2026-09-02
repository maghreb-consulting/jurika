package ma.jurika.billing.application.webhook;

import com.stripe.model.Event;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.WebhookEventJpaRepository;
import ma.jurika.billing.infrastructure.persistence.WebhookEventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sprint 12 — orchestre la persistance + le dispatch des events Stripe.
 *
 * <p>Pipeline :
 * <ol>
 *   <li>{@link #ingest(Event, String)} insert dans {@code webhook_events}
 *       (UNIQUE stripe_event_id -> idempotency naturelle)</li>
 *   <li>Dispatch vers le {@link StripeEventHandler} matching</li>
 *   <li>Marque processed=TRUE + timestamp</li>
 *   <li>En cas d'exception, log, marque error_message + return — Stripe
 *       retry pendant 3 jours</li>
 * </ol>
 */
@Service
public class StripeWebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookDispatcher.class);

    private final WebhookEventJpaRepository webhookEventRepo;
    private final Map<String, StripeEventHandler> handlersByType;

    public StripeWebhookDispatcher(WebhookEventJpaRepository webhookEventRepo,
                                    List<StripeEventHandler> handlers) {
        this.webhookEventRepo = webhookEventRepo;
        this.handlersByType = handlers.stream()
                .collect(Collectors.toMap(StripeEventHandler::eventType, h -> h));
        log.info("StripeWebhookDispatcher initialise avec {} handlers : {}",
                handlersByType.size(), handlersByType.keySet());
    }

    /**
     * Ingest un event Stripe : persistance + dispatch atomique.
     *
     * @return {@code true} si l'event a ete traite (premier appel) ;
     *         {@code false} si deja vu (idempotent skip).
     */
    @Transactional
    public boolean ingest(Event event, String rawPayload) {
        if (webhookEventRepo.existsByStripeEventId(event.getId())) {
            log.info("Event Stripe deja recu, skip idempotent. event_id={} type={}",
                    event.getId(), event.getType());
            return false;
        }

        WebhookEventEntity entity = new WebhookEventEntity();
        entity.setStripeEventId(event.getId());
        entity.setEventType(event.getType());
        entity.setPayload(rawPayload);
        entity.setAttempts(1);
        try {
            entity = webhookEventRepo.save(entity);
        } catch (DataIntegrityViolationException race) {
            // Race condition (2 receptions concurrentes du meme event) — l'autre a gagne.
            log.info("Race condition sur event_id={}, l'autre transaction a gagne", event.getId());
            return false;
        }

        StripeEventHandler handler = handlersByType.get(event.getType());
        if (handler == null) {
            log.debug("Event Stripe non gere (no handler) — log only. type={}", event.getType());
            entity.setProcessed(true);
            entity.setProcessedAt(Instant.now());
            return true;
        }

        try {
            handler.handle(event);
            entity.setProcessed(true);
            entity.setProcessedAt(Instant.now());
            log.info("Event Stripe traite type={} id={}", event.getType(), event.getId());
            return true;
        } catch (RuntimeException e) {
            // Log + persiste l'erreur, mais on ne re-throw PAS : Stripe re-essaiera
            // l'event automatiquement (jusqu'a 3 jours). Le client recoit 200 OK
            // pour eviter une boucle de retry agressive sur erreur deterministe.
            log.error("Echec traitement event Stripe type={} id={}", event.getType(), event.getId(), e);
            entity.setErrorMessage(e.getClass().getSimpleName() + ": " + e.getMessage());
            entity.setProcessed(false);
            return false;
        }
    }
}
