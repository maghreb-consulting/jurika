package ma.jurika.billing.application.webhook;

import com.stripe.model.Event;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.WebhookEventJpaRepository;
import ma.jurika.billing.infrastructure.persistence.WebhookEventEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 12 — verifie l'idempotency au niveau dispatcher : 10 ingestions du
 * meme event = 1 seul handle() + 1 seul save (acceptance criteria).
 */
class StripeWebhookDispatcherTest {

    private WebhookEventJpaRepository repo;
    private StripeEventHandler handler;
    private StripeWebhookDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        repo = mock(WebhookEventJpaRepository.class);
        handler = mock(StripeEventHandler.class);
        when(handler.eventType()).thenReturn("checkout.session.completed");
        dispatcher = new StripeWebhookDispatcher(repo, List.of(handler));
    }

    @Test
    void firstIngestion_persistsAndDispatches() {
        Event event = newEvent("evt_test_001", "checkout.session.completed");
        when(repo.existsByStripeEventId("evt_test_001")).thenReturn(false);
        when(repo.save(any(WebhookEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean result = dispatcher.ingest(event, "{}");

        assertThat(result).isTrue();
        verify(handler, times(1)).handle(event);

        ArgumentCaptor<WebhookEventEntity> cap = ArgumentCaptor.forClass(WebhookEventEntity.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getStripeEventId()).isEqualTo("evt_test_001");
        assertThat(cap.getValue().getEventType()).isEqualTo("checkout.session.completed");
        assertThat(cap.getValue().getAttempts()).isEqualTo(1);
    }

    @Test
    void duplicateEvent_skipsIdempotent() {
        Event event = newEvent("evt_test_002", "checkout.session.completed");
        when(repo.existsByStripeEventId("evt_test_002")).thenReturn(true);

        boolean result = dispatcher.ingest(event, "{}");

        assertThat(result).isFalse();
        verify(handler, never()).handle(any(Event.class));
        verify(repo, never()).save(any(WebhookEventEntity.class));
    }

    @Test
    void tenIngestionsOfSameEvent_dispatchHandlerOnce() {
        Event event = newEvent("evt_test_003", "checkout.session.completed");
        // Premiere ingestion : pas encore vu, save, dispatch
        when(repo.existsByStripeEventId("evt_test_003")).thenReturn(false, true, true, true, true, true, true, true, true, true);
        when(repo.save(any(WebhookEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        for (int i = 0; i < 10; i++) {
            dispatcher.ingest(event, "{}");
        }

        verify(handler, times(1)).handle(event);
        verify(repo, times(1)).save(any(WebhookEventEntity.class));
    }

    @Test
    void unknownEventType_persistsButSkipsDispatch() {
        Event event = newEvent("evt_test_004", "customer.created");
        when(repo.existsByStripeEventId("evt_test_004")).thenReturn(false);
        when(repo.save(any(WebhookEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean result = dispatcher.ingest(event, "{}");

        assertThat(result).isTrue();
        verify(handler, never()).handle(any(Event.class));
        verify(repo).save(any(WebhookEventEntity.class));
    }

    @Test
    void handlerException_persistsErrorAndReturnsFalse() {
        Event event = newEvent("evt_test_005", "checkout.session.completed");
        when(repo.existsByStripeEventId("evt_test_005")).thenReturn(false);
        when(repo.save(any(WebhookEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doThrow(new RuntimeException("DB down")).when(handler).handle(event);

        boolean result = dispatcher.ingest(event, "{}");

        assertThat(result).isFalse();
    }

    private static Event newEvent(String id, String type) {
        Event e = new Event();
        e.setId(id);
        e.setType(type);
        return e;
    }
}
