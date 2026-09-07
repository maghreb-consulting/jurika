package ma.jurika.common.audit;

import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Sprint 14 bis / TASK C3 — AuditAspectIT.
 *
 * <p>Resorbe la dette D-S2-02 (absence d'IT pour l'aspect AOP central). 8 cas
 * couvrant les chemins critiques de {@link AuditAspect} :
 *
 * <ol>
 *   <li>{@code standard} : succes -> {@code action} ecrit dans audit_log avec
 *       metadata.method</li>
 *   <li>{@code resourceType+resourceId} : SpEL resourceIdExpr resolu + entity_type
 *       et entity_id en colonnes dediees</li>
 *   <li>{@code exception} : runtime exception -> {@code action_FAILED} avec
 *       metadata.error et metadata.errorMessage, exception re-thrown</li>
 *   <li>{@code RLS isolation} : ecriture WS1 invisible depuis WS2 par contexte
 *       multi-tenant (current_workspace_id)</li>
 *   <li>{@code audit_bypass SuperAdmin} : SET LOCAL app.audit_bypass=true permet
 *       lecture cross-workspace</li>
 *   <li>{@code RabbitMQ async} : si publisher Rabbit OK, JDBC fallback NON
 *       appele (separation publisher/consumer)</li>
 *   <li>{@code Rabbit fallback JDBC} : AmqpException -> bascule sur JDBC
 *       fallback emitter (zero perte)</li>
 *   <li>{@code correlation ID} : MDC.correlationId propage en colonne dediee
 *       correlation_id (jointure logs <-> audit)</li>
 * </ol>
 *
 * <p>Stack : auto-configuration jurika-common ({@link AuditAutoConfiguration})
 * sur container Postgres 16. Schema audit_log via {@code audit-it-init.sql}.
 * Tests 1-3 + 6-7-8 utilisent un emitter mock pour isoler l'aspect. Tests 4-5
 * passent par le {@link JdbcAuditEventEmitter} reel (write effectif).
 */
