package ma.jurika.supervision.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ma.jurika.supervision.infrastructure.persistence.BusinessEventEntity;
import ma.jurika.supervision.infrastructure.persistence.BusinessEventJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sprint 11 TASK 6 — Endpoint d'ingestion interne des business events.
 *
 * Contrat cross-service (decide user) : les emetteurs (marketing-site,
 * frontend-react, auth-service, etc.) appellent POST /internal/events.
 * Pas d'INSERT direct cross-tier en base.
 *
 * Securite MVP Sprint 11 : header {@code X-Internal-Token} valide contre
 * la property {@code jurika.internal.token} (env var). Whitelist gateway
 * sur /internal/events. Pour MVP, on accepte aussi sans token mais en
 * mode "best-effort" (log warn) — sera durci Sprint 12 quand le client
 * commercial sera actif (changer matchIfMissing).
 */
@RestController
@RequestMapping("/internal/events")
public class InternalEventsController {

    private static final Logger log = LoggerFactory.getLogger(InternalEventsController.class);

    private final BusinessEventJpaRepository eventRepo;
    private final String internalToken;
    private final boolean tokenRequired;

    public InternalEventsController(BusinessEventJpaRepository eventRepo,
                                    @Value("${jurika.internal.token:}") String internalToken,
                                    @Value("${jurika.internal.token-required:false}") boolean tokenRequired) {
        this.eventRepo = eventRepo;
        this.internalToken = internalToken;
        this.tokenRequired = tokenRequired;
    }

    public static class EventPayload {
        @NotBlank
        @Size(max = 50)
        public String eventType;
        public UUID workspaceId;
        public Map<String, Object> properties = new HashMap<>();
        public Instant occurredAt;
        @Size(max = 20)
        public String source = "backend";
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody EventPayload payload) {

        if (tokenRequired) {
            if (internalToken == null || internalToken.isBlank() || !internalToken.equals(token)) {
                log.warn("Internal token invalid for event_type={}", payload.eventType);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "INTERNAL_TOKEN_REQUIRED"));
            }
        }

        BusinessEventEntity e = new BusinessEventEntity();
        e.setEventType(payload.eventType);
        e.setWorkspaceId(payload.workspaceId);
        e.setProperties(payload.properties == null ? new HashMap<>() : payload.properties);
        e.setOccurredAt(payload.occurredAt == null ? Instant.now() : payload.occurredAt);
        e.setSource(payload.source == null || payload.source.isBlank() ? "backend" : payload.source);
        e.setReceivedAt(Instant.now());

        // Best-effort fire-and-forget : un event d'analytics qui plante (Flyway en retard,
        // contrainte DB, JSON malforme...) ne doit JAMAIS remonter en 500 a la SPA — le
        // frontend traite cette route comme du nice-to-have et logge un debug. On accepte
        // l'ingestion sans bloquer l'UX, et on log cote serveur pour investigation.
        try {
            eventRepo.save(e);
            Map<String, Object> body = new HashMap<>();
            body.put("id", e.getId()); // peut etre null en test (mock @GeneratedValue) — HashMap accepte
            body.put("stored", true);
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (RuntimeException ex) {
            log.warn("Event ingestion failed (best-effort, returning 202) event_type={} workspace={} cause={}",
                    payload.eventType, payload.workspaceId, ex.getMessage());
            Map<String, Object> body = new HashMap<>();
            body.put("stored", false);
            body.put("reason", "INGEST_FAILED");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
        }
    }

    /**
     * Validation failures (e.g. eventType missing) → 400 propre.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .orElse("invalid payload");
        log.debug("Event payload validation failed: {}", detail);
        return ResponseEntity.badRequest().body(Map.of("error", "VALIDATION_FAILED", "detail", detail));
    }

    /**
     * JSON malforme / champ deserialisation impossible → 202 silencieux (best-effort).
     * Avant ce fix, un Instant mal formate ou un type incompatible faisait remonter un
     * 400 par defaut, ce qui polluait la console SPA en rouge sans valeur ajoutee.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.debug("Event payload not readable (best-effort, returning 202): {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("stored", false, "reason", "UNREADABLE"));
    }

    /**
     * Catch-all : TOUTE exception non geree (AOP audit, transaction manager, etc.) renvoie
     * 202 silencieux. La SPA traite l'analytics en fire-and-forget — un 500 console
     * polluerait sans aider. Le serveur logge en ERROR pour Sentry/Loki.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAny(Exception ex) {
        log.error("Event ingestion failed (catch-all, returning 202) cause={} message={}",
                ex.getClass().getSimpleName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("stored", false, "reason", "INTERNAL_ERROR"));
    }
}
