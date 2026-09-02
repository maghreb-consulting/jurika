package ma.jurika.supervision.api;

import ma.jurika.supervision.infrastructure.persistence.BusinessEventEntity;
import ma.jurika.supervision.infrastructure.persistence.BusinessEventJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Sprint 11 TASK 6 — InternalEventsController unit (mock repo).
 */
class InternalEventsControllerTest {

    private BusinessEventJpaRepository repo;
    private InternalEventsController controllerNoTokenRequired;
    private InternalEventsController controllerWithToken;

    @BeforeEach
    void setUp() {
        repo = mock(BusinessEventJpaRepository.class);
        controllerNoTokenRequired = new InternalEventsController(repo, "", false);
        controllerWithToken = new InternalEventsController(repo, "s3cr3t-internal", true);
    }

    @Test
    void persistsValidEventWithoutTokenWhenNotRequired() {
        InternalEventsController.EventPayload p = new InternalEventsController.EventPayload();
        p.eventType = "PAGE_VIEWED";
        p.source = "marketing-site";
        p.properties = Map.of("path", "/");
        ResponseEntity<Map<String, Object>> res = controllerNoTokenRequired.ingest(null, p);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ArgumentCaptor<BusinessEventEntity> cap = ArgumentCaptor.forClass(BusinessEventEntity.class);
        verify(repo).save(cap.capture());
        BusinessEventEntity saved = cap.getValue();
        assertThat(saved.getEventType()).isEqualTo("PAGE_VIEWED");
        assertThat(saved.getSource()).isEqualTo("marketing-site");
        assertThat(saved.getProperties()).containsEntry("path", "/");
        assertThat(saved.getOccurredAt()).isNotNull();
        assertThat(saved.getReceivedAt()).isNotNull();
    }

    @Test
    void rejectsWhenTokenRequiredAndMissing() {
        InternalEventsController.EventPayload p = new InternalEventsController.EventPayload();
        p.eventType = "DEMO_REQUESTED";
        ResponseEntity<Map<String, Object>> res = controllerWithToken.ingest(null, p);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(repo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsWhenTokenRequiredAndWrong() {
        InternalEventsController.EventPayload p = new InternalEventsController.EventPayload();
        p.eventType = "DEMO_REQUESTED";
        ResponseEntity<Map<String, Object>> res = controllerWithToken.ingest("wrong-token", p);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(repo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void acceptsValidTokenAndPersists() {
        InternalEventsController.EventPayload p = new InternalEventsController.EventPayload();
        p.eventType = "SIGNUP_COMPLETED";
        p.workspaceId = UUID.randomUUID();
        p.source = "spa";
        ResponseEntity<Map<String, Object>> res = controllerWithToken.ingest("s3cr3t-internal", p);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(repo).save(org.mockito.ArgumentMatchers.any());
    }
}
