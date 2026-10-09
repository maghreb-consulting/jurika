package ma.jurika.ticket.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.ticket.application.TicketNoteService;
import ma.jurika.ticket.domain.model.TicketNote;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Lot L1 : note de ticket (RG-TKT-07). Strictement interne : jamais le client
 * (gardes par autorite exacte, sans heritage EMPLOYE > CLIENT dans ce sens).
 */
@RestController
public class TicketNoteController {

    private final TicketNoteService service;

    public TicketNoteController(TicketNoteService service) {
        this.service = service;
    }

    /** Corps : contenu de la note, vide accepte ("validable meme vide"). */
    public record NotePayload(@Size(max = 20000) String contenu) {}

    @GetMapping("/api/v1/tickets/{ticketId}/note")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR') and !hasAuthority('ROLE_SUPER_ADMIN')")
    public TicketNote lire(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID ticketId) {
        return service.lire(actor.workspaceId(), actor.userId(), actor.role() == Role.SUPERVISEUR, ticketId);
    }

    @PutMapping("/api/v1/tickets/{ticketId}/note")
    @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
    public TicketNote enregistrer(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID ticketId,
                                  @Valid @RequestBody(required = false) NotePayload req) {
        return service.enregistrer(actor.workspaceId(), actor.userId(), ticketId, req == null ? "" : req.contenu());
    }
}
