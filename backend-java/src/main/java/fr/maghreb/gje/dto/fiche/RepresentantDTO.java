package fr.maghreb.gje.dto.fiche;

import fr.maghreb.gje.models.Representant.Qualite;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class RepresentantDTO {
    private UUID id;
    private String nomPrenom;
    private Qualite qualite;
    private String cin; // Will be masked or unmasked depending on Role
    private LocalDate datePriseFonction;
}
