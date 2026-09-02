package ma.jurika.dataroom.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.dataroom.api.dto.DataroomDtos.ComplementRequest;
import ma.jurika.dataroom.api.dto.DataroomDtos.CreateDemandeRequest;
import ma.jurika.dataroom.api.dto.DataroomDtos.CreateRequeteRequest;
import ma.jurika.dataroom.api.dto.DataroomDtos.DemandeSummary;
import ma.jurika.dataroom.api.dto.DataroomDtos.DemandeSupervisionRow;
import ma.jurika.dataroom.api.dto.DataroomDtos.RepondreRequest;
import ma.jurika.dataroom.api.dto.DataroomDtos.UpdateDemandeStatusRequest;
import ma.jurika.dataroom.application.DemandesClientService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Sprint 7 / TASK 6.2 -- Refactor : controller dedie aux demandes client.
 */
@RestController
@RequestMapping("/api/v1/dataroom")
@Tag(name = "Dataroom Demandes", description = "Demandes client (Non traitee / En cours / Traitee)")
public class DemandesController {

    private final DemandesClientService demandes;

    public DemandesController(DemandesClientService demandes) {
        this.demandes = demandes;
    }

    @PostMapping("/demandes")
    @PreAuthorize("hasAuthority('ROLE_CLIENT')")
    public DemandeSummary createDemande(@AuthenticationPrincipal AuthenticatedUser user,
                                          @Valid @RequestBody CreateDemandeRequest req) {
        return demandes.create(req, user.userId());
    }

    @GetMapping("/dossiers/{dossierId}/demandes")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    public List<DemandeSummary> demandesByDossier(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable UUID dossierId,
                                                  @RequestParam(required = false) String direction) {
        // Scoping par role : EMPLOYE non responsable -> 403 ; CLIENT limite a SON
        // dossier ; SUPERVISEUR / SUPER_ADMIN non restreints (cf. listByDossier).
        // Lot AG : `direction` (defaut CLIENT_TO_EMPLOYE) separe les demandes
        // client->employe (inchangees) des requetes employe->client.
        return demandes.listByDossier(dossierId, user.userId(), user.role(), direction);
    }

    @GetMapping("/demandes")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE')")
    public List<DemandeSummary> demandesAll(@AuthenticationPrincipal AuthenticatedUser user,
                                            @RequestParam(required = false) String statut,
                                            @RequestParam(required = false) String direction) {
        // Scoping par role (2026-07-03) : l'EMPLOYE ne voit que les demandes des
        // dossiers dont il est responsable (suivent le dossier apres transfert) ;
        // SUPERVISEUR / SUPER_ADMIN gardent la vue globale du workspace.
        if (user.role() == Role.EMPLOYE) {
            return demandes.listAllForEmployee(user.userId(), statut, direction);
        }
        return demandes.listAll(statut, direction);
    }

    /**
     * Vue SUPERVISEUR (workspace-wide) enrichie : toutes les demandes OU requetes
     * du cabinet avec le nom du dataroom + l'employe responsable resolus par
     * jointure. {@code direction} = CLIENT_TO_EMPLOYE (demandes des clients,
     * defaut) ou EMPLOYE_TO_CLIENT (requetes aux clients). Lecture seule.
     */
    @GetMapping("/supervision/demandes")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')")
    public List<DemandeSupervisionRow> supervisionDemandes(@RequestParam(required = false) String statut,
                                                           @RequestParam(required = false) String direction) {
        return demandes.listAllEnriched(direction, statut);
    }

    @PatchMapping("/demandes/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPER_ADMIN')")
    public DemandeSummary updateDemande(@AuthenticationPrincipal AuthenticatedUser user,
                                          @PathVariable UUID id,
                                          @Valid @RequestBody UpdateDemandeStatusRequest req) {
        // Le service applique le controle d'ownership : un EMPLOYE non responsable
        // du dossier -> 403 (AccessDenied). SUPER_ADMIN = override plateforme.
        return demandes.updateStatus(id, req, user.userId(), user.role());
    }

    // =====================================================================
    // Lot AG -- « Requetes au client » (EMPLOYE_TO_CLIENT). Ecrans separes.
    // =====================================================================

    /** L'employe responsable (ou superviseur) cree une requete au client. */
    @PostMapping("/requetes")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')")
    public DemandeSummary createRequete(@AuthenticationPrincipal AuthenticatedUser user,
                                        @Valid @RequestBody CreateRequeteRequest req) {
        return demandes.createRequete(req, user.userId(), user.role());
    }

    /** Le CLIENT du dossier repond / fournit -> REPONDUE. */
    @PostMapping("/demandes/{id}/repondre")
    @PreAuthorize("hasAuthority('ROLE_CLIENT')")
    public DemandeSummary repondre(@AuthenticationPrincipal AuthenticatedUser user,
                                   @PathVariable UUID id,
                                   @Valid @RequestBody(required = false) RepondreRequest req) {
        return demandes.repondre(id, req, user.userId(), user.role());
    }

    /** L'employe responsable (ou superviseur) valide -> CLOTUREE. */
    @PostMapping("/demandes/{id}/valider")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')")
    public DemandeSummary valider(@AuthenticationPrincipal AuthenticatedUser user,
                                  @PathVariable UUID id) {
        return demandes.valider(id, user.userId(), user.role());
    }

    /** L'employe responsable (ou superviseur) demande un complement -> A_COMPLETER. */
    @PostMapping("/demandes/{id}/complement")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')")
    public DemandeSummary complement(@AuthenticationPrincipal AuthenticatedUser user,
                                     @PathVariable UUID id,
                                     @Valid @RequestBody ComplementRequest req) {
        return demandes.complement(id, req, user.userId(), user.role());
    }
}
