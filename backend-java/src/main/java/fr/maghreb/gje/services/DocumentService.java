package fr.maghreb.gje.services;

import fr.maghreb.gje.config.TenantContext;
import fr.maghreb.gje.dto.document.*;
import fr.maghreb.gje.models.*;
import fr.maghreb.gje.repositories.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DossierRepository dossierRepository;
    private final HistoriqueRepository historiqueRepository;
    private final QuotaService quotaService;
    private final HistoriqueService historiqueService;

    @PersistenceContext
    private EntityManager entityManager;

    private void setTenant(UUID workspaceId) {
        TenantContext.setTenantId(workspaceId.toString());
        entityManager.createNativeQuery(
            "SET LOCAL app.current_tenant_id = '"
            + workspaceId.toString() + "'")
            .executeUpdate();
        entityManager.flush();
    }

    @Transactional
    public List<Document> getByDossier(
            UUID dossierId, UUID workspaceId) {
        setTenant(workspaceId);
        return documentRepository
            .findByDossierIdOrderByVersionNumberDesc(
                dossierId);
    }

    @Transactional
    public Document upload(
            DocumentUploadRequest request,
            UUID userId,
            UUID workspaceId) {

        setTenant(workspaceId);

        UUID dossierId = UUID.fromString(
            request.getDossierId());

        // Verify dossier belongs to workspace
        dossierRepository.findById(dossierId)
            .filter(d -> d.getWorkspaceId()
                .equals(workspaceId))
            .orElseThrow(() ->
                new RuntimeException(
                    "Dossier introuvable"));

        // Get next version
        Integer maxVersion = documentRepository
            .findMaxVersionByDossierId(dossierId);
        int nextVersion = maxVersion == null ?
            1 : maxVersion + 1;

        // Mark previous versions as not current
        List<Document> existing = documentRepository
            .findByDossierIdAndIsCurrent(
                dossierId, true);
        existing.forEach(d -> {
            d.setIsCurrent(false);
            documentRepository.save(d);
        });

        // Simulate file size bytes computation (if provided, or a random/mock value for now since request doesn't hold size)
        long fileBytes = request.getFileContent() != null ? 
            (long) request.getFileContent().length() * 3 / 4 : 500_000L; // estimate from Base64 or assume 500KB

        // Verify storage quota
        quotaService.checkStorageQuota(workspaceId, fileBytes);

        // Create new document
        String fileUuid = UUID.randomUUID().toString();
        Document document = Document.builder()
                .workspaceId(workspaceId)
                .dossierId(dossierId)
                .title(request.getTitle())
                .filePathUuid(fileUuid)
                .fileUrl("/files/" + fileUuid)
                .versionNumber(nextVersion)
                .isIaGenerated(false)
                .isCurrent(true)
                .createdBy(userId)
                .fileSizeBytes(fileBytes)
                .editMention("Uploadé manuellement")
                .build();

        document = documentRepository.save(document);

        // Log historique
        historiqueRepository.save(Historique.builder()
            .workspaceId(workspaceId)
            .dossierId(dossierId)
            .userId(userId)
            .action("DOCUMENT_UPLOADE")
            .nouvelleValeur(request.getTitle()
                + " v" + nextVersion)
            .build());

        return document;
    }

    @Transactional
    public Map<String, Object> generateIA(
            DocumentGenerateRequest request,
            UUID userId,
            UUID workspaceId) {

        setTenant(workspaceId);

        UUID dossierId = UUID.fromString(
            request.getDossierId());

        // Get dossier data
        EntrepriseDossier dossier = dossierRepository
            .findById(dossierId)
            .filter(d -> d.getWorkspaceId()
                .equals(workspaceId))
            .orElseThrow(() ->
                new RuntimeException(
                    "Dossier introuvable"));

        // Get next version
        Integer maxVersion = documentRepository
            .findMaxVersionByDossierId(dossierId);
        int nextVersion = maxVersion == null ?
            1 : maxVersion + 1;

        // Simulate AI generation
        // In production: call OpenClaw agent
        String generatedContent = buildLegalDocument(
            dossier, request.getDocumentType());

        long fileBytes = (long) generatedContent.length();

        // Verify storage quota
        quotaService.checkStorageQuota(workspaceId, fileBytes);

        String fileUuid = UUID.randomUUID().toString();
        String title = request.getDocumentType()
            + " — " + dossier.getDenomination()
            + " v" + nextVersion;

        Document document = Document.builder()
                .workspaceId(workspaceId)
                .dossierId(dossierId)
                .title(title)
                .filePathUuid(fileUuid)
                .fileUrl("/files/" + fileUuid)
                .versionNumber(nextVersion)
                .isIaGenerated(true)
                .isCurrent(true)
                .createdBy(userId)
                .fileSizeBytes(fileBytes)
                .editMention(
                    "Généré par IA — Non validé")
                .build();

        document = documentRepository.save(document);

        historiqueRepository.save(Historique.builder()
            .workspaceId(workspaceId)
            .dossierId(dossierId)
            .userId(userId)
            .action("DOCUMENT_GENERE_IA")
            .nouvelleValeur(title)
            .build());

        Map<String, Object> result = new HashMap<>();
        result.put("document", document);
        result.put("generated_content",
            generatedContent);
        result.put("status", "generated");
        result.put("message",
            "Document généré par IA avec succès");
        result.put("requires_validation", true);
        return result;
    }

    @Transactional
    public Document valider(
            UUID id,
            UUID userId,
            UUID workspaceId) {

        setTenant(workspaceId);

        Document document = documentRepository
            .findById(id)
            .orElseThrow(() ->
                new RuntimeException(
                    "Document introuvable"));

        // Verify workspace isolation
        if (!document.getWorkspaceId().equals(workspaceId)) {
            throw new RuntimeException(
                "Document introuvable");
        }

        document.setEditMention(
            "Généré par IA — Validé sans modification");
        document = documentRepository.save(document);

        historiqueRepository.save(Historique.builder()
            .workspaceId(workspaceId)
            .dossierId(document.getDossierId())
            .userId(userId)
            .action("DOCUMENT_VALIDE")
            .nouvelleValeur(document.getTitle())
            .build());

        return document;
    }

    @Transactional
    public Document editer(
            UUID id,
            DocumentEditRequest request,
            UUID userId,
            UUID workspaceId) {

        setTenant(workspaceId);

        Document document = documentRepository
            .findById(id)
            .orElseThrow(() ->
                new RuntimeException(
                    "Document introuvable"));

        // Verify workspace isolation
        if (!document.getWorkspaceId().equals(workspaceId)) {
            throw new RuntimeException(
                "Document introuvable");
        }

        String mention = request.getEditMention() != null
            ? request.getEditMention()
            : "Généré par IA — Édité par utilisateur";

        document.setEditMention(mention);
        document = documentRepository.save(document);

        historiqueRepository.save(Historique.builder()
            .workspaceId(workspaceId)
            .dossierId(document.getDossierId())
            .userId(userId)
            .action("DOCUMENT_EDITE")
            .nouvelleValeur(mention)
            .build());

        return document;
    }

    @Transactional(readOnly = true)
    public Document download(
            UUID id,
            User user,
            String ipAddress) {

        setTenant(user.getWorkspaceId());

        Document document = documentRepository
            .findById(id)
            .orElseThrow(() ->
                new RuntimeException(
                    "Document introuvable"));

        // Verify workspace isolation
        if (!document.getWorkspaceId().equals(user.getWorkspaceId())) {
            throw new RuntimeException(
                "Document introuvable");
        }

        // ROLE_SUPER_ADMIN access denied (CDC p.3)
        if (user.getRole() == User.Role.SUPER_ADMIN) {
            throw new RuntimeException("Accès refusé - Isolation Super Admin");
        }

        // Verify rights for EMPLOYE (CDC Module 13)
        if (user.getRole() == User.Role.EMPLOYE) {
            EntrepriseDossier dossier = dossierRepository.findById(document.getDossierId())
                    .orElseThrow(() -> new RuntimeException("Dossier introuvable"));
            if (!dossier.getAuteurId().equals(user.getId())) {
                throw new RuntimeException("Accès refusé - Vous n'êtes pas l'auteur de ce dossier");
            }
        }

        // Log traceability (try-finally as required)
        try {
            return document;
        } finally {
            historiqueService.logDownload(
                user.getWorkspaceId(),
                user.getId(),
                user.getRole(),
                document.getDossierId(),
                document.getId(),
                document.getTitle(),
                document.getVersionNumber(),
                ipAddress,
                "DOC" // Default format, can be refined if title ends with extension
            );
        }
    }

    @Transactional
    public Map<String, Object> generateZip(
            UUID dossierId,
            User user,
            String ipAddress) {

        setTenant(user.getWorkspaceId());

        EntrepriseDossier dossier = dossierRepository.findById(dossierId)
                .filter(d -> d.getWorkspaceId().equals(user.getWorkspaceId()))
                .orElseThrow(() -> new RuntimeException("Dossier introuvable"));

        // Verify rights for EMPLOYE
        if (user.getRole() == User.Role.EMPLOYE && !dossier.getAuteurId().equals(user.getId())) {
            throw new RuntimeException("Accès refusé - Vous n'êtes pas l'auteur de ce dossier");
        }

        List<Document> documents = documentRepository
            .findByDossierIdOrderByVersionNumberDesc(
                dossierId);

        if (documents.isEmpty()) {
            throw new RuntimeException(
                "Aucun document à archiver");
        }

        String zipUuid = UUID.randomUUID().toString();
        String zipUrl = "/zip/" + zipUuid + ".zip";

        try {
            historiqueRepository.save(Historique.builder()
                .workspaceId(user.getWorkspaceId())
                .dossierId(dossierId)
                .userId(user.getId())
                .action("ZIP_GENERE")
                .nouvelleValeur(documents.size()
                    + " documents archivés")
                .ipAddress(ipAddress)
                .build());

            return buildZipResult(zipUuid, zipUrl, documents.size());
        } finally {
            // Also log as DOCUMENT_TELECHARGE with format ZIP
            historiqueService.logDownload(
                user.getWorkspaceId(),
                user.getId(),
                user.getRole(),
                dossierId,
                null, // multiple docs
                "Archive ZIP - " + dossier.getDenomination(),
                null, 
                ipAddress,
                "ZIP"
            );
        }
    }

    private Map<String, Object> buildZipResult(String zipUuid, String zipUrl, int count) {
        Map<String, Object> result = new HashMap<>();
        result.put("zip_url", zipUrl);
        result.put("zip_id", zipUuid);
        result.put("documents_count", count);
        result.put("expires_in", 3600);
        result.put("message", "Archive ZIP générée avec succès");
        return result;
    }

    private String buildLegalDocument(
            EntrepriseDossier dossier,
            String documentType) {

        return switch (documentType.toUpperCase()) {
            case "STATUTS_SARL" ->
                "STATUTS DE LA SOCIÉTÉ " +
                dossier.getDenomination() + "\n" +
                "Forme juridique: " +
                dossier.getFormeJuridique() + "\n" +
                "Capital social: " +
                dossier.getCapitalSocial() + " MAD\n" +
                "Siège social: " +
                dossier.getSiegeSocial() + "\n" +
                "Gérant: " + dossier.getGerant() + "\n" +
                "[Clauses générées par IA — " +
                "À valider par un juriste]";
            case "ACTE_DISSOLUTION" ->
                "ACTE DE DISSOLUTION\n" +
                "Société: " +
                dossier.getDenomination() + "\n" +
                "[Clauses de dissolution — IA]";
            default ->
                "DOCUMENT JURIDIQUE\n" +
                dossier.getDenomination() + "\n" +
                "[Généré par IA]";
        };
    }
}
