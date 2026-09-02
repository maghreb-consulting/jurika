package ma.jurika.dataroom.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.dataroom.api.dto.DataroomDtos.DossierFiscalDetailedView;
import ma.jurika.dataroom.api.dto.DataroomDtos.DossierFiscalView;
import ma.jurika.dataroom.api.dto.DataroomDtos.FiscalDocumentSummary;
import ma.jurika.dataroom.api.dto.DataroomDtos.SubClassificationDef;
import ma.jurika.dataroom.application.ClientDataroomPermissionGuard;
import ma.jurika.dataroom.application.DataroomFiscalService;
import ma.jurika.dataroom.application.OfficePreviewSupport;
import ma.jurika.dataroom.application.access.ClientAccessLogger;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import ma.jurika.dataroom.infrastructure.persistence.FiscalDocumentEntity;
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
 * Sprint 8 -- Controller Dossier Fiscal complet.
 * 7 categories CGI (TVA, IS, IR, TP_TSC, RAS, ATTESTATIONS, CONTENTIEUX),
 * sous-classifications, retention 10 ans (CGI Art. 211), notifications
 * RG-DF21 + RG-DF24.
 */
@RestController
@RequestMapping("/api/v1/dataroom")
@Tag(name = "Dataroom Fiscal", description = "Dossier Fiscal V2.1 (Sprint 8) -- 7 categories CGI, sous-classifications, retention 10 ans CGI Art. 211")
public class FiscalController {

    private final DataroomFiscalService fiscal;
    private final ClientDataroomPermissionGuard permissionGuard;
    private final ClientAccessLogger accessLogger;
    private final OfficePreviewSupport officePreview;
    private final ObjectStorage storage;

    public FiscalController(DataroomFiscalService fiscal,
                            ClientDataroomPermissionGuard permissionGuard,
                            ClientAccessLogger accessLogger,
                            OfficePreviewSupport officePreview,
                            ObjectStorage storage) {
        this.fiscal = fiscal;
        this.permissionGuard = permissionGuard;
        this.accessLogger = accessLogger;
        this.officePreview = officePreview;
        this.storage = storage;
    }

    @GetMapping("/dossiers/{dossierId}/fiscal")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    @Operation(summary = "Vue compatibilite Sprint 7 (liste exercices + 7 categories CGI annoncees)")
    public DossierFiscalView fiscalView(@PathVariable UUID dossierId,
                                         @RequestParam(required = false) UUID exerciceId) {
        return fiscal.view(dossierId, exerciceId);
    }

    @GetMapping("/dossiers/{dossierId}/fiscal/detail")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    @Operation(summary = "Vue detaillee : liste exercices + compteurs par categorie pour l'exercice courant")
    public DossierFiscalDetailedView fiscalDetail(@PathVariable UUID dossierId,
                                                   @RequestParam(required = false) UUID exerciceId) {
        return fiscal.detailedView(dossierId, exerciceId);
    }

    @GetMapping("/dossiers/{dossierId}/fiscal/documents")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    @Operation(summary = "Liste documents d'un exercice (filtre categorie optionnel)")
    public List<FiscalDocumentSummary> list(@PathVariable UUID dossierId,
                                              @RequestParam UUID exerciceId,
                                              @RequestParam(required = false) String categorie) {
        return fiscal.list(dossierId, exerciceId, categorie);
    }

    @GetMapping("/fiscal/sub-classifications")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    @Operation(summary = "Constantes sous-classifications (RG-DF16) -- toutes ou filtre categorie")
    public List<SubClassificationDef> subClassifications(@RequestParam(required = false) String categorie) {
        return fiscal.getSubClassifications(categorie);
    }

