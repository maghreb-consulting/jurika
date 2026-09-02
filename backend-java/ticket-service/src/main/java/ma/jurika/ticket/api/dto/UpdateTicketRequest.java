package ma.jurika.ticket.api.dto;

import jakarta.validation.constraints.Size;
import ma.jurika.ticket.domain.model.TicketPriorite;

import java.time.Instant;
import java.util.UUID;

public record UpdateTicketRequest(
        @Size(max = 200) String titre,
        String description,
        TicketPriorite priorite,
        UUID assigneId,
        Instant deadline
) {}
