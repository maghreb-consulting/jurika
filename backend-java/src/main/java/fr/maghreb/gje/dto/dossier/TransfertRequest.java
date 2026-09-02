package fr.maghreb.gje.dto.dossier;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.UUID;
@Data
public class TransfertRequest {
    @NotNull private UUID nouvelAuteurId;
    @NotBlank private String raison;
}
