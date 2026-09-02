package fr.maghreb.gje.dto.ticket;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data
public class TicketUpdateStatutRequest {
    @NotBlank
    private String statut;
    private String comment;
    private String blockedReason;
}
