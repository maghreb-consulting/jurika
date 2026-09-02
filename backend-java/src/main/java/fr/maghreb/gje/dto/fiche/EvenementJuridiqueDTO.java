package fr.maghreb.gje.dto.fiche;

import fr.maghreb.gje.models.EvenementType;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class EvenementJuridiqueDTO {
    private UUID id;
    private LocalDate date;
    private EvenementType typeEvenement;
    private String description;
    private UUID documentId;
    private String auteurNom;
    private Integer versionDossier;
}
