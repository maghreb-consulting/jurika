package ma.jurika.dataroom.infrastructure.identity;

import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.application.DossierArchiveGuard;
import ma.jurika.dataroom.domain.port.IdentityArchiver;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import ma.jurika.dataroom.infrastructure.persistence.DocumentEntity;
import ma.jurika.dataroom.infrastructure.persistence.DocumentJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.UUID;

/**
 * Construit le PDF fusionné recto+verso, l'envoie dans MinIO et crée
 * l'entrée correspondante dans {@code dataroom_documents}.
 * <p>
 * NB : on ne marque pas l'ancienne version comme remplacée (chaque extraction
 * crée un document version 1 distinct, comme les uploads manuels — laisse à
 * l'employé la liberté de supprimer manuellement les doublons).
 */
@Component
public class DataroomIdentityArchiver implements IdentityArchiver {

    private final ObjectStorage storage;
    private final DocumentJpaRepository documents;
    /**
     * Lot DIVERS §A (2026-08-13) — ce chemin ecrit dans {@code dataroom_documents}
     * SANS passer par {@code DataroomJuridiqueService} : sans garde explicite, il
     * ouvrait une breche dans la lecture seule des societes dissoutes. L'endpoint
     * d'extraction ne transporte pas de {@code ticketId}, d'ou la derogation
     * « liquidation ouverte sur ce dossier ».
     */
    private final DossierArchiveGuard archiveGuard;

    public DataroomIdentityArchiver(ObjectStorage storage, DocumentJpaRepository documents,
                                    DossierArchiveGuard archiveGuard) {
        this.storage = storage;
        this.documents = documents;
        this.archiveGuard = archiveGuard;
    }

    @Override
    @Transactional
    public UUID archive(UUID dossierId,
                        UUID uploaderId,
                        String documentType,
                        String title,
                        byte[] rectoBytes, String rectoFilename, String rectoContentType,
                        byte[] versoBytes, String versoFilename, String versoContentType) {
        // §A — pas d'archivage d'identite sur une societe archivee (sauf liquidation
        // en cours). dossierId est null pendant une CREATION : le garde laisse passer.
        archiveGuard.assertWritableForLiquidationCapableWrite(dossierId);
        UUID workspaceId = TenantContext.get();
        Instant now = Instant.now();

        byte[] pdf = IdentityPdfBuilder.build(
                rebuildPageBytes(rectoBytes, rectoContentType),
                versoBytes == null ? null : rebuildPageBytes(versoBytes, versoContentType));

        String safeBase = title == null ? documentType.toLowerCase() : title.replaceAll("[^a-zA-Z0-9._-]", "_");
        String filename = safeBase + ".pdf";
        String key = "ws/" + workspaceId + "/dossier/" + dossierId
                + "/juridique/" + documentType
                + "/v1_" + System.currentTimeMillis() + "_" + filename;

        storage.upload(key, new ByteArrayInputStream(pdf), pdf.length, "application/pdf");

        // Fix DR1 (2026-08-16) — cet archivage écrivait `is_current = true` en direct,
        // sans passer par le chemin de versioning. Depuis que la base impose UN SEUL
        // courant par slot (index unique partiel, migration V23), ré-archiver la même
        // pièce pour la même personne violerait la contrainte. On bascule donc
        // l'éventuel courant du slot en historique et on incrémente la version — même
        // règle que les autres dépôts, au lieu d'un doublon ou d'une erreur SQL.
        short nextVersion = 1;
        var previous = documents.findCurrentBySlot(dossierId, documentType, title);
        if (previous.isPresent()) {
            DocumentEntity p = previous.get();
            documents.markReplaced(p.getId(), now);
            nextVersion = (short) (p.getVersion() + 1);
        }

        DocumentEntity e = new DocumentEntity();
        e.setWorkspaceId(workspaceId);
        e.setDossierId(dossierId);
        e.setTicketId(null);
        e.setDocumentType(documentType);
        e.setTitle(title);
        e.setVersion(nextVersion);
        e.setCurrent(true);
        e.setObjectKey(key);
        e.setFilename(filename);
        e.setContentType("application/pdf");
        e.setSizeBytes(pdf.length);
        e.setUploadedBy(uploaderId);
        e.setCreatedAt(now);
        documents.save(e);
        return e.getId();
    }

    /**
     * Garde-fou : si le client a uploadé un PDF (pas une image), on ne peut pas le
     * réembarquer naïvement comme une "Image" iText. Pour le V1 on accepte
     * seulement des images côté archivage — si l'octet 0 est %PDF on lève.
     * <p>
     * (Le flux nominal côté front : photo téléphone JPEG/PNG.)
     */
    private byte[] rebuildPageBytes(byte[] data, String contentType) {
        if (data == null) return null;
        if (data.length >= 5 && data[0] == '%' && data[1] == 'P' && data[2] == 'D' && data[3] == 'F') {
            throw new IllegalArgumentException(
                    "Archivage : seules les images recto/verso sont supportées (pas de PDF en entrée).");
        }
        return data;
    }
}
