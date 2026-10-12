package ma.jurika.ticket.api.dto;

import ma.jurika.ticket.domain.model.Debours;
import ma.jurika.ticket.domain.model.DeboursCategorie;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ligne de debours exposee aux ecrans. Lot L5 (E6) : {@code categorieReprise} (ancienne
 * categorie reprise par V40) et {@code pieceDocumentId} (piece en Data Room du dossier).
 */
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
        Instant createdAt,
        String categorieReprise,
        UUID pieceDocumentId
) {
    public static DeboursDto from(Debours d) {
        return new DeboursDto(d.id(), d.ticketId(), d.libelle(), d.categorie(),
                d.montantMad(), d.dateEngagement(), d.pieceJointeUrl(),
                d.pieceJointeFilename(), d.notes(), d.createdAt(),
                d.categorieReprise(), d.pieceDocumentId());
    }
}
