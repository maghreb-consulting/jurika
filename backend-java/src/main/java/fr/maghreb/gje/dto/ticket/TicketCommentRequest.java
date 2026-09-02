package fr.maghreb.gje.dto.ticket;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data
public class TicketCommentRequest {
    @NotBlank
    private String comment;
}
