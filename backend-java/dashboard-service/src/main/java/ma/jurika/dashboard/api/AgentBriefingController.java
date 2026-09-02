package ma.jurika.dashboard.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.dashboard.api.dto.AgentDtos.AgentBriefing;
import ma.jurika.dashboard.application.AgentCopiloteService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Lot IA-1 — API de l'agent copilote (EMPLOYE). Un employe n'accede qu'a SON
 * briefing : {@code employee_id = user.userId()} (jamais celui d'un autre).
 *
 * <p>L'agent PROPOSE, l'humain VALIDE : ces endpoints ne declenchent aucune
 * action metier (lecture seule cote tickets/demandes/dossiers).
 */
@RestController
@RequestMapping("/api/v1/agent")
@Tag(name = "Agent Copilote",
        description = "Lot IA-1 -- briefing du jour de l'employe (echeances, reste-a-faire). Suggestions read-only.")
public class AgentBriefingController {

    private final AgentCopiloteService service;

    public AgentBriefingController(AgentCopiloteService service) {
        this.service = service;
    }

    @GetMapping("/briefing/me")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')")
    @Operation(summary = "Dernier briefing du jour de l'employe (genere si absent/obsolete)")
    public AgentBriefing myBriefing(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.getMyBriefing(user.workspaceId(), user.userId());
    }

    @PostMapping("/briefing/refresh")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')")
    @Operation(summary = "Regenere le briefing du jour")
    public AgentBriefing refresh(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.refresh(user.workspaceId(), user.userId());
    }

    @PostMapping("/briefing/{id}/seen")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')")
    @Operation(summary = "Marque un briefing comme vu (scoping strict : uniquement le sien)")
    public ResponseEntity<AgentBriefing> markSeen(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable UUID id) {
        return service.markSeen(user.workspaceId(), user.userId(), id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
