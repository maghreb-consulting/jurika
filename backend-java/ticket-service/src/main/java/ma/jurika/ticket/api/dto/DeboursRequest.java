package ma.jurika.ticket.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ma.jurika.ticket.domain.model.DeboursCategorie;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DeboursRequest(
        @NotBlank String libelle,
        @NotNull DeboursCategorie categorie,
        @NotNull @DecimalMin("0.00") BigDecimal montant,
        @NotNull LocalDate dateEngagement,
        String pieceJointeUrl,
        String pieceJointeFilename,
        String notes
) {}
