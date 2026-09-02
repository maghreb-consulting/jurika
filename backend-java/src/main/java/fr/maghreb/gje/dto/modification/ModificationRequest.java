package fr.maghreb.gje.dto.modification;

import fr.maghreb.gje.models.ModificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ModificationRequest {
    @NotNull
    private ModificationType type;
    @NotBlank
    private String description;
    private String ancienneValeur;
    private String nouvelleValeur;
}
