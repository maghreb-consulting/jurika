package ma.jurika.ticket.api;

import jakarta.validation.Valid;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.ticket.api.dto.DemarcheRequests;
import ma.jurika.ticket.application.DemarcheUseCases;
import ma.jurika.ticket.application.RecapitulatifClotureService;
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
    private final RecapitulatifClotureService recapitulatif;

    public DemarcheController(DemarcheUseCases demarches,
                               RecapitulatifClotureService recapitulatif) {
        this.demarches = demarches;
        this.recapitulatif = recapitulatif;
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

    /**
     * Lot B — ANNULER UN COCHAGE EXIGE UN MOTIF.
     *
     * <p>Le cochage reste annulable apres validation, par l'employe : c'est la
     * decision du cabinet. Mais l'annulation se justifie, et les deux horodatages
     * — celui du cochage et celui de l'annulation — sont conserves tous les deux
     * au journal de la demarche.
     */
    @PostMapping("/{ordre}/decocher")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    public DemarcheUseCases.Vue decocher(@AuthenticationPrincipal AuthenticatedUser user,
                                          @PathVariable UUID ticketId,
                                          @PathVariable int ordre,
                                          @Valid @RequestBody DemarcheRequests.Decocher body) {
        return demarches.decocher(user.workspaceId(), ticketId, ordre, body.motif(), user.userId());
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

    /**
     * Lot B (2026-09-11) — LE RECAPITULATIF DU TICKET, AVANT DE LE CLORE.
     *
     * <p>Les documents produits, les demarches accomplies avec leurs dates, les
     * justificatifs archives et CEUX QUI MANQUENT, les identifiants obtenus.
     *
     * <p>Ouvert au superviseur : il doit pouvoir constater l'avancement. Ouvert
     * aussi apres la cloture — c'est cette meme vue que le detail d'un ticket
     * clos affiche, en lecture seule. Aucune action n'y est attachee : cet
     * endpoint ne modifie rien, la cloture reste une transition de statut,
     * decidee explicitement.
     */
    @GetMapping("/recapitulatif")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')")
    public RecapitulatifClotureService.Recapitulatif recapitulatif(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID ticketId) {
        return recapitulatif.recapitulatif(user.workspaceId(), ticketId);
    }

    /**
     * Lot 5 (2026-09-07) — propage la reponse « gerance designee dans les statuts ? »
     * aux trois demarches qui portent cette condition (9, 15 et 18), au lieu de
     * demander a l'employe de les ecarter une par une avec le meme motif.
     */
    @PostMapping("/condition-gerance")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    public DemarcheUseCases.Vue conditionGerance(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable UUID ticketId,
                                                  @Valid @RequestBody
                                                  DemarcheRequests.ConditionGerance body) {
        return demarches.appliquerConditionGerance(user.workspaceId(), ticketId,
                body.statutaire(), user.userId());
    }
}
