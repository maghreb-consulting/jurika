package ma.jurika.billing.application.webhook;

import com.stripe.model.Event;

/**
 * Sprint 12 — handler par type d'event Stripe (Strategy pattern).
 *
 * <p>Implementations injectees dans {@code StripeWebhookDispatcher} qui
 * route par {@link #eventType()}.
 */
public interface StripeEventHandler {

    /** Identifie l'event Stripe traite (ex: "checkout.session.completed"). */
    String eventType();

    /**
     * Traitement metier de l'event. Doit etre idempotent : si appele deux fois
     * avec le meme event, le second call doit etre un no-op (ou logger un warn
     * sans crash).
     */
    void handle(Event event);
}
