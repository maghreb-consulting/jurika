package ma.jurika.auth.domain.port;

import java.util.Map;
import java.util.UUID;

public interface AuditLogger {

    void log(UUID workspaceId, UUID userId, String action, String entityType, UUID entityId,
             String ipAddress, String userAgent, Map<String, Object> metadata);
}
