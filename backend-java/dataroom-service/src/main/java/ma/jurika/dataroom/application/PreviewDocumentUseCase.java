package ma.jurika.dataroom.application;

import ma.jurika.common.audit.Auditable;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.dataroom.application.access.ClientAccessLogger;
import ma.jurika.dataroom.domain.port.PdfWatermarkService;
import ma.jurika.dataroom.infrastructure.persistence.DocumentEntity;
import ma.jurika.dataroom.infrastructure.persistence.DocumentJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.SettingsEntity;
import ma.jurika.dataroom.infrastructure.persistence.SettingsJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Sprint 7 / TASK 3 -- Use case "Apercu PDF inline" sur un document juridique.
 *
 * Couvre :
 *   - RG-DR03 : refus si dossier SUSPENDED (CLIENT bloque, EMPLOYE/+ pas bloques)
 *   - Audit : trace DOCUMENT_PREVIEWED (RG-SAAS-02)
 *   - Watermark optionnel via Decorator PdfWatermarkService (TASK 3.2)
 *
 * Note : la verif RBAC initiale (qui peut appeler) est faite par @PreAuthorize
 * du controller. Ici on rejoue uniquement les regles metier (SUSPENDED + role
 * pour watermark). RLS PostgreSQL filtre les rangees hors workspace.
 *
 * Le contrat de l'API renvoie un PreviewPayload qui transporte aussi la
 * raison sociale du dossier (utilisee pour le texte watermark) -- evite un
 * second roundtrip DB cote controller.
 */
@Service
public class PreviewDocumentUseCase {

    public record PreviewPayload(DocumentEntity document, InputStream stream,
                                  String contentType, long size, String filename) {}

    private final DocumentJpaRepository documents;
    private final DossierViewJpaRepository dossiers;
    private final SettingsJpaRepository settings;
    private final OfficePreviewSupport office;
    private final PdfWatermarkService watermark;
    private final ClientAccessLogger accessLogger;
    private final boolean watermarkEnabled;

    public PreviewDocumentUseCase(DocumentJpaRepository documents,
                                   DossierViewJpaRepository dossiers,
                                   SettingsJpaRepository settings,
                                   OfficePreviewSupport office,
                                   PdfWatermarkService watermark,
                                   ClientAccessLogger accessLogger,
                                   @Value("${jurika.dataroom.watermark.enabled:false}") boolean watermarkEnabled) {
        this.documents = documents;
        this.dossiers = dossiers;
        this.settings = settings;
        this.office = office;
        this.watermark = watermark;
        this.accessLogger = accessLogger;
        this.watermarkEnabled = watermarkEnabled;
    }

    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT;

    @Transactional(readOnly = true)
    @Auditable(action = "DOCUMENT_PREVIEWED", resourceType = "document", resourceIdExpr = "#documentId")
    public PreviewPayload execute(UUID documentId, AuthenticatedUser user) {
        DocumentEntity doc = documents.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document inconnu : " + documentId));

        // RG-DR03 : si CLIENT et dossier SUSPENDED -> 403 (ValidationException → 400 du
        // GlobalExceptionHandler. On garde le message explicite pour le frontend).
        // EMPLOYE / SUPERVISEUR / SUPER_ADMIN ne sont pas bloques.
        if (user != null && user.role() == Role.CLIENT) {
            SettingsEntity s = settings.findById(doc.getDossierId()).orElse(null);
            if (s != null && "SUSPENDED".equals(s.getAccessStatus())) {
                throw new ValidationException("Data Room suspendu : apercu indisponible");
            }
        }

        // Lot AB (2026-07-05) — Word/Excel -> PDF a la volee (via ai-service /
        // LibreOffice + cache MinIO) pour l'apercu inline ; PDF/images inchanges ;
        // fallback original si conversion indisponible. Se fait APRES le controle
        // d'acces (SUSPENDED ci-dessus), sur les memes octets deja autorises.
        OfficePreviewSupport.Rendered rd =
                office.render(doc.getObjectKey(), doc.getFilename(), doc.getContentType());
        InputStream stream = rd.stream();
        String contentType = rd.contentType();

        // Watermark optionnel -- Decorator pattern, applique uniquement si :
        //  (1) feature flag ON, (2) impl reellement enabled, (3) role CLIENT,
        //  (4) le flux est bien un PDF (post-conversion inclus).
        boolean shouldWatermark = watermarkEnabled
                && watermark.isEnabled()
                && user != null
                && user.role() == Role.CLIENT
                && "application/pdf".equalsIgnoreCase(contentType);
        if (shouldWatermark) {
            String text = buildWatermarkText(doc, user);
            stream = watermark.watermark(stream, text, documentId);
        }

        // Sprint 7 / TASK 5 -- trace l'acces client pour le drawer "Activite"
        if (user != null) {
            accessLogger.log(doc.getDossierId(), documentId, "PREVIEW_DOC", user);
        }

        return new PreviewPayload(doc, stream, contentType, rd.size(), rd.filename());
    }

    private String buildWatermarkText(DocumentEntity doc, AuthenticatedUser user) {
        DossierViewEntity d = dossiers.findById(doc.getDossierId()).orElse(null);
        String raisonSociale = d != null ? d.getRaisonSociale() : "Dossier";
        return "JURIKA -- " + raisonSociale
                + " -- " + user.email()
                + " -- " + TS.format(Instant.now());
    }
}
