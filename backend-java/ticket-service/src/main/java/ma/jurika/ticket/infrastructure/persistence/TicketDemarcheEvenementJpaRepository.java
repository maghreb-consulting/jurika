package ma.jurika.ticket.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TicketDemarcheEvenementJpaRepository
        extends JpaRepository<TicketDemarcheEvenementEntity, UUID> {

    List<TicketDemarcheEvenementEntity> findByTicketDemarcheIdInOrderBySurvenuLeAsc(
            Collection<UUID> ticketDemarcheIds);
}
