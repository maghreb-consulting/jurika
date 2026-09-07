package ma.jurika.ticket.api;

import jakarta.validation.Valid;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.ticket.api.dto.DemarcheRequests;
import ma.jurika.ticket.application.DemarcheUseCases;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Cochage des demarches d'un ticket.
 *
 * <p>Reserve a l'EMPLOYE : le superviseur consulte les tickets mais ne les
 * manipule pas, et le client n'a acces qu'a sa Data Room. La lecture reste
 * ouverte au superviseur, qui doit pouvoir constater l'avancement.
 */
@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/demarches")
public class DemarcheController {

    private final DemarcheUseCases demarches;

    public DemarcheController(DemarcheUseCases demarches) {
        this.demarches = demarches;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')")
    public DemarcheUseCases.Vue vue(@AuthenticationPrincipal AuthenticatedUser user,
                                     @PathVariable UUID ticketId) {
        return demarches.vue(user.workspaceId(), ticketId);
    }

    @PostMapping("/{ordre}/cocher")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    public DemarcheUseCases.Vue cocher(@AuthenticationPrincipal AuthenticatedUser user,
                                        @PathVariable UUID ticketId,
                                        @PathVariable int ordre,
                                        @Valid @RequestBody DemarcheRequests.Cocher body) {
        return demarches.cocher(user.workspaceId(), ticketId, ordre,
                body.documentIds(), user.userId());
    }

    @PostMapping("/{ordre}/decocher")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    public DemarcheUseCases.Vue decocher(@AuthenticationPrincipal AuthenticatedUser user,
                                          @PathVariable UUID ticketId,
                                          @PathVariable int ordre) {
        return demarches.decocher(user.workspaceId(), ticketId, ordre, user.userId());
    }

    @PostMapping("/{ordre}/non-applicable")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    public DemarcheUseCases.Vue nonApplicable(@AuthenticationPrincipal AuthenticatedUser user,
                                               @PathVariable UUID ticketId,
                                               @PathVariable int ordre,
                                               @Valid @RequestBody DemarcheRequests.NonApplicable body) {
        return demarches.marquerNonApplicable(user.workspaceId(), ticketId, ordre,
                body.motif(), user.userId());
    }
}
