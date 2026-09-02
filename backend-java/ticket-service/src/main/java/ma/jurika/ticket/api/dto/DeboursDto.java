package ma.jurika.ticket.api.dto;

import ma.jurika.ticket.domain.model.Debours;
import ma.jurika.ticket.domain.model.DeboursCategorie;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DeboursDto(
        UUID id,
        UUID ticketId,
        String libelle,
        DeboursCategorie categorie,
        BigDecimal montantMad,
        LocalDate dateEngagement,
        String pieceJointeUrl,
        String pieceJointeFilename,
        String notes,
        Instant createdAt
) {
    public static DeboursDto from(Debours d) {
        return new DeboursDto(d.id(), d.ticketId(), d.libelle(), d.categorie(),
                d.montantMad(), d.dateEngagement(), d.pieceJointeUrl(),
                d.pieceJointeFilename(), d.notes(), d.createdAt());
    }
}
