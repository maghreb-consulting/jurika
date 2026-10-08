package ma.jurika.ticket.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface TicketJpaRepository extends JpaRepository<TicketEntity, UUID>, JpaSpecificationExecutor<TicketEntity> {

    /**
     * Lot L0 (E21, P9) : lecture filtree explicitement par workspace.
     * Transactionnelle comme les methodes CRUD heritees (findById) : une methode
     * de requete declaree ne l'est pas par defaut, et hors transaction la RLS
     * n'a pas de workspace (garde « hors-transaction » sous jurika_app).
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    java.util.Optional<TicketEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
