package ma.jurika.ticket.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lot L5 (E6, RG-DEB-01) : ce que l'employe saisit pour une ligne de debours. La piece
 * justificative est un document de la Data Room du dossier (identifiant et nom de fichier).
 */
public record DeboursSaisie(
        String libelle,
        DeboursCategorie categorie,
        BigDecimal montant,
        LocalDate dateEngagement,
        UUID pieceDocumentId,
        String pieceJointeFilename,
        String notes
) {}
