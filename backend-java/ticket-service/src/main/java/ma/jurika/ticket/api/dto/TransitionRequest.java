package ma.jurika.ticket.api.dto;

import jakarta.validation.constraints.NotNull;
import ma.jurika.ticket.domain.model.TicketStatut;

public record TransitionRequest(@NotNull TicketStatut target, String comment) {}
