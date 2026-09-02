package ma.jurika.auth.domain.exception;

import ma.jurika.common.exception.BusinessException;

/**
 * Levee quand un provider SMS (Twilio, Inwi...) echoue a livrer un message.
 * Code business : {@code SMS_DELIVERY_FAILED}.
 */
public class SmsDeliveryException extends BusinessException {

    public SmsDeliveryException(String message) {
        super("SMS_DELIVERY_FAILED", message);
    }

    public SmsDeliveryException(String message, Throwable cause) {
        super("SMS_DELIVERY_FAILED", message);
        initCause(cause);
    }
}
