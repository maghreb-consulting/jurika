package fr.maghreb.gje.dto.fiche;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class EtablissementSecondaireDTO {
    private UUID id;
    private String adresse;
    private String activite;
    private String denomination;
}
