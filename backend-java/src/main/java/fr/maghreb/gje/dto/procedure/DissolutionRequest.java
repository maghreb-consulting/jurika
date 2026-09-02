package fr.maghreb.gje.dto.procedure;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DissolutionRequest {
    @NotBlank
    private String motif;
    private LocalDate dateAssemblee;
    private String liquidateurNom;
}
