package fr.maghreb.gje.dto.procedure;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiquidationRequest {
    private String mode; // AMIABLE | JUDICIAIRE
    @NotBlank
    private String description;
    private String clotureMotif;
}
