package ma.jurika.common.notification;

import java.util.Map;
import java.util.UUID;

/**
 * Port d'emission de notifications temps-reel vers le realtime-service Node.js.
 * Le realtime-service relaie via Socket.io aux clients connectes des
 * utilisateurs/workspaces concernes.
 */
public interface NotificationPublisher {

    /**
     * Pousse une notification a un utilisateur specifique (ou tout un workspace si userId=null).
     *
     * @param userId      cible (ex: assigne d'un ticket). Si null, broadcast au workspace.
     * @param workspaceId workspace cible (utilise pour scope multi-tenant cote socket)
     * @param event       nom d'evenement Socket.io (ex: "notification", "ticket:transitioned")
     * @param payload     donnees serialisables en JSON (titre, body, link, type, etc.)
     */
    void publish(UUID userId, UUID workspaceId, String event, Map<String, Object> payload);

    /**
     * Helper: emet une notification standard "in-app" (event="notification") avec champs requis.
     */
    default void notify(UUID userId, UUID workspaceId, String type, String title, String body, String link) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("id", UUID.randomUUID().toString());
        payload.put("type", type);
        payload.put("title", title);
        payload.put("body", body);
        payload.put("link", link);
        payload.put("createdAt", java.time.Instant.now().toString());
        if (userId != null) payload.put("userId", userId.toString());
        if (workspaceId != null) payload.put("workspaceId", workspaceId.toString());
        publish(userId, workspaceId, "notification", payload);
    }

    /**
     * Emet une notification <b>PERSISTANTE</b> in-app (cloche topbar) vers un
     * destinataire precis. Contrairement a {@link #publish} (qui ne fait qu'un
     * broadcast Socket.io transitoire via {@code /emit}), cette voie passe par
     * {@code POST /internal/notifications} du realtime-service : la notification
     * est ecrite en base (table {@code notifications}) PUIS poussee en temps reel
     * ({@code notification:received}). C'est ce qui remplit la cloche et le
     * compteur "non lues".
     *
     * <p>Best-effort : un echec ne casse JAMAIS la transaction metier appelante.
     * Le destinataire {@code userId} est OBLIGATOIRE (le realtime-service refuse
     * une notification sans cible ; pas de broadcast workspace ici).
     *
     * @param userId      destinataire (la bonne personne, jamais l'acteur lui-meme)
     * @param workspaceId workspace cible (scope multi-tenant)
     * @param type        un des types connus (TICKET_ASSIGNED, DEADLINE_DUE,
     *                    PAYMENT_VALIDATED, WORKFLOW_COMPLETED, WORKSPACE_EVENT, ...)
     * @param title       titre court affiche en gras dans la cloche
     * @param message     corps du message
     * @param actionUrl   lien de navigation au clic (ex: {@code /tickets/{id}}), ou null
     * @param metadata    donnees additionnelles serialisables (ou null)
     */
    default void notifyUser(UUID userId, UUID workspaceId, String type, String title,
                            String message, String actionUrl, Map<String, Object> metadata) {
        // Fallback transitoire pour les implementations qui ne persistent pas :
        // au moins un push Socket.io est tente. HttpNotificationPublisher override
        // cette methode pour persister via /internal/notifications.
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("id", UUID.randomUUID().toString());
        payload.put("type", type);
        payload.put("title", title);
        payload.put("message", message);
        payload.put("actionUrl", actionUrl);
        if (metadata != null) payload.put("metadata", metadata);
        payload.put("createdAt", java.time.Instant.now().toString());
        if (userId != null) payload.put("userId", userId.toString());
        if (workspaceId != null) payload.put("workspaceId", workspaceId.toString());
        publish(userId, workspaceId, "notification", payload);
    }
}
