package fr.maghreb.gje.dto.document;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AIExtractionRequest {
    @NotBlank
    private String fileContent; // Base64
}
