package fr.maghreb.gje.services;

import fr.maghreb.gje.config.TenantContext;
import fr.maghreb.gje.models.Notification;
import fr.maghreb.gje.repositories.NotificationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public List<Notification> getMyNotifications(
            UUID userId, UUID workspaceId) {
        entityManager.createNativeQuery(
            "SET LOCAL app.current_tenant_id = '"
            + workspaceId.toString() + "'")
            .executeUpdate();
        TenantContext.setTenantId(workspaceId.toString());
        return notificationRepository
            .findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public Map<String, Object> markAsRead(
            UUID id, UUID userId, UUID workspaceId) {
        entityManager.createNativeQuery(
            "SET LOCAL app.current_tenant_id = '"
            + workspaceId.toString() + "'")
            .executeUpdate();
        TenantContext.setTenantId(workspaceId.toString());
        Notification notif = notificationRepository
            .findById(id)
            .orElseThrow(() ->
                new RuntimeException(
                    "Notification introuvable"));
        notif.setIsRead(true);
        notificationRepository.save(notif);
        return Map.of(
            "message", "Notification lue",
            "id", id.toString());
    }

    @Transactional
    public Map<String, Object> markAllAsRead(
            UUID userId, UUID workspaceId) {
        entityManager.createNativeQuery(
            "SET LOCAL app.current_tenant_id = '"
            + workspaceId.toString() + "'")
            .executeUpdate();
        TenantContext.setTenantId(workspaceId.toString());
        notificationRepository.markAllAsRead(userId);
        return Map.of(
            "message",
            "Toutes les notifications marquées comme lues");
    }

    @Transactional
    public long countUnread(
            UUID userId, UUID workspaceId) {
        entityManager.createNativeQuery(
            "SET LOCAL app.current_tenant_id = '"
            + workspaceId.toString() + "'")
            .executeUpdate();
        TenantContext.setTenantId(workspaceId.toString());
        return notificationRepository
            .findByUserIdAndIsReadFalse(userId)
            .size();
    }
}
