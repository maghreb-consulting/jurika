package ma.jurika.workflow.domain.strategy;

import java.util.Map;
import java.util.UUID;

public record StepContext(
        UUID workspaceId,
        UUID ticketId,
        UUID userId,
        int step,
        Map<String, Object> payload,
        Map<String, Object> existingData
) {}