    // 2026-06-30 : Dossier Fiscal en LECTURE SEULE pour le CLIENT (list + download).
    // L'upload est reserve a l'EMPLOYE (le client ne depose jamais de fiscal).
    @PostMapping(value = "/dossiers/{dossierId}/fiscal/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    @Operation(summary = "Upload document fiscal (EMPLOYE uniquement)",
            description = "RG-DF01..23. Commentaire >= 20 chars sur CONTENTIEUX (RG-DF23). Max 15 Mo (RG-DF08). CLIENT en lecture seule.")
    @ApiResponse(responseCode = "403", description = "Role non autorise (CLIENT en lecture seule)")
    @ApiResponse(responseCode = "409", description = "Exercice VERROUILLE/CLOTURE non compatible (RG-DF25)")
    @ApiResponse(responseCode = "422", description = "Categorie/sous-classification invalide ou taille fichier")
    public FiscalDocumentSummary upload(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID dossierId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) UUID exerciceId,
            @RequestParam(required = false) Short annee,
            @RequestParam String categorie,
            @RequestParam String sousClassification,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String commentaire,
            @RequestParam(required = false) String tifMetadata,
            @RequestParam(required = false) String numeroDeclaration,
            @RequestParam(required = false) String periodeDeclaree,
            @RequestParam(required = false) UUID comptableDocSource) {
        boolean isClient = user.role() == Role.CLIENT;
        // Prompt H (2026-06-23) — soit exerciceId explicite (back-compat), soit
        // `annee` brute (workflow IMPORT) : exerciceId prime quand les deux sont
        // fournis. Si aucun n'est présent → 400.
        if (exerciceId != null) {
            return fiscal.upload(dossierId, exerciceId, categorie, sousClassification,
                    title, commentaire, tifMetadata, numeroDeclaration, periodeDeclaree,
                    comptableDocSource, file, user.userId(), isClient);
        }
        if (annee != null) {
            return fiscal.uploadByAnnee(dossierId, annee, categorie, sousClassification,
                    title, commentaire, tifMetadata, numeroDeclaration, periodeDeclaree,
                    comptableDocSource, file, user.userId(), isClient);
        }
        throw new ma.jurika.common.exception.ValidationException(
                "Au moins l'un des paramètres `exerciceId` ou `annee` est requis.");
    }

    // 2026-06-30 : suppression fiscale interdite au CLIENT -> EMPLOYE seul.
    @DeleteMapping("/fiscal/documents/{documentId}")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    @Operation(summary = "Soft delete document fiscal (CGI Art. 211 -- retention 10 ans : fichier MinIO conserve)")
    @ApiResponse(responseCode = "409", description = "CONTENTIEUX non supprimable tant qu'exercice non CLOTURE (RG-DF12)")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,
                                         @PathVariable UUID documentId) {
        fiscal.softDelete(documentId, user.userId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Lot AA (2026-07-05) -- Apercu INLINE d'un document fiscal, aligne sur le
     * preview juridique. Consultation : NON gate par {@code perm_download}. Bloque
     * seulement si dossier SUSPENDED (CLIENT). Trace PREVIEW_DOC (Activite client).
     */
    @GetMapping("/fiscal/documents/{documentId}/preview")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    @Operation(summary = "Apercu inline d'un document fiscal (Content-Disposition: inline, non gate perm_download)")
    public ResponseEntity<InputStreamResource> preview(@AuthenticationPrincipal AuthenticatedUser user,
                                                       @PathVariable UUID documentId) {
        FiscalDocumentEntity doc = fiscal.loadForDownload(documentId);
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

    @GetMapping("/fiscal/documents/{documentId}/download")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    public ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal AuthenticatedUser user,
                                                        @PathVariable UUID documentId) {
        FiscalDocumentEntity doc = fiscal.loadForDownload(documentId);
        // CLIENT autorise a lire le fiscal, mais selon perm_download.
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

    @GetMapping(value = "/dossiers/{dossierId}/fiscal/{exerciceId}/export-zip",
            produces = "application/zip")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    @Operation(summary = "Export ZIP exercice complet (RG-DF19)")
    public ResponseEntity<byte[]> exportZip(@AuthenticationPrincipal AuthenticatedUser user,
                                              @PathVariable UUID dossierId,
                                              @PathVariable UUID exerciceId) {
        permissionGuard.assertCanDownload(dossierId, user);
        byte[] zip = fiscal.exportExerciceAsZip(dossierId, exerciceId);
        String filename = "fiscal_" + dossierId + "_" + exerciceId + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(zip.length))
                .body(zip);
    }
}
