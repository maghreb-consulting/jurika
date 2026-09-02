package ma.jurika.ticket.domain.port;

import ma.jurika.ticket.domain.model.Debours;
import ma.jurika.ticket.domain.model.DeboursCategorie;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeboursRepository {

    Optional<Debours> findById(UUID workspaceId, UUID id);

    List<Debours> findByTicket(UUID workspaceId, UUID ticketId);

    Debours create(UUID workspaceId, UUID ticketId, String libelle, DeboursCategorie categorie,
                    BigDecimal montant, LocalDate dateEngagement, String pieceJointeUrl,
                    String filename, String notes, UUID createdById);

    Debours update(UUID id, String libelle, DeboursCategorie categorie, BigDecimal montant,
                    LocalDate dateEngagement, String notes);

    void delete(UUID workspaceId, UUID id);

    BigDecimal sumByTicket(UUID workspaceId, UUID ticketId);
}
