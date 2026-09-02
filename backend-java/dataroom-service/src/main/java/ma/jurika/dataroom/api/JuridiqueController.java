package ma.jurika.dataroom.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.dataroom.api.dto.DataroomDtos.BulkDeleteRequest;
import ma.jurika.dataroom.api.dto.DataroomDtos.BulkExportZipRequest;
import ma.jurika.dataroom.api.dto.DataroomDtos.DocumentSummary;
import ma.jurika.dataroom.api.dto.DataroomDtos.DossierJuridiqueView;
import ma.jurika.dataroom.api.dto.DataroomDtos.SearchJuridiqueInput;
import ma.jurika.dataroom.api.dto.DataroomDtos.SearchJuridiqueOutput;
import ma.jurika.dataroom.api.dto.DataroomDtos.VersionScope;
import ma.jurika.dataroom.application.OfficePreviewSupport;
import ma.jurika.dataroom.application.ClientDataroomPermissionGuard;
import ma.jurika.dataroom.application.DataroomJuridiqueService;
import ma.jurika.dataroom.application.FicheClientService;
import ma.jurika.dataroom.application.PreviewDocumentUseCase;
import ma.jurika.dataroom.application.SearchJuridiqueDocumentsUseCase;
import ma.jurika.dataroom.application.access.ClientAccessLogger;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import ma.jurika.dataroom.infrastructure.persistence.DocumentEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.List;
import java.util.UUID;

/**
 * Sprint 7 / TASK 6.2 -- Refactor : controller dedie au Dossier Juridique.
 *
 * Couvre :
 *   - view (avec filtres timeline TASK 2)
 *   - upload single + batch (TASK 6.2 audit A5 : upload-batch preserve)
 *   - search FTS (TASK 1)
 *   - preview inline (TASK 3)
 *   - download single + bulk delete + bulk ZIP + export PDF (TASK 4)
 *
 * Toutes les URLs sont strictement identiques a l'ancien DataroomController.
 */
@RestController
@RequestMapping("/api/v1/dataroom")
@Tag(name = "Dataroom Juridique", description = "Documents juridiques en vigueur + historique des operations cloturees")
public class JuridiqueController {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(JuridiqueController.class);

    private final DataroomJuridiqueService juridique;
    private final FicheClientService ficheClient;
    private final SearchJuridiqueDocumentsUseCase searchJuridique;
    private final PreviewDocumentUseCase previewDocument;
    private final ClientAccessLogger accessLogger;
    private final ClientDataroomPermissionGuard permissionGuard;
    private final ObjectStorage storage;
    /** Fix DR4 — conversion Office → PDF pour l'aperçu des versions (.docx directeur). */
    private final OfficePreviewSupport officePreview;

    public JuridiqueController(DataroomJuridiqueService juridique,
                                FicheClientService ficheClient,
                                SearchJuridiqueDocumentsUseCase searchJuridique,
                                PreviewDocumentUseCase previewDocument,
                                ClientAccessLogger accessLogger,
                                ClientDataroomPermissionGuard permissionGuard,
                                ObjectStorage storage,
                                OfficePreviewSupport officePreview) {
        this.juridique = juridique;
        this.ficheClient = ficheClient;
        this.searchJuridique = searchJuridique;
        this.previewDocument = previewDocument;
        this.accessLogger = accessLogger;
        this.permissionGuard = permissionGuard;
        this.storage = storage;
        this.officePreview = officePreview;
    }

    // ============================================================
    // View + Search + Filters (TASK 1, TASK 2)
    // ============================================================

