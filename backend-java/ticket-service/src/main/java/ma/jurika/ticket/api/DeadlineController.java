package ma.jurika.ticket.api;

import jakarta.validation.Valid;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.ticket.api.dto.CreateDeadlineRequest;
import ma.jurika.ticket.api.dto.DeadlineDto;
import ma.jurika.ticket.application.DeadlineUseCase;
import ma.jurika.ticket.domain.model.DeadlineStatut;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API REST pour la gestion des deadlines metier.
 * <p>
 * Killer Feature §4.2 -- echeances automatiques (CN_EXPIRY_90D, RC_DEPOT_3M, CNSS_DECL_30D, etc.)
 * + echeances manuelles. Endpoints :
 * <ul>
 *   <li>{@code GET    /api/v1/deadlines} -- liste filtrable</li>
 *   <li>{@code GET    /api/v1/deadlines/overdue} -- en retard</li>
 *   <li>{@code GET    /api/v1/deadlines/count-open} -- compteur widget topbar</li>
 *   <li>{@code POST   /api/v1/deadlines} -- creation manuelle</li>
 *   <li>{@code PATCH  /api/v1/deadlines/{id}/complete}</li>
 *   <li>{@code PATCH  /api/v1/deadlines/{id}/dismiss}</li>
 *   <li>{@code GET    /api/v1/tickets/{ticketId}/deadlines}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/deadlines")
public class DeadlineController {

    private final DeadlineUseCase useCase;

    public DeadlineController(DeadlineUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                     @RequestParam(required = false) DeadlineStatut statut,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                     @RequestParam(defaultValue = "50") int limit) {
        List<DeadlineDto> items = useCase.list(actor.workspaceId(), statut, from, to, limit)
                .stream().map(DeadlineDto::from).toList();
        return Map.of("items", items, "total", items.size());
    }

    @GetMapping("/overdue")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> overdue(@AuthenticationPrincipal AuthenticatedUser actor,
                                        @RequestParam(defaultValue = "50") int limit) {
        List<DeadlineDto> items = useCase.listOverdue(actor.workspaceId(), limit)
                .stream().map(DeadlineDto::from).toList();
        return Map.of("items", items, "total", items.size());
    }

    @GetMapping("/count-open")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Long> countOpen(@AuthenticationPrincipal AuthenticatedUser actor) {
        return Map.of("count", useCase.countOpen(actor.workspaceId()));
    }

    @PostMapping
    // SUPERVISEUR = oversight only : lecture des deadlines OK (GET ci-dessus),
    // mais aucune ecriture. Denial explicite -> 403 (super_admin exclu aussi).
    @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
    public ResponseEntity<DeadlineDto> create(@AuthenticationPrincipal AuthenticatedUser actor,
                                                @Valid @RequestBody CreateDeadlineRequest req) {
        var d = useCase.createManual(new DeadlineUseCase.ManualCreateCommand(
                actor.workspaceId(), req.ticketId(), req.dossierId(),
                req.title(), req.description(), req.dueAt(), req.severity(),
                req.assigneId(), actor.userId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(DeadlineDto.from(d));
    }

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
    public DeadlineDto complete(@AuthenticationPrincipal AuthenticatedUser actor,
                                  @PathVariable UUID id) {
        return DeadlineDto.from(useCase.markCompleted(actor.workspaceId(), id));
    }

    @PatchMapping("/{id}/dismiss")
    @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
    public DeadlineDto dismiss(@AuthenticationPrincipal AuthenticatedUser actor,
                                @PathVariable UUID id) {
        return DeadlineDto.from(useCase.dismiss(actor.workspaceId(), id));
    }
}
