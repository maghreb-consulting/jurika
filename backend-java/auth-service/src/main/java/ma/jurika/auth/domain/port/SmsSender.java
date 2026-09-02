package ma.jurika.auth.domain.port;

/**
 * Port d'envoi de SMS transactionnels (code 2FA, verification telephone).
 * <p>
 * Implementations possibles :
 * <ul>
 *     <li>{@code LoggerSmsSender} (dev) : logge le code dans la console, n'envoie rien</li>
 *     <li>{@code TwilioSmsSender} (prod) : envoie via Twilio API</li>
 *     <li>{@code InwiBulkSmsSender} (prod Maroc) : envoie via Inwi Bulk SMS</li>
 * </ul>
 * Le choix d'implementation est configurable via {@code jurika.sms.provider} (logger / twilio / inwi).
 */
public interface SmsSender {

    /**
     * Envoie un SMS contenant le code de verification.
     *
     * @param phoneE164 numero au format +212XXXXXXXXX
     * @param body      texte du SMS (max 160 chars ASCII ou 70 chars unicode)
     */
    void send(String phoneE164, String body);
}
