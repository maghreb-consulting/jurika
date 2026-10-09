package ma.jurika.dataroom.application;

import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.infrastructure.persistence.DocumentEntity;
import ma.jurika.dataroom.infrastructure.persistence.DocumentJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Lot L1 : gardes de l'employe en Data Room.
 * <ul>
 *   <li>RG-DOS-01 : un employe ne voit et ne traite que les dossiers dont il est
 *       responsable, y compris par acces direct (UUID) : 404 sinon (on ne revele pas
 *       l'existence du dossier) ;</li>
 *   <li>RG-DR-06 / CDC 3.2 : il ne supprime un document que si le superviseur lui en
 *       a accorde le droit : 403 sinon.</li>
 * </ul>
 */
@Component
public class EmployeDataroomGuard {

    private final DocumentJpaRepository documents;
    private final DossierViewJpaRepository dossiers;
    private final DroitSuppressionLookup droits;

    public EmployeDataroomGuard(DocumentJpaRepository documents, DossierViewJpaRepository dossiers,
                                DroitSuppressionLookup droits) {
        this.documents = documents;
        this.dossiers = dossiers;
        this.droits = droits;
    }

    /** Suppression d'un document : employe responsable du dossier, titulaire du droit. */
    @Transactional(readOnly = true)
    public void assertPeutSupprimerDocument(UUID documentId, AuthenticatedUser user) {
        if (user == null || user.role() != Role.EMPLOYE) {
            throw new AccessDeniedException("Seul un employe supprime un document en Data Room");
        }
        UUID dossierId = dossierDuDocument(documentId);
        assertResponsable(dossierId, user);
        if (!droits.aLeDroit(TenantContext.get(), user.userId())) {
            throw new AccessDeniedException("Suppression refusee : le droit de suppression en Data Room "
                    + "ne vous a pas ete accorde par le superviseur.");
        }
    }

    /** RG-DOS-01 : l'employe doit etre responsable du dossier (404 sinon). Autres roles : sans effet. */
    @Transactional(readOnly = true)
    public void assertResponsable(UUID dossierId, AuthenticatedUser user) {
        if (user == null || user.role() != Role.EMPLOYE) {
            return;
        }
        UUID ws = TenantContext.get();
        UUID responsable = ws == null ? null : dossiers.findByWorkspaceIdAndId(ws, dossierId)
                .map(d -> d.getResponsableId()).orElse(null);
        if (!user.userId().equals(responsable)) {
            throw new NotFoundException("Dossier inconnu");
        }
    }

    /** RG-DOS-01 applique a un document : son dossier doit etre celui de l'employe. */
    @Transactional(readOnly = true)
    public void assertResponsableDuDocument(UUID documentId, AuthenticatedUser user) {
        if (user == null || user.role() != Role.EMPLOYE) {
            return;
        }
        assertResponsable(dossierDuDocument(documentId), user);
    }

    private UUID dossierDuDocument(UUID documentId) {
        UUID ws = TenantContext.get();
        DocumentEntity doc = documents.findById(documentId)
                .filter(d -> ws != null && ws.equals(d.getWorkspaceId()))
                .orElseThrow(() -> new NotFoundException("Document inconnu : " + documentId));
        return doc.getDossierId();
    }
}
