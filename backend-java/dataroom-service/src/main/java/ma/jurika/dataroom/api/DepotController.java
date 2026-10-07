package ma.jurika.dataroom.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.dataroom.api.dto.DataroomDtos.DepotSummary;
import ma.jurika.dataroom.application.ClientDataroomPermissionGuard;
import ma.jurika.dataroom.application.DataroomDepotService;
import ma.jurika.dataroom.application.OfficePreviewSupport;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import ma.jurika.dataroom.infrastructure.persistence.DepotEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Lot V -- Espace « Depots » client (depot libre + consultation employe).
 *
 * <p>Base mapping identique a {@link ComptableController} ({@code /api/v1/dataroom}).
 * Le CLIENT depose (gate par perm_depot) ; l'EMPLOYE responsable / SUPERVISEUR /
 * SUPER_ADMIN consulte (Voir inline + Telecharger). Le scoping responsable /
 * client est applique dans {@link DataroomDepotService}.
 */
@RestController
@RequestMapping("/api/v1/dataroom")
@Tag(name = "Dataroom Depots", description = "Espace Depots : depot libre client (perm_depot) + consultation employe responsable")
public class DepotController {

    private final DataroomDepotService depots;
    private final ClientDataroomPermissionGuard permissionGuard;
    private final OfficePreviewSupport officePreview;
    private final ObjectStorage storage;

    public DepotController(DataroomDepotService depots,
                           ClientDataroomPermissionGuard permissionGuard,
                           OfficePreviewSupport officePreview,
                           ObjectStorage storage) {
        this.depots = depots;
        this.permissionGuard = permissionGuard;
        this.officePreview = officePreview;
        this.storage = storage;
    }

    @PostMapping(value = "/dossiers/{dossierId}/depots/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_CLIENT','ROLE_EMPLOYE')")
    @Operation(summary = "Deposer un fichier libre (CLIENT gate par perm_depot ; EMPLOYE/superviseur non gates)")
    public DepotSummary upload(@AuthenticationPrincipal AuthenticatedUser user,
                               @PathVariable UUID dossierId,
                               @RequestParam("file") MultipartFile file,
                               @RequestParam(required = false) String title) {
        // Depot client : gate par perm_depot (no-op hors CLIENT). Le scoping
        // responsable / client (ownership du dossier) est verifie dans le service.
        permissionGuard.assertCanDepot(dossierId, user);
        return depots.upload(dossierId, title, file, user.userId(), user.role());
    }

    @GetMapping("/dossiers/{dossierId}/depots")
    @PreAuthorize("hasAnyAuthority('ROLE_CLIENT','ROLE_EMPLOYE','ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')")
    @Operation(summary = "Lister les depots d'un dossier (scope : CLIENT=son dossier, EMPLOYE=responsable, superviseur=tout)")
    public List<DepotSummary> list(@AuthenticationPrincipal AuthenticatedUser user,
                                   @PathVariable UUID dossierId) {
        return depots.list(dossierId, user.userId(), user.role());
    }

    @GetMapping("/depots/{id}/download")
    @PreAuthorize("hasAnyAuthority('ROLE_CLIENT','ROLE_EMPLOYE','ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')")
    @Operation(summary = "Telecharger un depot (CLIENT telecharge SES depots inconditionnellement, hors perm_download)")
    public ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal AuthenticatedUser user,
                                                        @PathVariable UUID id) {
        // CLIENT : ce sont SES fichiers -> NON gate par perm_download. Le scoping
        // (ownership du dossier) suffit et est applique dans getForStream.
        DepotEntity e = depots.getForStream(id, user.userId(), user.role());
        var r = storage.download(e.getObjectKey());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + e.getFilename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE,
                        e.getContentType() != null ? e.getContentType()
                                : (r.contentType() != null ? r.contentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(r.size()))
                .body(new InputStreamResource(r.stream()));
    }

    @GetMapping("/depots/{id}/preview")
    @PreAuthorize("hasAnyAuthority('ROLE_CLIENT','ROLE_EMPLOYE','ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')")
    @Operation(summary = "Apercu inline d'un depot (Content-Disposition: inline pour PdfPreviewModal : PDF + images)")
    public ResponseEntity<InputStreamResource> preview(@AuthenticationPrincipal AuthenticatedUser user,
                                                       @PathVariable UUID id) {
        DepotEntity e = depots.getForStream(id, user.userId(), user.role());
        // Lot AB — Word/Excel converti en PDF a la volee (fallback original sinon).
        OfficePreviewSupport.Rendered rd =
                officePreview.render(e.getObjectKey(), e.getFilename(), e.getContentType());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + rd.filename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, rd.contentType())
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(rd.size()))
                .body(new InputStreamResource(rd.stream()));
    }

    @DeleteMapping("/depots/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_CLIENT','ROLE_EMPLOYE')")
    @Operation(summary = "Supprimer (soft-delete) un depot : CLIENT sur SON dossier, EMPLOYE responsable, superviseur")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable UUID id) {
        depots.softDelete(id, user.userId(), user.role());
        return ResponseEntity.noContent().build();
    }
}
