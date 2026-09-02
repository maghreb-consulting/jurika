package ma.jurika.dataroom.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.dataroom.api.dto.DataroomDtos.ComptableDocumentSummary;
import ma.jurika.dataroom.api.dto.DataroomDtos.DossierComptableView;
import ma.jurika.dataroom.application.ClientDataroomPermissionGuard;
import ma.jurika.dataroom.application.DataroomComptableService;
import ma.jurika.dataroom.application.OfficePreviewSupport;
import ma.jurika.dataroom.application.access.ClientAccessLogger;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import ma.jurika.dataroom.infrastructure.persistence.ComptableDocumentEntity;
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
 * Sprint 7 / TASK 6.2 -- Refactor : controller dedie au Dossier Comptable.
 *
 * Preserve /comptable/upload-batch (audit A5).
 * Sprint 8 ajoutera l'integration exercices_fiscaux (table V12) :
 *  - les uploads cibleront un exercice + categorie au lieu d'une annee libre
 *  - les comptes annuels passeront par dataroom_exercices_fiscaux.statut
 */
@RestController
@RequestMapping("/api/v1/dataroom")
@Tag(name = "Dataroom Comptable", description = "Dossier Comptable V2 : 6 categories x N annees, retention 10 ans CGI Art. 211")
public class ComptableController {

    private final DataroomComptableService comptable;
    private final ClientDataroomPermissionGuard permissionGuard;
    private final ClientAccessLogger accessLogger;
    private final OfficePreviewSupport officePreview;
    private final ObjectStorage storage;

    public ComptableController(DataroomComptableService comptable,
                               ClientDataroomPermissionGuard permissionGuard,
                               ClientAccessLogger accessLogger,
                               OfficePreviewSupport officePreview,
                               ObjectStorage storage) {
        this.comptable = comptable;
        this.permissionGuard = permissionGuard;
        this.accessLogger = accessLogger;
        this.officePreview = officePreview;
        this.storage = storage;
    }

    @GetMapping("/dossiers/{dossierId}/comptable")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    public DossierComptableView comptableView(@PathVariable UUID dossierId) {
        return comptable.view(dossierId);
    }

    @GetMapping("/dossiers/{dossierId}/comptable/documents")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    public List<ComptableDocumentSummary> comptableList(@PathVariable UUID dossierId,
                                                         @RequestParam short annee,
                                                         @RequestParam String categorie) {
        return comptable.list(dossierId, annee, categorie);
    }

    @PostMapping(value = "/dossiers/{dossierId}/comptable/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_CLIENT')")
    public ComptableDocumentSummary uploadComptable(@AuthenticationPrincipal AuthenticatedUser user,
                                                      @PathVariable UUID dossierId,
                                                      @RequestParam("file") MultipartFile file,
                                                      @RequestParam short annee,
                                                      @RequestParam String categorie,
                                                      @RequestParam(required = false) String title) {
        // Depot client : autorise seulement si perm_depot (defense en profondeur).
        permissionGuard.assertCanDepot(dossierId, user);
        return comptable.upload(dossierId, annee, categorie, title, file, user.userId());
    }

    @PostMapping(value = "/dossiers/{dossierId}/comptable/upload-batch",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_CLIENT')")
    public List<ComptableDocumentSummary> uploadComptableBatch(@AuthenticationPrincipal AuthenticatedUser user,
                                                                 @PathVariable UUID dossierId,
                                                                 @RequestParam("files") List<MultipartFile> files,
                                                                 @RequestParam short annee,
                                                                 @RequestParam String categorie,
                                                                 @RequestParam(required = false) String title) {
        permissionGuard.assertCanDepot(dossierId, user);
        return comptable.uploadBatch(dossierId, annee, categorie, title, files, user.userId());
    }

    // 2026-06-30 : la SUPPRESSION est interdite au CLIENT (faille corrigee) -> EMPLOYE seul.
    @DeleteMapping("/comptable/documents/{documentId}")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    public ResponseEntity<Void> deleteComptable(@PathVariable UUID documentId) {
        comptable.softDelete(documentId);
        return ResponseEntity.noContent().build();
    }

    /** RG-DC26 : ZIP de l'annee complete. */
    @GetMapping(value = "/dossiers/{dossierId}/comptable/{annee}/export-zip",
            produces = "application/zip")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    public ResponseEntity<byte[]> exportComptableYearZip(@AuthenticationPrincipal AuthenticatedUser user,
                                                           @PathVariable UUID dossierId,
                                                           @PathVariable short annee) {
        permissionGuard.assertCanDownload(dossierId, user);
        byte[] zip = comptable.exportYearAsZip(dossierId, annee);
        String filename = "comptable_" + dossierId + "_" + annee + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(zip.length))
                .body(zip);
    }

    /**
     * Lot AA (2026-07-05) -- Apercu INLINE d'un document comptable, aligne sur le
     * preview juridique. Consultation : NON gate par {@code perm_download} (seul le
     * download l'est). Bloque seulement si dossier SUSPENDED (CLIENT). Trace
     * PREVIEW_DOC pour l'Activite client (complete le Lot X).
     */
    @GetMapping("/comptable/documents/{documentId}/preview")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    @Operation(summary = "Apercu inline d'un document comptable (Content-Disposition: inline, non gate perm_download)")
    public ResponseEntity<InputStreamResource> previewComptable(@AuthenticationPrincipal AuthenticatedUser user,
                                                                @PathVariable UUID documentId) {
        ComptableDocumentEntity doc = comptable.loadForDownload(documentId);
        permissionGuard.assertCanPreview(doc.getDossierId(), user);
        // Lot AB — Word/Excel converti en PDF a la volee (fallback original sinon).
        OfficePreviewSupport.Rendered rd =
                officePreview.render(doc.getObjectKey(), doc.getFilename(), null);
        if (user != null) {
            accessLogger.log(doc.getDossierId(), documentId, "PREVIEW_DOC", user);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + rd.filename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, rd.contentType())
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(rd.size()))
                .body(new InputStreamResource(rd.stream()));
    }

    @GetMapping("/comptable/documents/{documentId}/download")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    public ResponseEntity<InputStreamResource> downloadComptable(@AuthenticationPrincipal AuthenticatedUser user,
                                                                 @PathVariable UUID documentId) {
        ComptableDocumentEntity doc = comptable.loadForDownload(documentId);
        permissionGuard.assertCanDownload(doc.getDossierId(), user);
        var r = storage.download(doc.getObjectKey());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + doc.getFilename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE,
                        r.contentType() != null ? r.contentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(r.size()))
                .body(new InputStreamResource(r.stream()));
    }
}
