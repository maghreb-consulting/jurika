package ma.jurika.auth.application;

import ma.jurika.auth.infrastructure.persistence.WorkspaceEntity;
import ma.jurika.auth.infrastructure.persistence.WorkspaceJpaRepository;
import ma.jurika.common.audit.Auditable;
import ma.jurika.common.events.BusinessEventPublisher;
import ma.jurika.common.events.BusinessEventPublisher.EventPayload;
import ma.jurika.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sprint 12.5 / Sprint Beta (pricing-deploy) — TASK 5.
 *
 * <p>Met a jour {@code workspaces.preferred_theme} (V22) pour le
 * workspace courant. Audit log via @Auditable + business event
 * {@code WORKSPACE_THEME_CHANGED} (best-effort) pour le funnel
 * supervision.
 */
@Service
public class UpdateThemeUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateThemeUseCase.class);
    private static final Set<String> ALLOWED = Set.of("dark", "light");

    private final WorkspaceJpaRepository workspaceRepo;
    private final BusinessEventPublisher eventPublisher;

    /**
     * BusinessEventPublisher est optionnel (Sprint 11 TASK 6 — supervision
     * peut etre absent en dev). On l'injecte lazy via @Autowired(required=false).
     */
    @Autowired
    public UpdateThemeUseCase(WorkspaceJpaRepository workspaceRepo,
                              @Autowired(required = false) BusinessEventPublisher eventPublisher) {
        this.workspaceRepo = workspaceRepo;
        this.eventPublisher = eventPublisher;
    }

    @Auditable(action = "WORKSPACE_THEME_CHANGED", resourceType = "workspace")
    @Transactional
    public String execute(UUID workspaceId, UUID userId, String theme) {
        if (workspaceId == null) {
            throw new IllegalArgumentException("workspaceId requis");
        }
        String normalized = theme == null ? "" : theme.toLowerCase().trim();
        if (!ALLOWED.contains(normalized)) {
            throw new BusinessException("THEME_INVALID",
                    "theme doit etre 'dark' ou 'light' (recu : '" + theme + "')");
        }
        WorkspaceEntity ws = workspaceRepo.findById(workspaceId)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND",
                        "Workspace introuvable : " + workspaceId));
        if (normalized.equals(ws.getPreferredTheme())) {
            // No-op — meme valeur. Pas d'event emis pour ne pas polluer.
            return normalized;
        }
        String previous = ws.getPreferredTheme();
        ws.setPreferredTheme(normalized);
        workspaceRepo.save(ws);
        log.info("Theme update workspace={} {} -> {}", workspaceId, previous, normalized);

        // Best-effort business event (Sprint 11 TASK 6 — supervision absent en local OK).
        if (eventPublisher != null) {
            try {
                eventPublisher.publish(
                        EventPayload.of("WORKSPACE_THEME_CHANGED", workspaceId,
                                Map.of("previous", previous, "next", normalized,
                                        "userId", userId.toString())),
                        null);
            } catch (RuntimeException ex) {
                log.warn("BusinessEvent WORKSPACE_THEME_CHANGED publish failed (non-bloquant) : {}",
                        ex.getMessage());
            }
        }
        return normalized;
    }

    /** Lecture simple (pas d'audit, hot path). */
    @Transactional(readOnly = true)
    public String getCurrentTheme(UUID workspaceId) {
        return workspaceRepo.findById(workspaceId)
                .map(WorkspaceEntity::getPreferredTheme)
                .orElse("light");
    }
}
