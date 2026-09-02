package ma.jurika.auth.infrastructure.sms;

import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import ma.jurika.auth.domain.exception.SmsDeliveryException;
import ma.jurika.common.observability.BusinessMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du TwilioSmsSender — mocke le SDK Twilio (Twilio.init + Message.creator)
 * pour eviter tout appel reseau reel.
 */
class TwilioSmsSenderTest {

    private SimpleMeterRegistry registry;
    private BusinessMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new BusinessMetrics(registry);
    }

    @Test
    void initThrowsWhenAccountSidMissing() {
        TwilioSmsSender sender = new TwilioSmsSender("", "token", "+14155551234", metrics);
        assertThatThrownBy(sender::initTwilio)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TWILIO_ACCOUNT_SID");
    }

    @Test
    void initThrowsWhenAuthTokenMissing() {
        TwilioSmsSender sender = new TwilioSmsSender("AC123", "", "+14155551234", metrics);
        assertThatThrownBy(sender::initTwilio)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TWILIO_AUTH_TOKEN");
    }

    @Test
    void initThrowsWhenFromNumberMissing() {
        TwilioSmsSender sender = new TwilioSmsSender("AC123", "token", "", metrics);
        assertThatThrownBy(sender::initTwilio)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TWILIO_FROM_NUMBER");
    }

    @Test
    void sendIncrementsSmsSentMetricOnSuccess() {
        try (MockedStatic<Twilio> twilioStatic = mockStatic(Twilio.class);
             MockedStatic<Message> messageStatic = mockStatic(Message.class)) {

            MessageCreator creator = mock(MessageCreator.class);
            Message msg = mock(Message.class);
            when(msg.getSid()).thenReturn("SMxxx");
            when(msg.getStatus()).thenReturn(Message.Status.QUEUED);
            messageStatic.when(() -> Message.creator(any(PhoneNumber.class), any(PhoneNumber.class), any(String.class)))
                    .thenReturn(creator);
            when(creator.create()).thenReturn(msg);

            TwilioSmsSender sender = new TwilioSmsSender("AC123", "token", "+14155551234", metrics);
            sender.initTwilio();
            sender.send("+212600000000", "JURIKA: code 123456");

            Counter sent = registry.find(BusinessMetrics.SMS_SENT)
                    .tag("provider", "twilio").counter();
            assertThat(sent).isNotNull();
            assertThat(sent.count()).isEqualTo(1.0);
            assertThat(registry.find(BusinessMetrics.SMS_FAILED).counter()).isNull();
        }
    }

    @Test
    void sendIncrementsSmsFailedAndThrowsOnApiException() {
        try (MockedStatic<Twilio> twilioStatic = mockStatic(Twilio.class);
             MockedStatic<Message> messageStatic = mockStatic(Message.class)) {

            MessageCreator creator = mock(MessageCreator.class);
            messageStatic.when(() -> Message.creator(any(PhoneNumber.class), any(PhoneNumber.class), any(String.class)))
                    .thenReturn(creator);
            ApiException apiEx = new ApiException("invalid number", 21211, null, 400, null);
            doThrow(apiEx).when(creator).create();

            TwilioSmsSender sender = new TwilioSmsSender("AC123", "token", "+14155551234", metrics);
            sender.initTwilio();

            assertThatThrownBy(() -> sender.send("+212600000000", "body"))
                    .isInstanceOf(SmsDeliveryException.class)
                    .hasMessageContaining("21211");

            Counter failed = registry.find(BusinessMetrics.SMS_FAILED)
                    .tag("provider", "twilio").tag("reason", "api_error_21211").counter();
            assertThat(failed).isNotNull();
            assertThat(failed.count()).isEqualTo(1.0);
        }
    }
}
