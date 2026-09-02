package ma.jurika.dataroom.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.dataroom.api.dto.DataroomDtos.ExerciceFiscalSummary;
import ma.jurika.dataroom.api.dto.DataroomDtos.OpenExerciceRequest;
import ma.jurika.dataroom.api.dto.DataroomDtos.UnlockExerciceRequest;
import ma.jurika.dataroom.application.ExerciceFiscalService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Sprint 8 -- Endpoints exercices fiscaux : ouverture/cloture/verrouillage/deverrouillage.
 *
 * RBAC :
 *  - open/cloturer/verrouiller : ROLE_EMPLOYE
 *  - deverrouiller : ROLE_SUPERVISEUR uniquement (RG-DF26)
 */
@RestController
@RequestMapping("/api/v1/dataroom")
@Tag(name = "Exercices Fiscaux",
        description = "Sprint 8 -- gestion du cycle de vie des exercices fiscaux (OUVERT/CLOTURE/VERROUILLE)")
public class ExercicesController {

    private final ExerciceFiscalService service;

    public ExercicesController(ExerciceFiscalService service) {
        this.service = service;
    }

    @GetMapping("/dossiers/{dossierId}/exercices")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    public List<ExerciceFiscalSummary> list(@PathVariable UUID dossierId) {
        return service.listForDossier(dossierId);
    }

    @PostMapping("/dossiers/{dossierId}/exercices")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    @Operation(summary = "Ouvrir un nouvel exercice (conforme au comptable)",
            description = "RG-DF03 + RG-DF20 -- le COMPTABLE est la timeline maitresse : "
                    + "l'exercice fiscal reprend les dates de l'annee comptable correspondante "
                    + "et genere ses echeances DGI propres. En ouverture manuelle "
                    + "(autoCreateComptable=false), une annee non tenue en comptabilite est rejetee (422). "
                    + "A la finalisation import/creation (autoCreateComptable=true), l'ancre comptable "
                    + "est creee a la volee pour rester conforme sans bloquer.")
    @ApiResponse(responseCode = "409", description = "Exercice fiscal deja ouvert (echeances generees)")
    @ApiResponse(responseCode = "422", description = "Annee non conforme aux annees comptables (RG-DF03)")
    public ExerciceFiscalSummary open(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable UUID dossierId,
                                       @Valid @RequestBody OpenExerciceRequest req) {
        LocalDate db = req.dateDebut() != null && !req.dateDebut().isBlank()
                ? LocalDate.parse(req.dateDebut()) : null;
        LocalDate df = req.dateFin() != null && !req.dateFin().isBlank()
                ? LocalDate.parse(req.dateFin()) : null;
        return service.open(dossierId, req.annee(), db, df, req.regimeTvaMensuel(),
                req.autoCreateComptable(), user.userId());
    }

    @PatchMapping("/exercices/{exerciceId}/cloturer")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    @Operation(summary = "Cloturer un exercice (OUVERT -> CLOTURE)")
    @ApiResponse(responseCode = "409", description = "Transition invalide (RG-DF25)")
    public ExerciceFiscalSummary cloturer(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable UUID exerciceId) {
        return service.cloturer(exerciceId, user.userId());
    }

    @PatchMapping("/exercices/{exerciceId}/verrouiller")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    @Operation(summary = "Verrouiller un exercice (controle fiscal DGI en cours)")
    @ApiResponse(responseCode = "409", description = "Transition invalide (RG-DF25)")
    public ExerciceFiscalSummary verrouiller(@AuthenticationPrincipal AuthenticatedUser user,
                                               @PathVariable UUID exerciceId) {
        return service.verrouiller(exerciceId, user.userId());
    }

    @PatchMapping("/exercices/{exerciceId}/deverrouiller")
    @PreAuthorize("hasAuthority('ROLE_SUPERVISEUR')")
    @Operation(summary = "Deverrouiller un exercice (RG-DF26 -- superviseur uniquement + motif >= 20 chars)")
    @ApiResponse(responseCode = "422", description = "Motif manquant ou < 20 caracteres")
    @ApiResponse(responseCode = "409", description = "Exercice non VERROUILLE")
    public ExerciceFiscalSummary deverrouiller(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable UUID exerciceId,
                                                  @Valid @RequestBody UnlockExerciceRequest req) {
        return service.deverrouiller(exerciceId, req.motif(), user.userId());
    }
}
