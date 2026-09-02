package fr.maghreb.gje.controllers;

import fr.maghreb.gje.models.*;
import fr.maghreb.gje.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation
    .AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService
        notificationService;

    @GetMapping
    public ResponseEntity<List<Notification>> getAll(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
            notificationService.getMyNotifications(
                user.getId(),
                user.getWorkspaceId()));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> unread(
            @AuthenticationPrincipal User user) {
        long count = notificationService.countUnread(
            user.getId(), user.getWorkspaceId());
        return ResponseEntity.ok(
            Map.of("unread_count", count));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> read(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
            notificationService.markAsRead(
                id,
                user.getId(),
                user.getWorkspaceId()));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Object>> readAll(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
            notificationService.markAllAsRead(
                user.getId(),
                user.getWorkspaceId()));
    }
}
