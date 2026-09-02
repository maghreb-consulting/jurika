package ma.jurika.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Façade Micrometer pour les métriques business JURIKA (Sprint 2 / TASK 2).
 *
 * <p>Cache des meters par couple action+tags pour éviter le coût de lookup à chaque
 * incrément (Counter/Timer sont thread-safe une fois construits).
 *
 * <p>Cardinalité (Sprint 14 bis / D-S2-08) : workspace_id est haché en
 * {@code workspace_bucket} (100 buckets stables), ce qui garde la cardinalité
 * sous 100 séries par métrique même au-delà de 1000+ workspaces. La fonction
 * de hachage est déterministe (Math.abs(hashCode) % 100) — bucket_NN.
 *
 * <p>Tous les meters sont préfixés {@code jurika.} pour permettre un filtre
 * Prometheus simple ({@code {__name__=~"jurika_.*"}}).
 */
public class BusinessMetrics {

    public static final String LOGIN_SUCCESS = "jurika.auth.login.success";
    public static final String LOGIN_FAILED = "jurika.auth.login.failed";
    public static final String REFRESH_ISSUED = "jurika.auth.refresh.issued";
    public static final String SMS_SENT = "jurika.auth.sms.sent";
    public static final String SMS_FAILED = "jurika.auth.sms.failed";
    public static final String TICKET_CREATED = "jurika.tickets.created";
    public static final String TICKET_STATE_TRANSITION = "jurika.tickets.state.transition";
    public static final String DOCUMENT_UPLOADED = "jurika.documents.uploaded";
    public static final String WORKFLOW_DURATION = "jurika.workflows.duration";

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void loginSuccess() {
        counter(LOGIN_SUCCESS, Tags.empty()).increment();
    }

    public void loginFailed(String reason) {
        counter(LOGIN_FAILED, Tags.of("reason", safeReason(reason))).increment();
    }

    public void refreshTokenIssued() {
        counter(REFRESH_ISSUED, Tags.empty()).increment();
    }

    /**
     * Compteur d'envois SMS reussis, tagge par provider (twilio, logger, inwi...).
     * Sprint 3 / TASK 1.
     */
    public void smsSent(String provider) {
        counter(SMS_SENT, Tags.of("provider", safeProvider(provider))).increment();
    }

    /**
     * Compteur d'echecs d'envoi SMS, tagge par provider + raison (api_error, network, invalid_number...).
     * Sprint 3 / TASK 1.
     */
    public void smsFailed(String provider, String reason) {
        counter(SMS_FAILED, Tags.of(
                Tag.of("provider", safeProvider(provider)),
                Tag.of("reason", safeReason(reason))
        )).increment();
    }

    public void ticketCreated(UUID workspaceId) {
        counter(TICKET_CREATED, Tags.of(workspace(workspaceId))).increment();
    }

    /**
     * Sprint 14 bis / D-S2-06 — Compteur de transitions de tickets pour observer
     * la sante du workflow State Pattern. Tags: from, to, workspace_bucket.
     */
    public void ticketStateTransition(String fromState, String toState, UUID workspaceId) {
        counter(TICKET_STATE_TRANSITION, Tags.of(
                Tag.of("from", safeState(fromState)),
                Tag.of("to", safeState(toState)),
                workspace(workspaceId)
        )).increment();
    }

    private static String safeState(String state) {
        return state == null || state.isBlank() ? "unknown" : state;
    }

    public void documentUploaded(UUID workspaceId, String documentType) {
        counter(DOCUMENT_UPLOADED, Tags.of(workspace(workspaceId), Tag.of("type", safeType(documentType))))
                .increment();
    }

    public Timer workflowTimer(String workflowType) {
        return timers.computeIfAbsent(
                WORKFLOW_DURATION + "#" + workflowType,
                k -> Timer.builder(WORKFLOW_DURATION)
                        .description("Durée d'exécution des workflows métier (start → complete)")
                        .tag("type", workflowType)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(registry));
    }

    private Counter counter(String name, Tags tags) {
        return counters.computeIfAbsent(meterKey(name, tags),
                k -> Counter.builder(name).tags(tags).register(registry));
    }

    /**
     * Sprint 14 bis / D-S2-08 — Hashing déterministe en 100 buckets pour borner
     * la cardinalité Prometheus indépendamment du nombre de workspaces.
     * Le bucket est stable dans le temps pour un workspace donné.
     */
    static String workspaceBucket(UUID workspaceId) {
        if (workspaceId == null) return "unknown";
        int hash = Math.abs(workspaceId.hashCode()) % 100;
        return "bucket_" + String.format("%02d", hash);
    }

    private static Tag workspace(UUID workspaceId) {
        return Tag.of("workspace_bucket", workspaceBucket(workspaceId));
    }

    private static String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "unknown" : reason;
    }

    private static String safeType(String type) {
        return type == null || type.isBlank() ? "unknown" : type;
    }

    private static String safeProvider(String provider) {
        return provider == null || provider.isBlank() ? "unknown" : provider;
    }

    private static String meterKey(String name, Tags tags) {
        StringBuilder sb = new StringBuilder(name);
        tags.forEach(t -> sb.append('|').append(t.getKey()).append('=').append(t.getValue()));
        return sb.toString();
    }
}
