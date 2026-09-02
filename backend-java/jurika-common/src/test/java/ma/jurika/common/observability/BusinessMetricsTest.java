package ma.jurika.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessMetricsTest {

    private SimpleMeterRegistry registry;
    private BusinessMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new BusinessMetrics(registry);
    }

    @Test
    void loginSuccessIncrementsCounter() {
        metrics.loginSuccess();
        metrics.loginSuccess();

        Counter c = registry.find(BusinessMetrics.LOGIN_SUCCESS).counter();
        assertThat(c).isNotNull();
        assertThat(c.count()).isEqualTo(2.0);
    }

    @Test
    void loginFailedTagsReason() {
        metrics.loginFailed("bad_password");
        metrics.loginFailed("locked");

        assertThat(registry.find(BusinessMetrics.LOGIN_FAILED).tag("reason", "bad_password").counter())
                .isNotNull()
                .extracting(Counter::count).isEqualTo(1.0);
        assertThat(registry.find(BusinessMetrics.LOGIN_FAILED).tag("reason", "locked").counter())
                .isNotNull()
                .extracting(Counter::count).isEqualTo(1.0);
    }

    @Test
    void loginFailedNullReasonBucketsAsUnknown() {
        metrics.loginFailed(null);
        metrics.loginFailed("");
        metrics.loginFailed("  ");

        Counter c = registry.find(BusinessMetrics.LOGIN_FAILED).tag("reason", "unknown").counter();
        assertThat(c).isNotNull();
        assertThat(c.count()).isEqualTo(3.0);
    }

    @Test
    void refreshTokenIssuedHasNoTags() {
        metrics.refreshTokenIssued();
        Counter c = registry.find(BusinessMetrics.REFRESH_ISSUED).counter();
        assertThat(c).isNotNull();
        assertThat(c.count()).isEqualTo(1.0);
    }

    @Test
    void ticketCreatedTagsWorkspaceBucket() {
        UUID ws = UUID.randomUUID();
        metrics.ticketCreated(ws);

        String expectedBucket = BusinessMetrics.workspaceBucket(ws);
        Counter c = registry.find(BusinessMetrics.TICKET_CREATED)
                .tag("workspace_bucket", expectedBucket).counter();
        assertThat(c).isNotNull();
        assertThat(c.count()).isEqualTo(1.0);
    }

    @Test
    void documentUploadedTagsWorkspaceBucketAndType() {
        UUID ws = UUID.randomUUID();
        metrics.documentUploaded(ws, "STATUTS");

        String expectedBucket = BusinessMetrics.workspaceBucket(ws);
        Counter c = registry.find(BusinessMetrics.DOCUMENT_UPLOADED)
                .tag("workspace_bucket", expectedBucket)
                .tag("type", "STATUTS")
                .counter();
        assertThat(c).isNotNull();
        assertThat(c.count()).isEqualTo(1.0);
    }

    @Test
    void workspaceBucket_nullId_returnsUnknown() {
        assertThat(BusinessMetrics.workspaceBucket(null)).isEqualTo("unknown");
    }

    @Test
    void workspaceBucket_isDeterministic() {
        UUID ws = UUID.fromString("11111111-2222-3333-4444-555555555555");
        String a = BusinessMetrics.workspaceBucket(ws);
        String b = BusinessMetrics.workspaceBucket(ws);
        assertThat(a).isEqualTo(b);
        assertThat(a).matches("bucket_\\d{2}");
    }

    @Test
    void workspaceBucket_5000workspaces_stayWithin100buckets_D_S2_08() {
        // Sprint 14 bis / D-S2-08 — verifie que la cardinalite Prometheus est
        // bornee a 100 series memes avec 5000 workspaces uniques.
        SimpleMeterRegistry r = new SimpleMeterRegistry();
        BusinessMetrics m = new BusinessMetrics(r);

        for (int i = 0; i < 5000; i++) {
            m.ticketCreated(UUID.randomUUID());
        }

        long seriesCount = r.getMeters().stream()
                .filter(meter -> BusinessMetrics.TICKET_CREATED.equals(meter.getId().getName()))
                .count();
        // 100 buckets max + tolerance "unknown" si jamais un UUID hash a 0 ne touche pas bucket_00
        assertThat(seriesCount)
                .as("Cardinalite bornee a 100 series workspace_bucket pour 5000 workspaces uniques")
                .isLessThanOrEqualTo(100L);
    }

    @Test
    void cacheSameCounterAcrossIncrements() {
        UUID ws = UUID.randomUUID();
        metrics.ticketCreated(ws);
        metrics.ticketCreated(ws);
        metrics.ticketCreated(ws);

        long created = registry.getMeters().stream()
                .filter(m -> BusinessMetrics.TICKET_CREATED.equals(m.getId().getName()))
                .count();
        assertThat(created).isEqualTo(1L);  // une seule serie temporelle, pas 3
    }

    @Test
    void smsSentTagsProvider() {
        metrics.smsSent("twilio");
        metrics.smsSent("twilio");
        metrics.smsSent("logger");

        Counter twilio = registry.find(BusinessMetrics.SMS_SENT).tag("provider", "twilio").counter();
        Counter logger = registry.find(BusinessMetrics.SMS_SENT).tag("provider", "logger").counter();
        assertThat(twilio).isNotNull();
        assertThat(twilio.count()).isEqualTo(2.0);
        assertThat(logger).isNotNull();
        assertThat(logger.count()).isEqualTo(1.0);
    }

    @Test
    void smsFailedTagsProviderAndReason() {
        metrics.smsFailed("twilio", "api_error_21211");
        metrics.smsFailed("twilio", "api_error_21211");
        metrics.smsFailed("twilio", "unexpected");

        Counter invalid = registry.find(BusinessMetrics.SMS_FAILED)
                .tag("provider", "twilio").tag("reason", "api_error_21211").counter();
        Counter unexpected = registry.find(BusinessMetrics.SMS_FAILED)
                .tag("provider", "twilio").tag("reason", "unexpected").counter();
        assertThat(invalid).isNotNull();
        assertThat(invalid.count()).isEqualTo(2.0);
        assertThat(unexpected).isNotNull();
        assertThat(unexpected.count()).isEqualTo(1.0);
    }

    @Test
    void smsFailedNullReasonBucketsAsUnknown() {
        metrics.smsFailed("twilio", null);
        metrics.smsFailed(null, "  ");

        assertThat(registry.find(BusinessMetrics.SMS_FAILED)
                .tag("provider", "twilio").tag("reason", "unknown").counter())
                .isNotNull().extracting(Counter::count).isEqualTo(1.0);
        assertThat(registry.find(BusinessMetrics.SMS_FAILED)
                .tag("provider", "unknown").tag("reason", "unknown").counter())
                .isNotNull().extracting(Counter::count).isEqualTo(1.0);
    }

    @Test
    void ticketStateTransitionTagsFromToAndBucket() {
        UUID ws = UUID.randomUUID();
        metrics.ticketStateTransition("NOUVEAU", "EN_COURS", ws);
        metrics.ticketStateTransition("EN_COURS", "CLOTURE", ws);

        String bucket = BusinessMetrics.workspaceBucket(ws);
        Counter c1 = registry.find(BusinessMetrics.TICKET_STATE_TRANSITION)
                .tag("from", "NOUVEAU").tag("to", "EN_COURS")
                .tag("workspace_bucket", bucket).counter();
        Counter c2 = registry.find(BusinessMetrics.TICKET_STATE_TRANSITION)
                .tag("from", "EN_COURS").tag("to", "CLOTURE")
                .tag("workspace_bucket", bucket).counter();
        assertThat(c1).isNotNull();
        assertThat(c1.count()).isEqualTo(1.0);
        assertThat(c2).isNotNull();
        assertThat(c2.count()).isEqualTo(1.0);
    }

    @Test
    void ticketStateTransition_nullStates_bucketsAsUnknown() {
        UUID ws = UUID.randomUUID();
        metrics.ticketStateTransition(null, "  ", ws);

        Counter c = registry.find(BusinessMetrics.TICKET_STATE_TRANSITION)
                .tag("from", "unknown").tag("to", "unknown").counter();
        assertThat(c).isNotNull();
        assertThat(c.count()).isEqualTo(1.0);
    }

    @Test
    void workflowTimerRecordsDuration() {
        metrics.workflowTimer("CREATION_SARL").record(java.time.Duration.ofMillis(100));
        metrics.workflowTimer("CREATION_SARL").record(java.time.Duration.ofMillis(200));

        var timer = registry.find(BusinessMetrics.WORKFLOW_DURATION)
                .tag("type", "CREATION_SARL").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(2L);
    }
}
