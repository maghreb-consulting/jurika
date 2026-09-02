package ma.jurika.auth.infrastructure.audit;

import ma.jurika.auth.domain.port.AuditLogger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Component
public class AuditLogAdapter implements AuditLogger {

    private final AuditLogJpaRepository repository;

    public AuditLogAdapter(AuditLogJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID workspaceId, UUID userId, String action, String entityType, UUID entityId,
                    String ipAddress, String userAgent, Map<String, Object> metadata) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setUserId(userId);
        entity.setAction(action);
        entity.setEntityType(entityType);
        entity.setEntityId(entityId);
        entity.setIpAddress(ipAddress);
        entity.setUserAgent(userAgent);
        entity.setMetadata(metadata);
        repository.save(entity);
    }
}