    @GetMapping("/dossiers/{dossierId}/juridique")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    @Operation(summary = "Vue du Dossier Juridique (en vigueur + historique tickets) avec filtres timeline optionnels")
    @ApiResponse(responseCode = "200", description = "Vue avec documentsEnVigueur + historiqueOperations")
    @ApiResponse(responseCode = "404", description = "Dossier inconnu ou hors workspace")
    public DossierJuridiqueView juridiqueView(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID dossierId,
            @RequestParam(required = false) List<String> types,
            @RequestParam(required = false) java.time.Instant from,
            @RequestParam(required = false) java.time.Instant to) {
        // 2026-06-04 (fix P1) : un CLIENT n'a acces qu'A SON dossier. Avant ce
        // fix, n'importe quel CLIENT pouvait deviner un UUID dossier et lire
        // ses documents (RLS workspace seul ne suffit pas, cf RG-DR15).
        if (user != null && user.role() == ma.jurika.common.security.Role.CLIENT) {
            juridique.assertClientAccess(dossierId, user.userId());
        }
        DossierJuridiqueView view = (types == null && from == null && to == null)
                ? juridique.view(dossierId)
                : juridique.view(dossierId, types, from, to);
        if (user != null) {
            accessLogger.log(dossierId, null, "VIEW_DOSSIER", user);
        }
        return view;
    }

    @GetMapping("/dossiers/{dossierId}/juridique/search")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    @Operation(summary = "Recherche FTS PostgreSQL (titre + filename) avec filtres types/dates/scope versions",
            description = "RG-DR-FTS : websearch_to_tsquery('french') sur la colonne tsvector GENERATED de V10")
    public SearchJuridiqueOutput searchJuridique(
            @PathVariable UUID dossierId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> types,
            @RequestParam(required = false) java.time.Instant from,
            @RequestParam(required = false) java.time.Instant to,
            @RequestParam(required = false, defaultValue = "CURRENT") VersionScope versionScope,
            @RequestParam(required = false, defaultValue = "20") int limit,
            @RequestParam(required = false, defaultValue = "0") int offset) {
        SearchJuridiqueInput in = new SearchJuridiqueInput(
                dossierId, q, types, from, to, versionScope, limit, offset);
        return searchJuridique.execute(in);
    }

    // ============================================================
    // Upload single + batch (preserve l'API audit A5)
    // ============================================================

    @PostMapping(value = "/dossiers/{dossierId}/juridique/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE')")
    public DocumentSummary uploadJuridique(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable UUID dossierId,
                                            MultipartHttpServletRequest req) {
        log.info("uploadJuridique dossier={} user={} role={} contentType={} fileMapKeys={} paramNames={}",
                dossierId, user == null ? "null" : user.userId(),
                user == null ? "null" : user.role(),
                req.getContentType(),
                req.getFileMap().keySet(),
                java.util.Collections.list(req.getParameterNames()));

        MultipartFile file = req.getFile("file");
        if (file == null && !req.getFileMap().isEmpty()) {
            file = req.getFileMap().values().iterator().next();
            log.warn("Le part 'file' n'a pas ete trouve par son nom -> on prend le 1er part disponible : {}",
                    file.getOriginalFilename());
        }
        if (file == null || file.isEmpty()) {
            log.error("uploadJuridique : aucun fichier. parts trouves = {}", req.getFileMap().keySet());
            throw new ma.jurika.common.exception.ValidationException(
                    "FILE_MISSING : aucun fichier dans la requete (parts: " + req.getFileMap().keySet() + ")");
        }
        log.info("uploadJuridique file=[name={}, size={}, contentType={}]",
                file.getOriginalFilename(), file.getSize(), file.getContentType());

        String documentType = req.getParameter("documentType");
        String title = req.getParameter("title");
        String ticketIdStr = req.getParameter("ticketId");
        UUID ticketId = (ticketIdStr == null || ticketIdStr.isBlank()) ? null : UUID.fromString(ticketIdStr);

        String type = (documentType == null || documentType.isBlank()) ? "AUTRE" : documentType;
        String t = (title == null || title.isBlank()) ? defaultTitle(file) : title;
        try {
            DocumentSummary result = juridique.uploadVersion(dossierId, type, t, ticketId, file, user.userId());
            log.info("uploadJuridique SUCCESS docId={} version={} key={}",
                    result.id(), result.version(), result.filename());
            return result;
        } catch (Exception ex) {
            log.error("uploadJuridique CRASH dossier={} type={} title={} file={} : {} - {}",
                    dossierId, type, t, file.getOriginalFilename(),
                    ex.getClass().getSimpleName(), ex.getMessage(), ex);
            throw ex;
        }
    }

