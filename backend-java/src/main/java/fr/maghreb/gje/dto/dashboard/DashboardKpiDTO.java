package fr.maghreb.gje.dto.dashboard;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardKpiDTO {
    private long totalDossiers;
    private long ticketsEnRevision;
    private long ticketsBloques48h;
    private double storageUsedPercentage;
    private double storageLimitGb;
    private double storageUsedGb;
}
