package fr.maghreb.gje.dto.dashboard;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class DashboardAlerteDTO {
    private UUID dossierId;
    private String denomination;
    private String alerteType; // EN_RETARD | TICKET_BLOQUE
    private String message;
}