    @PostMapping(value = "/dossiers/{dossierId}/juridique/upload-batch",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE')")
    public List<DocumentSummary> uploadJuridiqueBatch(@AuthenticationPrincipal AuthenticatedUser user,
                                                       @PathVariable UUID dossierId,
                                                       @RequestParam("files") List<MultipartFile> files,
                                                       @RequestParam(value = "documentType", required = false) String documentType,
                                                       @RequestParam(value = "title", required = false) String title,
                                                       @RequestParam(value = "ticketId", required = false) UUID ticketId) {
        String type = (documentType == null || documentType.isBlank()) ? "AUTRE" : documentType;
        String t = (title == null || title.isBlank())
                ? files.size() + " documents - " + java.time.LocalDate.now()
                : title;
        return juridique.uploadVersionBatch(dossierId, type, t, ticketId, files, user.userId());
    }

    private static String defaultTitle(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) return "Document";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // ============================================================
    // Delete single + bulk (TASK 4)
    // ============================================================

    @DeleteMapping("/documents/{documentId}")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    public ResponseEntity<Void> deleteJuridique(@PathVariable UUID documentId) {
        juridique.delete(documentId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/juridique/documents")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    @Operation(summary = "Suppression bulk (transaction unique, rollback complet si erreur)")
    public ResponseEntity<Void> deleteJuridiqueBulk(@Valid @RequestBody BulkDeleteRequest req) {
        juridique.deleteBulk(req.documentIds());
        return ResponseEntity.noContent().build();
    }

    // ============================================================
    // Export ZIP + PDF historique (TASK 4)
    // ============================================================

    @PostMapping(value = "/dossiers/{dossierId}/juridique/export-zip",
            produces = "application/zip")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    public ResponseEntity<byte[]> exportJuridiqueZip(@AuthenticationPrincipal AuthenticatedUser user,
                                                       @PathVariable UUID dossierId,
                                                       @Valid @RequestBody BulkExportZipRequest req) {
        // Export ZIP = telechargement groupe -> meme garde perm_download pour le CLIENT.
        permissionGuard.assertCanDownload(dossierId, user);
        byte[] zip = juridique.exportSelectionAsZip(
                dossierId, req.documentIds(), req.includeOldVersions());
        String filename = "juridique_" + dossierId + "_" + java.time.LocalDate.now() + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(zip.length))
                .body(zip);
    }

    /**
     * Fiche client (2026-07-14) — remplace l'ancien « Exporter rapport PDF ».
     * Carte d'identite juridique de la societe (4 sections). Reserve EMPLOYE
     * (responsable du dossier, verifie en service) / SUPERVISEUR / SUPER_ADMIN.
     * Le CLIENT est exclu (ni ROLE_CLIENT dans @PreAuthorize, ni acces service).
     */
    @GetMapping(value = "/dossiers/{dossierId}/juridique/fiche-client-pdf",
            produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE')")
    @Operation(summary = "Fiche client PDF (identifiants + operations + documents en vigueur + historique)",
            description = "Carte d'identite juridique a la charte JURIKA. CLIENT interdit.")
    @ApiResponse(responseCode = "200", description = "PDF de la Fiche client")
    @ApiResponse(responseCode = "403", description = "Employe non responsable du dossier")
    @ApiResponse(responseCode = "404", description = "Dossier inconnu ou hors workspace")
    public ResponseEntity<byte[]> ficheClientPdf(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @PathVariable UUID dossierId) {
        var view = ficheClient.assemble(dossierId, user.role(), user.userId());
        byte[] pdf = ficheClient.renderPdf(view);
        String safe = view.identity().raisonSociale() == null ? "dossier"
                : view.identity().raisonSociale().replaceAll("[^a-zA-Z0-9_-]", "_");
        if (safe.length() > 40) safe = safe.substring(0, 40);
        String filename = "Fiche_Client_" + safe + "_" + java.time.LocalDate.now() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(pdf.length))
                .body(pdf);
    }

    // ============================================================
    // Preview + Download single (TASK 3, TASK 5 access log)
    // ============================================================

    @GetMapping("/documents/{documentId}/preview")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    @Operation(summary = "Apercu PDF inline (Content-Disposition: inline)",
            description = "RG-DR03 : 400 si dossier SUSPENDED et role CLIENT. Watermark optionnel via jurika.dataroom.watermark.enabled.")
    @ApiResponse(responseCode = "200", description = "Stream PDF inline")
    @ApiResponse(responseCode = "400", description = "Dossier suspendu (CLIENT)")
    @ApiResponse(responseCode = "404", description = "Document inconnu")
    public ResponseEntity<InputStreamResource> previewJuridique(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID documentId) {
        PreviewDocumentUseCase.PreviewPayload p = previewDocument.execute(documentId, user);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + p.filename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, p.contentType())
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(p.size()))
                .body(new InputStreamResource(p.stream()));
    }

