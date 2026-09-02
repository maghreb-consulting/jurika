package ma.jurika.ticket.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DossierTransfertRequestJpaRepository
        extends JpaRepository<DossierTransfertRequestEntity, UUID> {

    boolean existsByWorkspaceIdAndDossierIdAndStatut(UUID workspaceId, UUID dossierId, String statut);

    List<DossierTransfertRequestEntity> findByWorkspaceIdAndToUserIdAndStatutOrderByCreatedAtDesc(
            UUID workspaceId, UUID toUserId, String statut);

    List<DossierTransfertRequestEntity> findByWorkspaceIdAndFromUserIdAndStatutOrderByCreatedAtDesc(
            UUID workspaceId, UUID fromUserId, String statut);
}
