package fr.maghreb.gje.dto.admin;

import lombok.*;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceDetailDTO {
    private UUID id;
    private String name;
    private String code;
    private Boolean isActive;
    private SubscriptionDTO subscription;
}
