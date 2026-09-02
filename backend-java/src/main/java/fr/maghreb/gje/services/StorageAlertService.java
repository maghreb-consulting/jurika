package fr.maghreb.gje.services;

import fr.maghreb.gje.events.WorkspaceNear90PercentEvent;
import fr.maghreb.gje.models.*;
import fr.maghreb.gje.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageAlertService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final HistoriqueRepository historiqueRepository;
    private final RestTemplate restTemplate = new RestTemplate(); // Obvious one-off for this demo, can be bean-injected

    private static final String NOTIF_TYPE = "STORAGE_90_PERCENT";
    private static final String NODE_EMIT_URL = "http://localhost:3000/emit";

    @EventListener
    @Transactional
    public void handleStorageAlert(WorkspaceNear90PercentEvent event) {
        UUID workspaceId = event.getWorkspaceId();
        
        // 1. Anti-spam: Check if alert sent in last 24h
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        if (notificationRepository.existsByWorkspaceIdAndTypeAndCreatedAtAfter(workspaceId, NOTIF_TYPE, since)) {
            log.info("Alerte quota déjà envoyée pour le workspace {} dans les dernières 24h. Skip.", workspaceId);
            return;
        }

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new RuntimeException("Workspace introuvable"));

        String message = String.format("Alerte : Le workspace [%s] a atteint %.1f%% de sa limite de stockage (%d GB / %d GB)",
                workspace.getName(),
                event.getPercentageUsed() * 100,
                event.getCurrentUsageBytes() / (1024 * 1024 * 1024),
                event.getLimitBytes() / (1024 * 1024 * 1024));

        // 2. Identify recipients
        List<User> recipients = new ArrayList<>();
        // Supervisor of the workspace
        recipients.addAll(userRepository.findByWorkspaceIdAndRole(workspaceId, User.Role.SUPERVISEUR));
        // All Super Admins
        recipients.addAll(userRepository.findByRole(User.Role.SUPER_ADMIN));

        // 3. Create Notifications
        for (User recipient : recipients) {
            Notification notif = Notification.builder()
                    .workspaceId(workspaceId)
                    .userId(recipient.getId())
                    .type(NOTIF_TYPE)
                    .message(message)
                    .isRead(false)
                    .build();
            notificationRepository.save(notif);
        }

        // 4. Emit WebSocket via Node.js
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("room", "workspace:" + workspaceId);
            payload.put("event", "storage_alert");
            payload.put("data", Map.of(
                "percentage", event.getPercentageUsed(),
                "message", message
            ));
            restTemplate.postForEntity(NODE_EMIT_URL, payload, String.class);
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi du signal WebSocket vers Node.js", e);
        }

        // 5. Log to Audit Trace
        historiqueRepository.save(Historique.builder()
                .workspaceId(workspaceId)
                .action("STORAGE_ALERT_90")
                .nouvelleValeur(String.format("%.1f%%", event.getPercentageUsed() * 100))
                .timestamp(LocalDateTime.now())
                .build());
                
        log.info("Alerte quota 90% traitée pour le workspace {}", workspaceId);
    }
}
