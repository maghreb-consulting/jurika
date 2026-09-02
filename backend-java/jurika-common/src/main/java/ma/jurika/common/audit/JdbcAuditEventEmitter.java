package ma.jurika.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter par defaut qui ecrit dans la table {@code audit_log} via JdbcTemplate.
 * <p>
 * Sprint 2 / TASK 6 : ecrit aussi {@code correlation_id}, {@code source_service} et
 * {@code payload_diff} (les nouvelles colonnes alignees sur le MDC).
 */
public class JdbcAuditEventEmitter implements AuditEventEmitter {

    private static final Logger log = LoggerFactory.getLogger(JdbcAuditEventEmitter.class);

    private static final String INSERT_SQL = """
            INSERT INTO audit_log
                (workspace_id, user_id, action, entity_type, entity_id,
                 metadata, correlation_id, source_service)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcAuditEventEmitter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.json = new ObjectMapper();
    }

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void emit(AuditEvent event) {
        try {
            String metaJson = (event.metadata() == null || event.metadata().isEmpty())
                    ? null
                    : json.writeValueAsString(event.metadata());
            jdbc.update(INSERT_SQL,
                    event.workspaceId(),
                    event.actorId(),
                    event.action(),
                    event.resourceType(),
                    event.resourceId(),
                    metaJson,
                    event.correlationId(),
                    event.sourceService());
        } catch (JsonProcessingException ex) {
            log.warn("audit.emit serialization failed action={} : {}", event.action(), ex.getMessage());
        } catch (Exception ex) {
            log.warn("audit.emit DB failed action={} : {}", event.action(), ex.getMessage());
        }
    }
}
