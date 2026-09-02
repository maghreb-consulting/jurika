package ma.jurika.auth.infrastructure.sms;

import ma.jurika.auth.domain.port.SmsSender;
import ma.jurika.common.observability.BusinessMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementation de developpement : ne contacte aucun provider SMS,
 * logge le SMS dans la console.
 * <p>
 * Activee par defaut. En production, configurer {@code jurika.sms.provider=twilio}
 * (ou {@code inwi}) pour utiliser un vrai provider.
 */
@Component
@ConditionalOnProperty(name = "jurika.sms.provider", havingValue = "logger", matchIfMissing = true)
public class LoggerSmsSender implements SmsSender {

    private static final String PROVIDER = "logger";
    private static final Logger log = LoggerFactory.getLogger(LoggerSmsSender.class);

    private final BusinessMetrics metrics;

    public LoggerSmsSender(BusinessMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void send(String phoneE164, String body) {
        log.warn("[SMS-DEV-STUB] -> {} : {}", phoneE164, body);
        metrics.smsSent(PROVIDER);
    }
}
