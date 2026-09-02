package ma.jurika.auth.infrastructure.sms;

import ma.jurika.auth.domain.exception.SmsDeliveryException;
import ma.jurika.auth.domain.port.SmsSender;
import ma.jurika.common.observability.BusinessMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import jakarta.annotation.PostConstruct;
import java.time.Duration;

/**
 * Sprint 12 finition (2026-06-04) — provider SMS via passerelle HTTP générique.
 *
 * <p>Cible une API REST qui envoie effectivement les SMS pour nous (TextBee,
 * SMSGateway-for-Android, Inwi BulkSMS, Maroc Telecom Aiwall, etc.). Le
 * format du POST est entièrement configurable via 4 properties / 4 env vars :
 *
 * <ul>
 *   <li>{@code SMS_GATEWAY_URL} : URL absolue du endpoint d'envoi. Le
 *       placeholder {@code {device_id}} y est interpolable si la passerelle
 *       a un device-id dans l'URL.</li>
 *   <li>{@code SMS_GATEWAY_AUTH_HEADER} : nom du header d'auth
 *       (ex. {@code x-api-key}, {@code Authorization}). Optionnel.</li>
 *   <li>{@code SMS_GATEWAY_AUTH_VALUE} : valeur du header (ex. {@code <api-key>}
 *       ou {@code Bearer <token>}). Optionnel.</li>
 *   <li>{@code SMS_GATEWAY_BODY_TEMPLATE} : template JSON avec deux
 *       placeholders : {@code {recipient}} (numéro E.164) et {@code {message}}
 *       (corps du SMS, échappé pour JSON). Defaut TextBee-compatible :
 *       {@code {"recipients":["{recipient}"],"message":"{message}"}}.</li>
 * </ul>
 *
 * <p>Activation conditionnelle stricte : ne se déploie que si
 * {@code jurika.sms.provider=httpgateway}. Sinon {@link LoggerSmsSender}
 * (defaut) ou {@link TwilioSmsSender} restent en place.
 *
 * <p>Erreurs HTTP loggées + métrique Prometheus {@code jurika.auth.sms.failed},
 * MAIS jamais d'exception remontée pour ne pas casser le login (l'utilisateur
 * peut toujours utiliser TOTP ou un code de récupération).
 */
@Component
@ConditionalOnProperty(name = "jurika.sms.provider", havingValue = "httpgateway")
public class HttpGatewaySmsSender implements SmsSender {

    private static final String PROVIDER = "httpgateway";
    private static final String DEFAULT_BODY_TEMPLATE =
            "{\"recipients\":[\"{recipient}\"],\"message\":\"{message}\"}";

    private static final Logger log = LoggerFactory.getLogger(HttpGatewaySmsSender.class);

    private final String url;
    private final String authHeader;
    private final String authValue;
    private final String bodyTemplate;
    private final int timeoutMs;
    private final BusinessMetrics metrics;
    private final RestClient http;

    public HttpGatewaySmsSender(
            @Value("${jurika.sms.httpgateway.url:}") String url,
            @Value("${jurika.sms.httpgateway.auth-header:}") String authHeader,
            @Value("${jurika.sms.httpgateway.auth-value:}") String authValue,
            @Value("${jurika.sms.httpgateway.body-template:" + DEFAULT_BODY_TEMPLATE + "}") String bodyTemplate,
            @Value("${jurika.sms.httpgateway.timeout-ms:5000}") int timeoutMs,
            BusinessMetrics metrics) {
        this.url = url == null ? "" : url.trim();
        this.authHeader = authHeader == null ? "" : authHeader.trim();
        this.authValue = authValue == null ? "" : authValue.trim();
        this.bodyTemplate = (bodyTemplate == null || bodyTemplate.isBlank()) ? DEFAULT_BODY_TEMPLATE : bodyTemplate;
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 5000;
        this.metrics = metrics;

        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofMillis(this.timeoutMs));
        rf.setReadTimeout(Duration.ofMillis(this.timeoutMs));
        this.http = RestClient.builder().requestFactory(rf).build();
    }

    @PostConstruct
    void validate() {
        if (url.isBlank()) {
            log.warn("HttpGatewaySmsSender actif (jurika.sms.provider=httpgateway) mais SMS_GATEWAY_URL vide — les SMS seront skippés silencieusement.");
        } else {
            log.info("HttpGatewaySmsSender initialise (url={}, authHeader={}, timeoutMs={})",
                    url, authHeader.isBlank() ? "(none)" : authHeader, timeoutMs);
        }
    }

    @Override
    public void send(String phoneE164, String body) {
        if (url.isBlank()) {
            // Defensif : si la config est incomplete on log et on n'echoue pas
            // (sinon on casse le login 2FA pour rien).
            log.warn("[SMS-HTTPGW-NOOP] url manquante — skip {} : {}", phoneE164, body);
            metrics.smsFailed(PROVIDER, "no_url");
            return;
        }

        String jsonBody = bodyTemplate
                .replace("{recipient}", phoneE164)
                .replace("{message}", escapeJson(body));

        try {
            var spec = http.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody);

            if (!authHeader.isBlank() && !authValue.isBlank()) {
                spec = spec.header(authHeader, authValue);
            }
            // Standard Accept JSON ; certaines passerelles renvoient text/plain
            // mais Spring 6 RestClient l'accepte par defaut.
            spec.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

            String response = spec.retrieve().body(String.class);
            metrics.smsSent(PROVIDER);
            log.info("SMS envoye via HttpGateway (to=***{}, response={})",
                    tail4(phoneE164),
                    response == null ? "<empty>" : truncate(response, 120));
        } catch (RestClientResponseException ex) {
            metrics.smsFailed(PROVIDER, "http_" + ex.getStatusCode().value());
            log.error("Echec envoi SMS via HttpGateway (status={}, body={})",
                    ex.getStatusCode().value(),
                    truncate(ex.getResponseBodyAsString(), 200));
            // Best-effort : ne pas remonter d'exception, le login 2FA doit
            // pouvoir continuer (TOTP / recovery code restent disponibles).
        } catch (RuntimeException ex) {
            metrics.smsFailed(PROVIDER, "io");
            log.error("Erreur reseau envoi SMS HttpGateway : {}", ex.getMessage());
            // Idem : best-effort.
        }
    }

    /** Echappe les caractères qui casseraient un JSON literal naïf. */
    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static String tail4(String s) {
        return s == null || s.length() < 4 ? "***" : s.substring(s.length() - 4);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /**
     * Utilisée par les tests unitaires pour vérifier la composition du body
     * sans avoir à monter un serveur HTTP. Voir {@code HttpGatewaySmsSenderTest}.
     */
    String renderBody(String phoneE164, String message) {
        return bodyTemplate
                .replace("{recipient}", phoneE164)
                .replace("{message}", escapeJson(message));
    }

    /** Pour SmsConfig (jamais leak en log — pas de getter complet). */
    boolean isConfigured() {
        return !url.isBlank();
    }
}
