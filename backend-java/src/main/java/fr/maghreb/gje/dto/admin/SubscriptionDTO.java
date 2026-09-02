package fr.maghreb.gje.dto.admin;

import fr.maghreb.gje.models.FormatPlan;
import lombok.*;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionDTO {
    private String type;
    private Integer maxUsers;
    private Integer storageGB;
    private Integer maxDossiers;
    private LocalDate startDate;
    private LocalDate endDate;
}
