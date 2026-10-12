package ma.jurika.ticket.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ma.jurika.ticket.domain.model.DeboursCategorie;
import ma.jurika.ticket.domain.model.DeboursSaisie;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lot L5 (E6, RG-DEB-01) : saisie d'une ligne de debours. La piece justificative est deposee
 * d'abord en Data Room du dossier ; son identifiant est transmis ici.
 */
public record DeboursRequest(
        @NotBlank @Size(max = 200) String libelle,
        @NotNull DeboursCategorie categorie,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal montant,
        @NotNull LocalDate dateEngagement,
        UUID pieceDocumentId,
        @Size(max = 255) String pieceJointeFilename,
        String notes
) {
    public DeboursSaisie saisie() {
        return new DeboursSaisie(libelle, categorie, montant, dateEngagement, pieceDocumentId,
                pieceJointeFilename, notes);
    }
}
