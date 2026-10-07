package ma.jurika.dataroom.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.dataroom.api.dto.IdentityDtos.ExtractedIdentityDto;
import ma.jurika.dataroom.api.dto.IdentityDtos.IdentityType;
import ma.jurika.dataroom.application.identity.IdentityExtractionService;
import ma.jurika.dataroom.domain.port.KieServiceClient.KieServiceUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Orchestration extraction d'identité (CIN / CN) via kie-service.
 * <p>
 * Un seul endpoint multipart : recto (requis), verso (optionnel selon type),
 * type métier, dossierId optionnel (présence = demande d'archivage), archive
 * (défaut true). RBAC :
 * <ul>
 *   <li>{@code EMPLOYE} : tout permis (extraction + archivage).</li>
 *   <li>{@code SUPERVISEUR} : refusé par la garde depuis le lot L0 (E5b) — l'extraction
 *       de pièces est un travail de saisie de l'employé (RG-VAR-09, CDC § 3.2). Le contrôle
 *       « archivage refusé » ci-dessous est conservé en défense en profondeur.</li>
 *   <li>{@code CLIENT}, {@code SUPER_ADMIN} : refusé.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/dataroom/identity")
@Tag(name = "Dataroom Identity", description = "Orchestration extraction d'identite (CIN / CN) via kie-service.")
public class IdentityController {

    private final IdentityExtractionService service;

    public IdentityController(IdentityExtractionService service) {
        this.service = service;
    }

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    @Operation(summary = "Extraire l'identite (CIN/CN) — appelle kie-service par face + fusion + archivage optionnel")
    @ApiResponse(responseCode = "200", description = "Extraction reussie")
    @ApiResponse(responseCode = "400", description = "Parametres invalides (recto manquant, etc.)")
    @ApiResponse(responseCode = "403", description = "SUPERVISEUR ne peut pas archiver")
    @ApiResponse(responseCode = "503", description = "kie-service indisponible")
    public ExtractedIdentityDto extract(@AuthenticationPrincipal AuthenticatedUser user,
                                         @RequestParam("recto") MultipartFile recto,
                                         @RequestParam(value = "verso", required = false) MultipartFile verso,
                                         @RequestParam("type") IdentityType type,
                                         @RequestParam(value = "dossierId", required = false) UUID dossierId,
                                         @RequestParam(value = "archive", required = false, defaultValue = "true") boolean archive) {
        boolean wantsArchive = archive && dossierId != null;
        if (wantsArchive && user.role() == Role.SUPERVISEUR) {
            throw new AccessDeniedException("SUPERVISEUR : archivage interdit (lecture seule).");
        }

        try {
            return service.extract(type, recto, verso, dossierId, wantsArchive, user.userId());
        } catch (KieServiceUnavailableException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "kie-service indisponible : " + ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
