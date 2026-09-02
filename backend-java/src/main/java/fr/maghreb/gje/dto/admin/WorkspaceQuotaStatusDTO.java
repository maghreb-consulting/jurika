package fr.maghreb.gje.dto.admin;

import lombok.*;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceQuotaStatusDTO {
    private UUID workspaceId;
    private String workspaceName;
    
    private StorageQuotaInfo storage;
    private ResourceQuotaInfo dossiers;
    private ResourceQuotaInfo users;
    private QuotaAlerts alerts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StorageQuotaInfo {
        private Long usedBytes;
        private Long limitBytes;
        private Double usedGb;
        private Double limitGb;
        private Double percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceQuotaInfo {
        private Long used;
        private Long limit;
        private Double percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuotaAlerts {
        private boolean near90;
        private boolean exceeded;
    }
}
