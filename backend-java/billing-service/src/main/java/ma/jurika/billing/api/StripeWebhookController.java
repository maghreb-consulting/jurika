package ma.jurika.billing.api;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import ma.jurika.billing.application.webhook.StripeWebhookDispatcher;
import ma.jurika.billing.infrastructure.stripe.StripeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Sprint 12 — endpoint webhook Stripe.
 *
 * <p>Securite (RG-BL18 + decision archi #6) :
 *  - Verification HMAC obligatoire via {@code Stripe-Signature} header
 *  - 401 Unauthorized si signature invalide
 *  - Endpoint exempte de JWT auth (Stripe ne porte pas notre JWT) — cf.
 *    {@code SecurityConfig#securityFilterChain} qui permitAll /webhook/**
 *
 * <p>Reception :
 *  - Body brut requis (pas de Jackson parse cote Spring) car la signature
 *    est calculee sur le RAW JSON byte-for-byte.
 *  - On retourne TOUJOURS 200 OK apres ingestion meme si le handler a fail
 *    (Stripe re-essaye automatiquement — pas de boucle de retry agressive
 *    cote Stripe sur erreur deterministe). L'erreur est tracee en DB.
 */
@RestController
@RequestMapping("/api/v1/billing/webhook")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final StripeService stripeService;
    private final StripeWebhookDispatcher dispatcher;

    public StripeWebhookController(StripeService stripeService,
                                    StripeWebhookDispatcher dispatcher) {
        this.stripeService = stripeService;
        this.dispatcher = dispatcher;
    }

    /**
     * POST /api/v1/billing/webhook/stripe
     *
     * @param payload    raw JSON body
     * @param signature  header {@code Stripe-Signature}
     * @return 200 OK si ingere (meme si handler fail), 401 si signature KO
     */
    @PostMapping("/stripe")
    public ResponseEntity<?> handleStripe(@RequestBody String payload,
                                           @RequestHeader(value = "Stripe-Signature", required = false) String signature) {

        if (signature == null || signature.isBlank()) {
            log.warn("Webhook Stripe rejete : header Stripe-Signature manquant");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing Stripe-Signature header"));
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, stripeService.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Webhook Stripe rejete : signature invalide. message={}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid Stripe signature"));
        } catch (RuntimeException e) {
            log.error("Webhook Stripe : erreur parsing payload", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid payload"));
        }

        boolean processed = dispatcher.ingest(event, payload);
        return ResponseEntity.ok(Map.of(
                "received", true,
                "event_id", event.getId(),
                "type", event.getType(),
                "processed", processed
        ));
    }
}
