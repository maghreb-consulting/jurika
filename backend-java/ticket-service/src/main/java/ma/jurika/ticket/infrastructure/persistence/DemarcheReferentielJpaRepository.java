package ma.jurika.ticket.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemarcheReferentielJpaRepository extends JpaRepository<DemarcheReferentielEntity, UUID> {

    List<DemarcheReferentielEntity> findByWorkflowTypeOrderByOrdreAsc(String workflowType);

    List<DemarcheReferentielEntity> findByWorkflowTypeAndStatutTicketOrderByOrdreAsc(
            String workflowType, String statutTicket);

    Optional<DemarcheReferentielEntity> findByWorkflowTypeAndOrdre(String workflowType, Short ordre);
}
