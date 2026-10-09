package ma.jurika.auth.infrastructure.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.auth.domain.port.AuditLogger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Ecrit le journal d'audit d'auth-service.
 *
 * <p>Lot L0 (E10b) : INSERT JDBC simple, sans {@code RETURNING}. L'ecriture se
 * fait sur un fil asynchrone, sans workspace courant. En JPA, l'identifiant
 * IDENTITY etait relu par {@code INSERT ... RETURNING}, qui soumet la ligne aux
 * politiques SELECT d'audit_log : sous un role soumis a la RLS, une trace
 * portant un workspace aurait ete refusee, et perdue en silence dans le fil
 * asynchrone. Un INSERT simple releve de la politique
 * {@code audit_log_insert WITH CHECK (true)} (auth V8).
 */
@Component
public class AuditLogAdapter implements AuditLogger {

    private static final String INSERT_SQL = """
            INSERT INTO audit_log (workspace_id, user_id, action, entity_type, entity_id,
                                   ip_address, user_agent, metadata, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditLogAdapter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID workspaceId, UUID userId, String action, String entityType, UUID entityId,
                    String ipAddress, String userAgent, Map<String, Object> metadata) {
        jdbc.update(INSERT_SQL, workspaceId, userId, action, entityType, entityId,
                ipAddress, userAgent, json(metadata), Timestamp.from(Instant.now()));
    }

    private String json(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Metadonnees d'audit non serialisables : " + e.getMessage(), e);
        }
    }
}