@Testcontainers
@SpringBootTest(
        classes = AuditAspectIT.TestApp.class,
        properties = {
                "spring.application.name=jurika-common-it",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,"
                        + "ma.jurika.common.notification.NotificationAutoConfiguration,"
                        + "ma.jurika.common.observability.OpsActuatorSecurityAutoConfiguration,"
                        + "ma.jurika.common.observability.OpsActuatorReactiveSecurityAutoConfiguration,"
                        + "ma.jurika.common.security.JwtAutoConfiguration,"
                        + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
                "jurika.audit.async-enabled=false",
                // Pool a 1 connexion : indispensable pour que SET / SET LOCAL voient
                // les memes lignes a travers BeforeEach + audit writes + assertions
                // (sinon Hikari distribue plusieurs connexions et la session SET
                // ne survit pas / contamine d'autres tests).
                "spring.datasource.hikari.maximum-pool-size=1",
                "spring.datasource.hikari.minimum-idle=1"
        }
)
class AuditAspectIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_audit_it")
            .withUsername("audit_it")
            .withPassword("audit_it_pwd")
            .withInitScript("audit-it-init.sql")
            .withReuse(false);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @SpringBootApplication
    @EnableAsync
    @EnableTransactionManagement
    @org.springframework.context.annotation.EnableAspectJAutoProxy
    static class TestApp {
        public static void main(String[] args) {
            SpringApplication.run(TestApp.class, args);
        }

        @Bean @Primary
        public TaskExecutor taskExecutor() { return new SyncTaskExecutor(); }

        // Spring n'auto-scanne pas les classes nested static des tests quand `classes`
        // est fixe sur @SpringBootTest : on declare explicitement.
        @Bean public AuditedSampleService auditedSampleService() {
            return new AuditedSampleService();
        }

        // L'auto-config jurika-common cree normalement ce bean via
        // JdbcAuditEventEmitterAutoConfiguration, mais le @ConditionalOnBean est
        // evalue avant que la DataSourceAutoConfiguration ne soit ordonnancee dans
        // ce contexte minimal. Declaration explicite pour rendre le bean dispo a
        // l'AuditAspect.
        @Bean public AuditEventEmitter jdbcAuditEventEmitter(JdbcTemplate jdbc) {
            return new JdbcAuditEventEmitter(jdbc);
        }
    }

    @Autowired private AuditedSampleService service;
    @Autowired private JdbcTemplate jdbc;

    private static final UUID WS_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WS_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_A = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeEach
    void cleanAndSeedAuth() {
        // Cleanup avec bypass dans une vraie transaction (SET LOCAL).
        jdbc.execute((java.sql.Connection conn) -> {
            boolean prev = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (var st = conn.createStatement()) {
                st.execute("SET LOCAL app.audit_bypass = 'true'");
                st.executeUpdate("DELETE FROM audit_log");
                conn.commit();
                return null;
            } finally {
                conn.setAutoCommit(prev);
            }
        });
        setAuthentication(WS_A, USER_A);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    // ============================== TEST 1 ==============================

    @Test
    @DisplayName("standard : succes -> action ecrit dans audit_log avec metadata.method")
    void successWritesActionRowWithMetadata() {
        service.doStandardThing("ping");

        Map<String, Object> row = findRow("SAMPLE_STANDARD");
        assertThat(row).isNotNull();
        assertThat(row.get("workspace_id")).isEqualTo(WS_A);
        assertThat(row.get("user_id")).isEqualTo(USER_A);
        assertThat(row.get("source_service")).isEqualTo("jurika-common-it");
        String meta = (String) row.get("metadata");
        assertThat(meta).contains("\"method\"").contains("doStandardThing");
    }

    // ============================== TEST 2 ==============================

    @Test
    @DisplayName("resourceType + resourceIdExpr SpEL : entity_type + entity_id renseignes")
    void resourceTypeAndIdResolvedFromSpel() {
        UUID id = UUID.randomUUID();
        service.doScopedThing(id, "payload");

        Map<String, Object> row = findRow("SAMPLE_SCOPED");
        assertThat(row).isNotNull();
        assertThat(row.get("entity_type")).isEqualTo("widget");
        assertThat(row.get("entity_id")).isEqualTo(id);
    }

    // ============================== TEST 3 ==============================

    @Test
    @DisplayName("exception : action_FAILED + metadata.error + exception relancee")
    void exceptionEmitsFailedSuffixAndRethrows() {
        try {
            service.doExplodingThing("boom");
            throw new AssertionError("expected exception");
        } catch (IllegalStateException ex) {
            assertThat(ex).hasMessage("boom");
        }

        Map<String, Object> row = findRow("SAMPLE_EXPLODE_FAILED");
        assertThat(row).isNotNull();
        String meta = (String) row.get("metadata");
        assertThat(meta).contains("\"error\"").contains("IllegalStateException");
        assertThat(meta).contains("\"errorMessage\"").contains("boom");
    }

    // ============================== TEST 4 ==============================

    @Test
    @DisplayName("RLS isolation : ecriture WS_A invisible depuis contexte WS_B")
    void rlsIsolationBlocksCrossWorkspaceRead() {
        // Ecrit dans WS_A
        setAuthentication(WS_A, USER_A);
        service.doStandardThing("a-side");

        // Bascule en contexte WS_B et essaie de lire : la requete doit etre filtree.
        // SET LOCAL exige une transaction explicite (sinon no-op en auto-commit).
        Long visibleFromB = queryCountInWorkspace(WS_B);
        assertThat(visibleFromB).isZero();

        // Depuis WS_A on doit voir la ligne.
        Long visibleFromA = queryCountInWorkspace(WS_A);
        assertThat(visibleFromA).isEqualTo(1L);
    }

    /**
     * Exec un COUNT(*) sous un workspace donne, en NON-superuser (SET LOCAL
     * ROLE app_no_super) pour que la RLS s'applique vraiment. Sinon le user de
     * connexion est superuser/bootstrap et bypass RLS.
     */
    private Long queryCountInWorkspace(UUID workspaceId) {
        return jdbc.execute((java.sql.Connection conn) -> {
            boolean prev = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (var st = conn.createStatement()) {
                st.execute("SET LOCAL ROLE app_no_super");
                st.execute("SET LOCAL app.current_workspace_id = '" + workspaceId + "'");
                try (var rs = st.executeQuery(
                        "SELECT COUNT(*) FROM audit_log WHERE action = 'SAMPLE_STANDARD'")) {
                    rs.next();
                    long n = rs.getLong(1);
                    conn.commit();
                    return n;
                }
            } finally {
                conn.setAutoCommit(prev);
            }
        });
    }

    // ============================== TEST 5 ==============================

    @Test
    @DisplayName("audit_bypass SuperAdmin : SET LOCAL app.audit_bypass=true autorise lecture cross-workspace")
    void auditBypassAllowsCrossWorkspaceRead() {
        setAuthentication(WS_A, USER_A);
        service.doStandardThing("a");
        setAuthentication(WS_B, UUID.randomUUID());
        service.doStandardThing("b");

        Long visibleBypass = jdbc.execute((java.sql.Connection conn) -> {
            boolean prev = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (var st = conn.createStatement()) {
                st.execute("SET LOCAL ROLE app_no_super");
                st.execute("SET LOCAL app.audit_bypass = 'true'");
                try (var rs = st.executeQuery("SELECT COUNT(*) FROM audit_log WHERE action = 'SAMPLE_STANDARD'")) {
                    rs.next();
                    long n = rs.getLong(1);
                    conn.commit();
                    return n;
                }
            } finally {
                conn.setAutoCommit(prev);
            }
        });
        assertThat(visibleBypass).isEqualTo(2L);

        // Verifie aussi le scenario inverse : sans bypass + workspace different = invisible.
        Long visibleNoContext = jdbc.execute((java.sql.Connection conn) -> {
            boolean prev = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (var st = conn.createStatement()) {
                st.execute("SET LOCAL ROLE app_no_super");
                try (var rs = st.executeQuery("SELECT COUNT(*) FROM audit_log WHERE action = 'SAMPLE_STANDARD'")) {
                    rs.next();
                    long n = rs.getLong(1);
                    conn.commit();
                    return n;
                }
            } finally {
                conn.setAutoCommit(prev);
            }
        });
        assertThat(visibleNoContext).isZero();
    }

    // ============================== TEST 6 ==============================

    @Test
    @DisplayName("RabbitMQ async OK : publisher Rabbit consume, JDBC fallback non appele")
    void rabbitEmitterPublishesAndSkipsFallback() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        AuditEventEmitter fallback = mock(AuditEventEmitter.class);
        RabbitAuditEventEmitter publisher = new RabbitAuditEventEmitter(rabbit, fallback);

        AuditEventEmitter.AuditEvent event = new AuditEventEmitter.AuditEvent(
                WS_A, USER_A, "SOMETHING_DONE", "user", null,
                Map.of("k", "v"), "corr-1", "svc-test");
        publisher.emit(event);

        verify(rabbit).convertAndSend(
                eq(AuditExchangeConfig.EXCHANGE),
                eq("audit.svc-test.something_done"),
                eq(event));
        verify(fallback, org.mockito.Mockito.never()).emit(any(AuditEventEmitter.AuditEvent.class));
    }

    // ============================== TEST 7 ==============================

    @Test
    @DisplayName("RabbitMQ AmqpException : publisher delegue au fallback JDBC (zero perte)")
    void rabbitFallbackToJdbcOnAmqpException() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        AuditEventEmitter fallback = mock(AuditEventEmitter.class);
        doThrow(new AmqpException("broker down"))
                .when(rabbit).convertAndSend(any(String.class), any(String.class), any(Object.class));

        RabbitAuditEventEmitter publisher = new RabbitAuditEventEmitter(rabbit, fallback);
        AuditEventEmitter.AuditEvent event = new AuditEventEmitter.AuditEvent(
                WS_A, USER_A, "FALLBACK_OK", null, null, Map.of(), null, "svc-test");
        publisher.emit(event);

        verify(fallback).emit(event);
    }

    // ============================== TEST 8 ==============================

    @Test
    @DisplayName("correlation ID : MDC.correlationId propage en colonne dediee correlation_id")
    void correlationIdFromMdcPropagatedToColumn() {
        String corr = "test-corr-" + System.nanoTime();
        MDC.put("correlationId", corr);
        try {
            service.doStandardThing("with-corr");
        } finally {
            MDC.remove("correlationId");
        }

        Map<String, Object> row = findRow("SAMPLE_STANDARD");
        assertThat(row).isNotNull();
        assertThat(row.get("correlation_id")).isEqualTo(corr);
    }

    // ========================= HELPERS =========================

    private void setAuthentication(UUID workspaceId, UUID userId) {
        AuthenticatedUser u = new AuthenticatedUser(userId, workspaceId,
                userId + "@example.com", Role.EMPLOYE);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u, "n/a", List.of()));
    }

    private Map<String, Object> findRow(String action) {
        // Lecture cross-workspace via bypass SuperAdmin, dans une vraie transaction
        // (SET LOCAL inoperant sinon).
        return jdbc.execute((java.sql.Connection conn) -> {
            boolean prev = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (var st = conn.createStatement()) {
                st.execute("SET LOCAL app.audit_bypass = 'true'");
                try (var ps = conn.prepareStatement(
                        "SELECT workspace_id, user_id, action, entity_type, entity_id, " +
                                "       metadata::text AS metadata, correlation_id, source_service, " +
                                "       created_at " +
                                "FROM audit_log WHERE action = ? ORDER BY created_at DESC LIMIT 1")) {
                    ps.setString(1, action);
                    try (var rs = ps.executeQuery()) {
                        Map<String, Object> row = null;
                        if (rs.next()) {
                            row = new java.util.HashMap<>();
                            row.put("workspace_id", rs.getObject("workspace_id"));
                            row.put("user_id", rs.getObject("user_id"));
                            row.put("action", rs.getString("action"));
                            row.put("entity_type", rs.getString("entity_type"));
                            row.put("entity_id", rs.getObject("entity_id"));
                            row.put("metadata", rs.getString("metadata"));
                            row.put("correlation_id", rs.getString("correlation_id"));
                            row.put("source_service", rs.getString("source_service"));
                            row.put("created_at", rs.getObject("created_at"));
                        }
                        conn.commit();
                        return row;
                    }
                }
            } finally {
                conn.setAutoCommit(prev);
            }
        });
    }

    /** Service de demonstration annote pour declencher l'AuditAspect. */
    static class AuditedSampleService {

        @Auditable(action = "SAMPLE_STANDARD")
        public String doStandardThing(String input) {
            return "ok:" + input;
        }

        @Auditable(action = "SAMPLE_SCOPED", resourceType = "widget", resourceIdExpr = "#id")
        public void doScopedThing(UUID id, String payload) {
            // no-op
        }

        @Auditable(action = "SAMPLE_EXPLODE")
        public String doExplodingThing(String why) {
            throw new IllegalStateException(why);
        }
    }
}
