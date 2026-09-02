package fr.maghreb.gje.dto.succursale;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SuccursaleRequest {
    @NotBlank
    private String denomination;
    @NotBlank
    private String adresse;
    private String rcNumber;
}
