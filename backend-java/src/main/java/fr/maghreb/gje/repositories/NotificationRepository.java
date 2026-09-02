package fr.maghreb.gje.repositories;

import fr.maghreb.gje.models.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

public interface NotificationRepository
    extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(
        UUID userId);

    List<Notification> findByUserIdAndIsReadFalse(
        UUID userId);

    @Query("SELECT COUNT(n) FROM Notification n " +
        "WHERE n.userId = :userId " +
        "AND n.type = :type " +
        "AND n.createdAt > :since")
    long countRecentByUserAndType(
        @Param("userId") UUID userId,
        @Param("type") String type,
        @Param("since") LocalDateTime since);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true " +
        "WHERE n.userId = :userId")
    void markAllAsRead(@Param("userId") UUID userId);

    boolean existsByWorkspaceIdAndTypeAndCreatedAtAfter(
        UUID workspaceId, String type, LocalDateTime since);
}
