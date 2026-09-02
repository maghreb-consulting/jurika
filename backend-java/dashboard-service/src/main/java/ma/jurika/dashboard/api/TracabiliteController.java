package ma.jurika.dashboard.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ma.jurika.dashboard.api.dto.TracabiliteDtos.TracabilitePage;
import ma.jurika.dashboard.application.TracabiliteQueryService;
import ma.jurika.dashboard.application.TracabiliteQueryService.Filter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Traçabilité (E2) — lecture de audit_log scopee au workspace courant (JWT).
 *
 * <ul>
 *   <li>{@code GET /api/v1/tracabilite} : page "qui-a-fait-quoi-quand" du
 *       workspace, reservee SUPERVISEUR (+ SUPER_ADMIN scoped a son workspace).</li>
 *   <li>{@code GET /api/v1/tracabilite/entite} : historique d'une entite precise
 *       (dossier / ticket), ouvert a EMPLOYE + SUPERVISEUR (panneau "Activite"
 *       contextuel).</li>
 * </ul>
 *
 * Le workspace n'est jamais un parametre : il provient du TenantContext (JWT).
 */
@RestController
@RequestMapping("/api/v1/tracabilite")
@Tag(name = "Tracabilite",
        description = "E2 — journal d'activite (audit_log) scope au workspace courant, libelles FR.")
public class TracabiliteController {

    private final TracabiliteQueryService service;

    public TracabiliteController(TracabiliteQueryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')")
    @Operation(summary = "Journal d'activite du workspace (filtres + pagination, created_at DESC)")
    public TracabilitePage search(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String actorStatus,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return service.search(new Filter(entityType, entityId, userId, action, from, to, actorStatus, limit, offset));
    }

    @GetMapping("/entite")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')")
    @Operation(summary = "Historique d'activite d'une entite precise (dossier / ticket)")
    public TracabilitePage entityHistory(
            @RequestParam @NotBlank String entityType,
            @RequestParam @NotNull UUID entityId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return service.search(new Filter(entityType, entityId, null, null, null, null, null, limit, offset));
    }
}
