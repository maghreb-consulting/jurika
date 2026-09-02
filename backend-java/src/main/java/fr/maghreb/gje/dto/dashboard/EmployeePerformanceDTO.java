package fr.maghreb.gje.dto.dashboard;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class EmployeePerformanceDTO {
    private UUID userId;
    private String fullName;
    private long dossiersTraites;
    private long ticketsTermines;
    private long ticketsBloques;
    private double respectDelaisPercentage;
}
