package fr.maghreb.gje.dto.dashboard;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MonthlyActivityDTO {
    private String month; // YYYY-MM
    private long dossiersCrees;
    private long ticketsCloturés;
}
