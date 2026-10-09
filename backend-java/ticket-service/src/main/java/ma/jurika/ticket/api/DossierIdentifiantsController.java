package ma.jurika.ticket.api;

import jakarta.validation.Valid;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.ticket.domain.model.TaxeProfessionnelleVersion;
import org.springframework.web.bind.annotation.GetMapping;
import ma.jurika.ticket.api.dto.DossierIdentifiantsDtos.DossierIdentifiantsView;
import ma.jurika.ticket.api.dto.DossierIdentifiantsDtos.UpdateIdentifiantsRequest;
import ma.jurika.ticket.application.DossierIdentifiantsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Fiche client (2026-07-14) — edition des identifiants de la societe
 * (post-immatriculation). Reserve EMPLOYE (responsable, verifie en service) /
 * SUPERVISEUR / SUPER_ADMIN. Le CLIENT est exclu (pas de ROLE_CLIENT).
 */
@RestController
public class DossierIdentifiantsController {

    private final DossierIdentifiantsService service;

    public DossierIdentifiantsController(DossierIdentifiantsService service) {
        this.service = service;
    }

    // RG-U02-04 : le SUPERVISEUR consulte tout en LECTURE SEULE. L'edition des
    // identifiants (donnee metier d'un dossier client) est reservee a l'EMPLOYE
    // responsable du dossier ; SUPERVISEUR / SUPER_ADMIN / CLIENT -> 403.
    @PatchMapping("/api/v1/dossiers/{dossierId}/identifiants")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    public DossierIdentifiantsView update(@AuthenticationPrincipal AuthenticatedUser actor,
                                          @PathVariable UUID dossierId,
                                          @Valid @RequestBody UpdateIdentifiantsRequest req) {
        return service.update(actor.workspaceId(), actor.role(), actor.userId(), dossierId, req);
    }

    /**
     * Lot L1 (RG-FIC-02) : versions successives de la taxe professionnelle (la derniere en
     * vigueur). Employe responsable ou superviseur ; jamais le client.
     */
    @GetMapping("/api/v1/dossiers/{dossierId}/taxe-professionnelle/versions")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR') and !hasAuthority('ROLE_SUPER_ADMIN')")
    public List<TaxeProfessionnelleVersion> versionsTp(@AuthenticationPrincipal AuthenticatedUser actor,
                                                       @PathVariable UUID dossierId) {
        return service.versionsTp(actor.workspaceId(), actor.userId(),
                actor.role() == Role.SUPERVISEUR, dossierId);
    }
}
