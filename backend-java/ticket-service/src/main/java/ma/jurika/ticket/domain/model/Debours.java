package ma.jurika.ticket.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Une ligne de frais avances pour le client (RG-DEB-01).
 *
 * @param categorieReprise lot L5 (V40) : categorie anterieure conservee par la reprise des
 *                         anciennes valeurs, null sinon
 * @param pieceDocumentId  lot L5 (V40) : piece justificative deposee en Data Room du dossier
 */
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
        Instant createdAt,
        String categorieReprise,
        UUID pieceDocumentId
) {}
