package ma.jurika.auth.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 2 / Lot D — test unitaire de {@link AdminAuditController} : verifie le
 * comportement SQL (filtres -> WHERE clauses, RLS bypass active, pagination).
 * <p>
 * On ne charge pas Spring : on instancie le controller directement avec un
 * JdbcTemplate mocke. Les contraintes @PreAuthorize sont couvertes par les tests
 * d'integration (smoke .http).
 */
class AdminAuditControllerTest {

    private JdbcTemplate jdbc;
    private AdminAuditController controller;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        controller = new AdminAuditController(jdbc);
    }

    @Test
    void enablesRlsBypassBeforeQuerying() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        controller.search(null, null, null, null, null, null, null,0, 50);

        verify(jdbc).execute("SET LOCAL app.audit_bypass = 'true'");
    }

    @Test
    void appliesAllFilters() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-05-22T00:00:00Z");

        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(42L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        AdminAuditController.PageResponse page = controller.search(
                workspaceId, userId, "EMPLOYE", "LOGIN_SUCCESS", "auth-service", from, to, 0, 50);

        assertThat(page.total()).isEqualTo(42L);

        ArgumentCaptor<String> sqlCount = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sqlCount.capture(), eq(Long.class), any(Object[].class));
        assertThat(sqlCount.getValue())
                .contains("workspace_id = ?")
                .contains("user_id = ?")
                // Le role n'est PAS stocke dans audit_log : le filtre passe par une
                // sous-requete sur `users`, pas par une colonne `role = ?`.
                .contains("user_id IN (SELECT id FROM users WHERE role = ?)")
                .contains("action = ?")
                .contains("source_service = ?")
                .contains("created_at >= ?")
                .contains("created_at <  ?");
    }

    @Test
    void trimsRoleAndIgnoresBlankOne() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        controller.search(null, null, "  EMPLOYE  ", null, null, null, null, 0, 50);

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForObject(anyString(), eq(Long.class), args.capture());
        assertThat(args.getValue()).containsExactly("EMPLOYE");
    }

    @Test
    void blankRoleAddsNoClause() {
        // Une chaine vide venant d'un <select> non renseigne ne doit pas restreindre
        // le journal aux utilisateurs d'un role inexistant.
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        controller.search(null, null, "   ", null, null, null, null, 0, 50);

        ArgumentCaptor<String> sqlCount = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sqlCount.capture(), eq(Long.class), any(Object[].class));
        assertThat(sqlCount.getValue()).doesNotContain("FROM users WHERE role");
    }

    @Test
    void clampsLimitToMax200() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        AdminAuditController.PageResponse page = controller.search(null, null, null, null, null, null, null,0, 9999);
        assertThat(page.limit()).isEqualTo(200);
    }

    @Test
    void clampsNegativeOffsetToZero() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        AdminAuditController.PageResponse page = controller.search(null, null, null, null, null, null, null,-5, 50);
        assertThat(page.offset()).isZero();
    }

    @Test
    void mapsRowsToAuditEntries() throws Exception {
        UUID id = UUID.randomUUID();
        UUID ws = UUID.randomUUID();
        Instant now = Instant.now();

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(123L);
        when(rs.getObject("workspace_id")).thenReturn(ws);
        when(rs.getObject("user_id")).thenReturn(id);
        when(rs.getString("action")).thenReturn("LOGIN_SUCCESS");
        when(rs.getString("entity_type")).thenReturn("user");
        when(rs.getObject("entity_id")).thenReturn(id);
        when(rs.getString("source_service")).thenReturn("auth-service");
        when(rs.getString("correlation_id")).thenReturn("corr-abc");
        when(rs.getString("metadata")).thenReturn("{}");
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(now));

        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(inv -> {
                    RowMapper<AdminAuditController.AuditEntry> mapper = inv.getArgument(1);
                    return List.of(mapper.mapRow(rs, 0));
                });

        AdminAuditController.PageResponse page = controller.search(null, null, null, null, null, null, null,0, 50);

        assertThat(page.items()).hasSize(1);
        AdminAuditController.AuditEntry entry = page.items().get(0);
        assertThat(entry.id()).isEqualTo(123L);
        assertThat(entry.action()).isEqualTo("LOGIN_SUCCESS");
        assertThat(entry.sourceService()).isEqualTo("auth-service");
        assertThat(entry.correlationId()).isEqualTo("corr-abc");
        assertThat(entry.workspaceId()).isEqualTo(ws);
    }
}
