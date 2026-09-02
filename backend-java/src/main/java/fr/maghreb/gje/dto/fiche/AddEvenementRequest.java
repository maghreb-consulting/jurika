package fr.maghreb.gje.dto.fiche;

import fr.maghreb.gje.models.EvenementType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddEvenementRequest {
    private LocalDate date;
    private EvenementType typeEvenement;
    private String description;
    private UUID documentId;
}
