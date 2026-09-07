package ma.jurika.ticket.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketDemarcheJustificatifJpaRepository
        extends JpaRepository<TicketDemarcheJustificatifEntity, UUID> {

    List<TicketDemarcheJustificatifEntity> findByTicketDemarcheIdIn(List<UUID> ticketDemarcheIds);

    void deleteByTicketDemarcheId(UUID ticketDemarcheId);
}
