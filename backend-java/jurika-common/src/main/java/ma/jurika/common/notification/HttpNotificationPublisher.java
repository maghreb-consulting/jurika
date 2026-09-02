package ma.jurika.common.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter HTTP vers le realtime-service Node.js.
 *
 * <p>Deux voies distinctes :
 * <ul>
 *   <li>{@link #publish} -> {@code POST /emit} : broadcast Socket.io <b>transitoire</b>
 *       (jamais persiste, jamais requetable). Conserve pour les events socket purs.</li>
 *   <li>{@link #notifyUser} -> {@code POST /internal/notifications} : notification
 *       <b>persistante</b> (ecrite en base + push {@code notification:received}).
 *       C'est ce qui remplit la cloche topbar et le compteur "non lues".</li>
 * </ul>
 *
 * <p>Best-effort : un echec d'emission ne casse JAMAIS la transaction metier appelante.
 */
public class HttpNotificationPublisher implements NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(HttpNotificationPublisher.class);

    private final RestClient restClient;
    private final String emitUrl;
    private final String notifyUrl;
    private final String internalToken;

    public HttpNotificationPublisher(String realtimeBaseUrl) {
        this(realtimeBaseUrl, null);
    }

    public HttpNotificationPublisher(String realtimeBaseUrl, String internalToken) {
        this.restClient = RestClient.builder()
                .baseUrl(realtimeBaseUrl)
                .build();
        this.emitUrl = "/emit";
        this.notifyUrl = "/internal/notifications";
        // Vide -> on n'envoie pas l'entete (cote Node, le token optionnel : si non
        // configure il accepte sans). Non-vide -> on l'envoie (sinon 403).
        this.internalToken = internalToken == null ? "" : internalToken.trim();
    }

    @Override
    @Async
    public void publish(UUID userId, UUID workspaceId, String event, Map<String, Object> payload) {
        try {
            Map<String, Object> body = new HashMap<>();
            if (userId != null) body.put("userId", userId.toString());
            if (workspaceId != null) body.put("workspaceId", workspaceId.toString());
            body.put("event", event);
            body.put("data", payload == null ? Map.of() : payload);
            restClient.post()
                    .uri(emitUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Notification emission failed (event={}, user={}, ws={}): {}",
                    event, userId, workspaceId, ex.getMessage());
        }
    }

    @Override
    @Async
    public void notifyUser(UUID userId, UUID workspaceId, String type, String title,
                           String message, String actionUrl, Map<String, Object> metadata) {
        // Le realtime-service exige workspaceId + userId + type + title + message.
        if (userId == null || workspaceId == null
                || type == null || title == null || message == null) {
            log.warn("Notification persistante ignoree (champ requis manquant) "
                    + "type={} user={} ws={}", type, userId, workspaceId);
            return;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("workspaceId", workspaceId.toString());
            body.put("userId", userId.toString());
            body.put("type", type);
            body.put("title", title);
            body.put("message", message);
            body.put("actionUrl", actionUrl);
            body.put("metadata", metadata);
            var request = restClient.post()
                    .uri(notifyUrl)
                    .contentType(MediaType.APPLICATION_JSON);
            if (!internalToken.isEmpty()) {
                request = request.header("X-Internal-Token", internalToken);
            }
            request.body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Notification persistante echouee (type={}, user={}, ws={}): {}",
                    type, userId, workspaceId, ex.getMessage());
        }
    }
}
