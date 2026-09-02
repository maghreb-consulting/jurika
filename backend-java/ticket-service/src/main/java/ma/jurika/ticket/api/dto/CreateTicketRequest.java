package ma.jurika.ticket.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ma.jurika.ticket.domain.model.FormeJuridique;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketType;

import java.time.Instant;
import java.util.UUID;

public record CreateTicketRequest(
        @NotBlank @Size(max = 200) String titre,
        @NotNull TicketType type,
        TicketPriorite priorite,
        UUID dossierId,
        UUID assigneId,
        String description,
        Instant deadline,
        /**
         * Obligatoire pour type=CREATION ou type=IMPORT (sauf si dossierId fourni).
         * Cree automatiquement le dossier d'entreprise + son Data Room.
         */
        @Valid CompanyInfoRequest companyInfo
) {
    public record CompanyInfoRequest(
            @NotBlank @Size(max = 200) String raisonSociale,
            @NotNull FormeJuridique formeJuridique
    ) {}
}
