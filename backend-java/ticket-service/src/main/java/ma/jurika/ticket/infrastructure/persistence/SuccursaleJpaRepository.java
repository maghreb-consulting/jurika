package ma.jurika.ticket.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SuccursaleJpaRepository extends JpaRepository<SuccursaleEntity, UUID> {

    /**
     * Liste les succursales d'une societe mere pour un statut donne (typiquement
     * ACTIVE), scopees au workspace. Ordre stable par denomination puis creation.
     */
    List<SuccursaleEntity> findByWorkspaceIdAndParentDossierIdAndStatutOrderByDenominationAscCreatedAtAsc(
            UUID workspaceId, UUID parentDossierId, String statut);
}
