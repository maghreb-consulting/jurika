package ma.jurika.auth.infrastructure.sms;

import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import ma.jurika.auth.domain.exception.SmsDeliveryException;
import ma.jurika.auth.domain.port.SmsSender;
import ma.jurika.common.observability.BusinessMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Sprint 3 / TASK 1 — Provider SMS Twilio (production).
 *
 * <p>Active uniquement quand {@code jurika.sms.provider=twilio}. Sinon {@link LoggerSmsSender}
 * reste actif (mode dev/test).
 *
 * <p>Credentials Twilio fournis via Doppler / AWS Secrets Manager :
 * <ul>
 *     <li>{@code TWILIO_ACCOUNT_SID}</li>
 *     <li>{@code TWILIO_AUTH_TOKEN}</li>
 *     <li>{@code TWILIO_FROM_NUMBER} — numero E.164 (ex {@code +14155551234}) ou alpha sender ID</li>
 * </ul>
 * Voir {@code infrastructure/secrets/SECRETS_INVENTORY.md}.
 *
 * <p>Métriques émises : {@code jurika.auth.sms.sent{provider="twilio"}} et
 * {@code jurika.auth.sms.failed{provider="twilio",reason=...}}.
 */
@Component
@ConditionalOnProperty(name = "jurika.sms.provider", havingValue = "twilio")
public class TwilioSmsSender implements SmsSender {

    private static final String PROVIDER = "twilio";
    private static final Logger log = LoggerFactory.getLogger(TwilioSmsSender.class);

    private final String accountSid;
    private final String authToken;
    private final String fromNumber;
    private final BusinessMetrics metrics;

    public TwilioSmsSender(
            @Value("${jurika.sms.twilio.account-sid:}") String accountSid,
            @Value("${jurika.sms.twilio.auth-token:}") String authToken,
            @Value("${jurika.sms.twilio.from-number:}") String fromNumber,
            BusinessMetrics metrics) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
        this.metrics = metrics;
    }

    @PostConstruct
    void initTwilio() {
        if (accountSid == null || accountSid.isBlank()) {
            throw new IllegalStateException(
                    "TWILIO_ACCOUNT_SID manquant — definir jurika.sms.twilio.account-sid (Doppler/AWS SM)");
        }
        if (authToken == null || authToken.isBlank()) {
            throw new IllegalStateException("TWILIO_AUTH_TOKEN manquant");
        }
        if (fromNumber == null || fromNumber.isBlank()) {
            throw new IllegalStateException("TWILIO_FROM_NUMBER manquant (format E.164 +XXX...)");
        }
        Twilio.init(accountSid, authToken);
        log.info("TwilioSmsSender initialise (from={}, sid=***{})",
                fromNumber, accountSid.length() > 4 ? accountSid.substring(accountSid.length() - 4) : "***");
    }

    @Override
    public void send(String phoneE164, String body) {
        try {
            Message msg = Message.creator(
                    new PhoneNumber(phoneE164),
                    new PhoneNumber(fromNumber),
                    body
            ).create();
            metrics.smsSent(PROVIDER);
            log.info("SMS envoye via Twilio (sid={}, status={}, to=***{})",
                    msg.getSid(),
                    msg.getStatus(),
                    phoneE164.length() > 4 ? phoneE164.substring(phoneE164.length() - 4) : "***");
        } catch (ApiException ex) {
            metrics.smsFailed(PROVIDER, "api_error_" + ex.getCode());
            log.error("Echec envoi SMS Twilio (code={}, status={})",
                    ex.getCode(), ex.getStatusCode(), ex);
            throw new SmsDeliveryException(
                    "Echec envoi SMS via Twilio (code=" + ex.getCode() + ")", ex);
        } catch (RuntimeException ex) {
            metrics.smsFailed(PROVIDER, "unexpected");
            log.error("Erreur inattendue envoi SMS Twilio", ex);
            throw new SmsDeliveryException("Erreur inattendue envoi SMS Twilio", ex);
        }
    }
}
