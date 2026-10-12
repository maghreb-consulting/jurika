package ma.jurika.dataroom.application;

import ma.jurika.common.courriel.CourrielClientEvenement;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.DocumentSummary;
import ma.jurika.dataroom.domain.port.CourrielsClient;
import ma.jurika.dataroom.infrastructure.persistence.DocumentJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lot L5 (E9, RG-COM-09) : le client est averti PAR COURRIEL d'un document mis a
 * sa disposition. Appele par le controleur une fois la visibilite reglee
 * (depot, depot multiple, validation d'un acte genere, document rendu visible).
 *
 * <p>Seuls les documents VISIBLES du client sont annonces, et seulement si le
 * dossier a un client. Un depot de plusieurs pieces donne UN courriel. Les
 * nouvelles versions issues de l'edition en ligne ne sont pas annoncees (chaque
 * enregistrement de l'editeur en cree une).
 */
@Service
public class AnnonceDocumentsClient {

    private final DossierViewJpaRepository dossiers;
    private final DocumentJpaRepository documents;
    private final CourrielsClient courriels;

    public AnnonceDocumentsClient(DossierViewJpaRepository dossiers, DocumentJpaRepository documents,
                                  CourrielsClient courriels) {
        this.dossiers = dossiers;
        this.documents = documents;
        this.courriels = courriels;
    }

    /** Le document est-il deja visible du client ? (avant un changement de visibilite) */
    @Transactional(readOnly = true)
    public boolean estVisible(UUID documentId) {
        UUID ws = TenantContext.get();
        if (ws == null) return false;
        return documents.findByWorkspaceIdAndId(ws, documentId).map(d -> d.isVisibleClient()).orElse(false);
    }

    /** Annonce au client les documents visibles parmi {@code docs} (un seul courriel). */
    @Transactional(readOnly = true)
    public boolean annoncer(List<DocumentSummary> docs) {
        UUID ws = TenantContext.get();
        if (ws == null || docs == null) return false;
        List<DocumentSummary> visibles = docs.stream()
                .filter(d -> d != null && d.visibleClient() && d.dossierId() != null).toList();
        if (visibles.isEmpty()) return false;
        UUID dossierId = visibles.get(0).dossierId();
        DossierViewEntity dossier = dossiers.findByWorkspaceIdAndId(ws, dossierId).orElse(null);
        if (dossier == null || dossier.getClientId() == null) return false;
        String objet = visibles.stream().filter(d -> dossierId.equals(d.dossierId()))
                .map(DocumentSummary::title).distinct().collect(Collectors.joining(", "));
        courriels.demander(new CourrielClientEvenement(CourrielClientEvenement.TYPE_DOCUMENT_DISPONIBLE,
                ws, dossierId, dossier.getClientId(), dossier.getRaisonSociale(), objet, "/data-rooms"));
        return true;
    }
}
