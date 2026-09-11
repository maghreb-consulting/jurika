package ma.jurika.dataroom.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VisibiliteEvenementJpaRepository
        extends JpaRepository<VisibiliteEvenementEntity, UUID> {

    List<VisibiliteEvenementEntity> findByDocumentIdOrderBySurvenuLeAsc(UUID documentId);

    /** Ajoute un evenement. Le journal n'expose ni mise a jour ni suppression. */
    default UUID enregistrer(UUID workspaceId, UUID documentId, boolean visible,
                             String origine, UUID acteurId) {
        VisibiliteEvenementEntity e = new VisibiliteEvenementEntity();
        e.setWorkspaceId(workspaceId);
        e.setDocumentId(documentId);
        e.setVisible(visible);
        e.setOrigine(origine == null || origine.isBlank() ? "DATAROOM" : origine);
        e.setActeurId(acteurId);
        return save(e).getId();
    }
}
