package ma.jurika.ticket.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record Debours(
        UUID id,
        UUID workspaceId,
        UUID ticketId,
        String libelle,
        DeboursCategorie categorie,
        BigDecimal montantMad,
        LocalDate dateEngagement,
        String pieceJointeUrl,
        String pieceJointeFilename,
        String notes,
        UUID createdById,
        Instant createdAt
) {}