    @GetMapping("/documents/{documentId}/download")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    public ResponseEntity<InputStreamResource> downloadJuridique(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID documentId) {
        DocumentEntity doc = juridique.loadForDownload(documentId);
        // Defense en profondeur : un CLIENT sans perm_download -> 403.
        permissionGuard.assertCanDownload(doc.getDossierId(), user);
        var r = storage.download(doc.getObjectKey());
        if (user != null) {
            accessLogger.log(doc.getDossierId(), documentId, "DOWNLOAD_DOC", user);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + doc.getFilename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE,
                        r.contentType() != null ? r.contentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(r.size()))
                .body(new InputStreamResource(r.stream()));
    }

    /**
     * Lot X -- Journalise une impression client. L'impression se declenche cote
     * navigateur ({@code window.print()} sur l'iframe d'apercu) : aucun appel
     * serveur ne la trace naturellement. Le front l'appelle en fire-and-forget
     * apres avoir lance l'impression. Le {@code dossierId} est resolu
     * server-side depuis le document (on ne fait pas confiance a un id fourni
     * par le client). Best-effort de bout en bout : renvoie 204 meme si le
     * document est introuvable, pour ne jamais bloquer l'UX d'impression.
     */
    @PostMapping("/documents/{documentId}/print-log")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    @Operation(summary = "Journalise une impression client (PRINT_DOC) -- best-effort",
            description = "Appele par le front apres window.print(). Resout le dossier depuis le "
                    + "document et trace l'action dans l'Activite client. Toujours 204.")
    @ApiResponse(responseCode = "204", description = "Impression journalisee (ou ignoree si best-effort echoue)")
    public ResponseEntity<Void> logPrint(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID documentId) {
        try {
            DocumentEntity doc = juridique.loadForDownload(documentId);
            if (user != null) {
                accessLogger.log(doc.getDossierId(), documentId, "PRINT_DOC", user);
            }
        } catch (Exception ex) {
            // Best-effort : document non juridique / introuvable, etc. On avale.
            log.debug("logPrint : trace ignoree pour document={} : {}", documentId, ex.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    // ============================================================
    // Versioning explicite (Sprint 2026-06-23)
    //   POST /documents/{id}/versions          → replace as new version
    //   GET  /documents/{id}/versions          → list lineage (active + history)
    //   POST /documents/{id}/versions/{vid}/restore  → promote old version
    //   GET  /documents/{id}/versions/{vid}/download → download specific version
    // ============================================================

    @PostMapping(value = "/documents/{documentId}/versions",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    @Operation(summary = "Remplacer comme NOUVELLE VERSION du même Document logique",
            description = "L'ancienne version bascule en historique (is_current=false, replaced_at=NOW, motif persisté). La nouvelle devient ACTIVE.")
    @ApiResponse(responseCode = "200", description = "Nouvelle version active créée")
    @ApiResponse(responseCode = "404", description = "Document inconnu")
    public DocumentSummary replaceAsNewVersion(@AuthenticationPrincipal AuthenticatedUser user,
                                                @PathVariable UUID documentId,
                                                MultipartHttpServletRequest req) {
        MultipartFile file = req.getFile("file");
        if (file == null && !req.getFileMap().isEmpty()) {
            file = req.getFileMap().values().iterator().next();
        }
        if (file == null || file.isEmpty()) {
            throw new ma.jurika.common.exception.ValidationException(
                    "FILE_MISSING : aucun fichier dans la requete");
        }
        String motif = req.getParameter("motif");
        return juridique.replaceAsNewVersion(documentId, file, motif, user.userId());
    }

    @GetMapping("/documents/{documentId}/versions")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    @Operation(summary = "Lignage complet d'un Document logique (active + historique)",
            description = "Trié de la version la plus récente à la plus ancienne. Toutes les versions partagent (dossier+type+title).")
    public List<DocumentSummary> listVersions(@PathVariable UUID documentId) {
        return juridique.listVersions(documentId);
    }

    @PostMapping("/documents/{documentId}/versions/{versionId}/restore")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    @Operation(summary = "Restaurer une ancienne version comme version active",
            description = "Pas d'INSERT — swap des flags is_current sur 2 lignes. La version actuelle bascule en historique avec un motif explicite.")
    @ApiResponse(responseCode = "200", description = "Version restaurée")
    @ApiResponse(responseCode = "404", description = "Document ou version inconnu(e)")
    public DocumentSummary restoreVersion(@PathVariable UUID documentId,
                                           @PathVariable UUID versionId,
                                           @RequestBody(required = false) RestoreVersionRequest body) {
        String motif = body == null ? null : body.motif();
        return juridique.restoreVersion(documentId, versionId, motif);
    }

    @GetMapping("/documents/{documentId}/versions/{versionId}/download")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    @Operation(summary = "Télécharger une version spécifique (active ou historique) d'un Document logique")
    @ApiResponse(responseCode = "404", description = "Version inconnue ou hors slot")
    public ResponseEntity<InputStreamResource> downloadVersion(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID documentId,
            @PathVariable UUID versionId) {
        DocumentEntity doc = juridique.loadVersionForDownload(documentId, versionId);
        permissionGuard.assertCanDownload(doc.getDossierId(), user);
        var r = storage.download(doc.getObjectKey());
        if (user != null) {
            accessLogger.log(doc.getDossierId(), versionId, "DOWNLOAD_VERSION", user);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + doc.getFilename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE,
                        r.contentType() != null ? r.contentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(r.size()))
                .body(new InputStreamResource(r.stream()));
    }

    /**
     * Fix DR4 (2026-08-16) — APERÇU DE N'IMPORTE QUELLE VERSION.
     *
     * <p>Deux limites se cumulaient : l'aperçu n'existait que pour la version
     * COURANTE, et il ne rendait que du PDF. Or les actes produits par la voie
     * directeur sont des <b>.docx</b>, et les versions historiques n'avaient qu'un
     * bouton « Télécharger ». Conséquence : relire un statut remplacé — le geste
     * même du contrôle juridique — imposait de télécharger le fichier et de
     * l'ouvrir dans Word.
     *
     * <p>Cet endpoint sert donc l'aperçu <i>inline</i> d'une version quelconque
     * (courante ou historique), en convertissant les formats Office en PDF à la
     * volée via {@link OfficePreviewSupport} — le même composant qui rend déjà les
     * dépôts. Contrôle d'accès identique au téléchargement de version, et accès
     * tracé comme partout ailleurs (RG-DR03).
     */
    @GetMapping("/documents/{documentId}/versions/{versionId}/preview")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    @Operation(summary = "Apercu inline d'une version (active ou historique), Office converti en PDF")
    @ApiResponse(responseCode = "200", description = "Stream inline (PDF ou original)")
    @ApiResponse(responseCode = "404", description = "Version inconnue ou hors slot")
    public ResponseEntity<InputStreamResource> previewVersion(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID documentId,
            @PathVariable UUID versionId) {
        DocumentEntity doc = juridique.loadVersionForDownload(documentId, versionId);
        permissionGuard.assertCanDownload(doc.getDossierId(), user);
        OfficePreviewSupport.Rendered rd =
                officePreview.render(doc.getObjectKey(), doc.getFilename(), doc.getContentType());
        if (user != null) {
            accessLogger.log(doc.getDossierId(), versionId, "PREVIEW_VERSION", user);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + rd.filename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, rd.contentType())
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(rd.size()))
                .body(new InputStreamResource(rd.stream()));
    }

    public record RestoreVersionRequest(String motif) {}
}
