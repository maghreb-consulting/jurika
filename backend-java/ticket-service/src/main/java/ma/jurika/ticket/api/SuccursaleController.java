package ma.jurika.ticket.api;

import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.api.dto.SuccursaleDto;
import ma.jurika.ticket.infrastructure.persistence.DossierEntity;
import ma.jurika.ticket.infrastructure.persistence.DossierJpaRepository;
import ma.jurika.ticket.infrastructure.persistence.SuccursaleJpaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Liste des succursales ACTIVE d'une societe mere (2026-07-05).
 *
 * <p>Alimente le second &lt;Select&gt; du workflow FERMETURE_SUCCURSALE :
 * l'employe choisit la mere puis SA succursale, et les champs connus
 * (RC secondaire, ville, adresse, denomination, directeur) sont auto-remplis.
 *
 * <p>Scope (defense-in-depth, RLS non fiable car jurika_user BYPASSRLS) :
 * <ul>
 *   <li>SUPERVISEUR / SUPER_ADMIN : toutes les succursales du workspace ;</li>
 *   <li>EMPLOYE : uniquement celles dont il est responsable du dossier mere
 *       ({@code responsable_id = userId}).</li>
 * </ul>
 * En cas de dossier hors workspace / hors scope : liste vide (le front bascule
 * alors sur la saisie manuelle legacy — non bloquant).
 */
@RestController
public class SuccursaleController {

    private final SuccursaleJpaRepository succursales;
    private final DossierJpaRepository dossiers;

    public SuccursaleController(SuccursaleJpaRepository succursales,
                                DossierJpaRepository dossiers) {
        this.succursales = succursales;
        this.dossiers = dossiers;
    }

    @GetMapping("/api/v1/dossiers/{parentDossierId}/succursales")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE')")
    @Transactional(readOnly = true)
    public List<SuccursaleDto> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @PathVariable UUID parentDossierId) {
        TenantContext.set(actor.workspaceId());

        // Le dossier mere doit appartenir au workspace de l'appelant.
        DossierEntity parent = dossiers.findById(parentDossierId).orElse(null);
        if (parent == null || !actor.workspaceId().equals(parent.getWorkspaceId())) {
            return List.of();
        }
        // EMPLOYE : uniquement les succursales des dossiers dont il est responsable.
        if (actor.role() == Role.EMPLOYE
                && !actor.userId().equals(parent.getResponsableId())) {
            return List.of();
        }

        return succursales
                .findByWorkspaceIdAndParentDossierIdAndStatutOrderByDenominationAscCreatedAtAsc(
                        actor.workspaceId(), parentDossierId, "ACTIVE")
                .stream()
                .map(SuccursaleDto::from)
                .toList();
    }
}
