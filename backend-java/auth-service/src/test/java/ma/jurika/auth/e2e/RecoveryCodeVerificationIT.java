package ma.jurika.auth.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import ma.jurika.auth.application.GenerateRecoveryCodesUseCase;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.EventPublisher;
import ma.jurika.auth.domain.port.SmsSender;
import ma.jurika.common.observability.BusinessMetrics;
import ma.jurika.common.validation.DnsMxValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 14 bis / TASK B4 — RecoveryCodeVerificationIT.
 *
 * <p>Resorbe la dette D-S3-03 (endpoint {@code POST /api/v1/auth/verify-recovery-code}
 * manquant) + valide les regles RG-AU41 (single-use) et RG-AU42 (rate limit 5/15min).
 *
 * <p>5 cas couvrant tous les chemins critiques :
 * <ol>
 *   <li><b>Succes</b> : code valide -> 200 + tokens + audit {@code RECOVERY_CODE_USED}
 *       + email {@code recovery-code-used} + marquage {@code used_at} en DB</li>
 *   <li><b>Code inconnu</b> : code aleatoire -> 401 + audit
 *       {@code RECOVERY_CODE_FAILED} (reason CODE_MISMATCH)</li>
 *   <li><b>Code deja utilise</b> : utiliser le meme code 2x -> 2eme 401 (RG-AU41)</li>
 *   <li><b>Rate limit</b> : 6 echecs consecutifs -> dernier 429 (RG-AU42)</li>
 *   <li><b>Email inconnu</b> : workspace OK mais email absent -> 401 + audit
 *       {@code RECOVERY_CODE_FAILED} (reason USER_UNKNOWN), aucune fuite d'enum</li>
 * </ol>
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
                "jurika.auth.recovery-code.max-attempts=5",
                "jurika.auth.recovery-code.window-minutes=15"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("it")
class RecoveryCodeVerificationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_recovery")
            .withUsername("jurika_it")
            .withPassword("jurika_it_pwd")
            .withInitScript("testcontainers-init.sql")
            .withReuse(false);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "16379");
    }

    @TestConfiguration
    static class SyncWiring {
        @Bean @Primary public TaskExecutor taskExecutor() { return new SyncTaskExecutor(); }
        @Bean public MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
        @Bean public BusinessMetrics businessMetrics(MeterRegistry r) { return new BusinessMetrics(r); }
        @Bean public ConnectionFactory rabbitConnectionFactory() { return mock(ConnectionFactory.class); }
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private GenerateRecoveryCodesUseCase generateRecoveryCodesUseCase;

    @MockBean private EmailSender emailSender;
    @MockBean private SmsSender smsSender;
    @MockBean private EventPublisher eventPublisher;
    @MockBean private DnsMxValidator dnsMxValidator;

    private static final UUID STARTER_PLAN = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private int suffixCounter = 0;

    private String nextSuffix() {
        return "rec" + (++suffixCounter) + "_" + System.nanoTime();
    }

    // ============================== TEST 1 ==============================

    @Test
    @DisplayName("Succes : code valide -> 200 + tokens + audit RECOVERY_CODE_USED + email + used_at non null")
    void verifyRecoveryCode_success_issuesTokensAndMarksUsed() throws Exception {
        ActiveUser u = createActiveUserWithRecoveryCodes();
        String code = u.plainCodes.get(0);

        MvcResult res = mvc.perform(post("/api/v1/auth/verify-recovery-code")
                        .contentType("application/json")
                        .content(payload(u.workspaceCode, u.email, code)))
                .andExpect(status().isOk()).andReturn();

        JsonNode body = json.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.get("refreshToken").asText()).isNotBlank();
        assertThat(body.get("userId").asText()).isEqualTo(u.userId.toString());

        Map<String, Object> row = findAuditRow(u.workspaceId, "RECOVERY_CODE_USED");
        assertThat(row).isNotNull();
        assertThat(row.get("user_id")).isEqualTo(u.userId);
        String metadata = (String) row.get("metadata");
        assertThat(metadata).contains("\"remaining\"");

        Long usedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM recovery_codes WHERE user_id = ? AND used_at IS NOT NULL",
                Long.class, u.userId);
        assertThat(usedCount).isEqualTo(1L);

        verify(emailSender, atLeastOnce()).sendTemplated(
                eq(u.email), anyString(), eq("recovery-code-used"), anyMap());
    }

    // ============================== TEST 2 ==============================

    @Test
    @DisplayName("Code inconnu : aleatoire valide en format -> 401 + audit RECOVERY_CODE_FAILED (CODE_MISMATCH)")
    void verifyRecoveryCode_unknownCode_returns401AndAuditsMismatch() throws Exception {
        ActiveUser u = createActiveUserWithRecoveryCodes();

        // Code valide en format (16 chars alphanum + tirets) mais jamais genere.
        mvc.perform(post("/api/v1/auth/verify-recovery-code")
                        .contentType("application/json")
                        .content(payload(u.workspaceCode, u.email, "ZZZZ-ZZZZ-ZZZZ-ZZZZ")))
                .andExpect(status().isUnauthorized());

        Map<String, Object> row = findAuditRow(u.workspaceId, "RECOVERY_CODE_FAILED");
        assertThat(row).isNotNull();
        assertThat(row.get("user_id")).isEqualTo(u.userId);
        String metadata = (String) row.get("metadata");
        assertThat(metadata).contains("CODE_MISMATCH");

        // Aucun code n'a ete consomme.
        Long usedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM recovery_codes WHERE user_id = ? AND used_at IS NOT NULL",
                Long.class, u.userId);
        assertThat(usedCount).isZero();
    }

    // ============================== TEST 3 ==============================

    @Test
    @DisplayName("Code deja utilise (RG-AU41) : meme code 2x -> 2eme 401 + 1 seul used_at en DB")
    void verifyRecoveryCode_alreadyUsed_returns401() throws Exception {
        ActiveUser u = createActiveUserWithRecoveryCodes();
        String code = u.plainCodes.get(0);

        // 1ere utilisation : succes
        mvc.perform(post("/api/v1/auth/verify-recovery-code")
                        .contentType("application/json")
                        .content(payload(u.workspaceCode, u.email, code)))
                .andExpect(status().isOk());

        // 2eme utilisation : single-use viole -> 401
        mvc.perform(post("/api/v1/auth/verify-recovery-code")
                        .contentType("application/json")
                        .content(payload(u.workspaceCode, u.email, code)))
                .andExpect(status().isUnauthorized());

        Long usedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM recovery_codes WHERE user_id = ? AND used_at IS NOT NULL",
                Long.class, u.userId);
        assertThat(usedCount).isEqualTo(1L);

        // Le 2eme echec a bien audit en FAILED.
        Long failCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE workspace_id = ? AND user_id = ? AND action = 'RECOVERY_CODE_FAILED'",
                Long.class, u.workspaceId, u.userId);
        assertThat(failCount).isGreaterThanOrEqualTo(1L);
    }

    // ============================== TEST 4 ==============================

    @Test
    @DisplayName("Rate limit (RG-AU42) : 5 echecs successifs OK, 6eme -> 429 TOO_MANY_REQUESTS")
    void verifyRecoveryCode_rateLimit_returns429AfterFive() throws Exception {
        ActiveUser u = createActiveUserWithRecoveryCodes();
        String bogus = "AAAA-AAAA-AAAA-AAAA";

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/verify-recovery-code")
                            .contentType("application/json")
                            .content(payload(u.workspaceCode, u.email, bogus)))
                    .andExpect(status().isUnauthorized());
        }

        // 6eme : rate limited
        mvc.perform(post("/api/v1/auth/verify-recovery-code")
                        .contentType("application/json")
                        .content(payload(u.workspaceCode, u.email, bogus)))
                .andExpect(status().isTooManyRequests());

        // Audit RATE_LIMITED present
        Long rateRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE workspace_id = ? AND action = 'RECOVERY_CODE_FAILED' AND metadata::text LIKE '%RATE_LIMITED%'",
                Long.class, u.workspaceId);
        assertThat(rateRows).isGreaterThanOrEqualTo(1L);
    }

    // ============================== TEST 5 ==============================

    @Test
    @DisplayName("Email inconnu : workspace OK mais email absent -> 401 + audit USER_UNKNOWN (pas de fuite d'enum)")
    void verifyRecoveryCode_unknownEmail_returns401WithoutLeakingEnum() throws Exception {
        // On a besoin d'un workspace ACTIF (avec un user verifie) pour ne pas etre bloque
        // par le workspace inconnu. On reutilise createActiveUserWithRecoveryCodes mais
        // on tape avec un email inexistant.
        ActiveUser u = createActiveUserWithRecoveryCodes();
        String fakeEmail = "ghost_" + System.nanoTime() + "@example.com";

        mvc.perform(post("/api/v1/auth/verify-recovery-code")
                        .contentType("application/json")
                        .content(payload(u.workspaceCode, fakeEmail, "BBBB-BBBB-BBBB-BBBB")))
                .andExpect(status().isUnauthorized());

        // Audit ecrit cote workspace de la cible (user_id = null car user inconnu).
        Long unknownRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE workspace_id = ? AND action = 'RECOVERY_CODE_FAILED' AND metadata::text LIKE '%USER_UNKNOWN%'",
                Long.class, u.workspaceId);
        assertThat(unknownRows).isGreaterThanOrEqualTo(1L);
    }

    // ========================= HELPERS =========================

    record ActiveUser(UUID userId, UUID workspaceId, String workspaceCode,
                      String email, String accessToken, List<String> plainCodes) {}

    /** Cree un user actif + genere 10 recovery codes et retourne le clair pour les tests. */
    private ActiveUser createActiveUserWithRecoveryCodes() throws Exception {
        ActiveBase b = createActiveBase();

        GenerateRecoveryCodesUseCase.Result r = generateRecoveryCodesUseCase.execute(
                new GenerateRecoveryCodesUseCase.Command(b.userId, b.workspaceId,
                        "127.0.0.1", "junit-test"));
        assertThat(r.plainCodes()).hasSize(10);

        return new ActiveUser(b.userId, b.workspaceId, b.workspaceCode, b.email, b.accessToken,
                r.plainCodes());
    }

    private record ActiveBase(UUID userId, UUID workspaceId, String workspaceCode,
                              String email, String accessToken) {}

    private ActiveBase createActiveBase() throws Exception {
        when(dnsMxValidator.validate(anyString()))
                .thenReturn(new DnsMxValidator.Result(true, "OK"));

        String suffix = nextSuffix();
        String email = suffix + "@example.com";

        MvcResult reg = mvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(registerPayload(suffix, email)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode regJson = json.readTree(reg.getResponse().getContentAsString());
        String workspaceCode = regJson.get("workspaceCode").asText();
        UUID workspaceId = UUID.fromString(regJson.get("workspaceId").asText());
        UUID userId = UUID.fromString(regJson.get("userId").asText());

        Map<String, Object> vars = captureWelcomeVars(email);
        String tempPwd = (String) vars.get("temporaryPassword");
        String token = extractToken((String) vars.get("verificationUrl"));

        mvc.perform(post("/api/v1/auth/verify-email")
                        .contentType("application/json")
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk());

        MvcResult login1 = mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"workspaceCode":"%s","email":"%s","password":"%s"}
                                """.formatted(workspaceCode, email, tempPwd)))
                .andExpect(status().isOk()).andReturn();
        String tempAccess = readField(login1, "accessToken");

        String newPwd = "NewStrong@2026!";
        mvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + tempAccess)
                        .contentType("application/json")
                        .content("""
                                {"oldPassword":"%s","newPassword":"%s","confirmPassword":"%s"}
                                """.formatted(tempPwd, newPwd, newPwd)))
                .andExpect(status().isNoContent());

        MvcResult login2 = mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"workspaceCode":"%s","email":"%s","password":"%s"}
                                """.formatted(workspaceCode, email, newPwd)))
                .andExpect(status().isOk()).andReturn();
        String clean = readField(login2, "accessToken");

        return new ActiveBase(userId, workspaceId, workspaceCode, email, clean);
    }

    private String payload(String workspaceCode, String email, String code) {
        return """
                {"workspaceCode":"%s","email":"%s","code":"%s"}
                """.formatted(workspaceCode, email, code);
    }

    private String registerPayload(String suffix, String email) {
        return """
                { "workspaceName": "Cabinet %s",
                  "contactEmail": "contact-%s@example.com",
                  "subscriptionId": "%s",
                  "firstName": "User", "lastName": "%s",
                  "phone": "+212600%06d",
                  "email": "%s", "password": "ignored-by-server-v2-strict" }
                """.formatted(suffix, suffix, STARTER_PLAN, suffix,
                        suffixCounter % 1_000_000, email);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> captureWelcomeVars(String email) {
        ArgumentCaptor<Map<String, Object>> captor =
                (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
        verify(emailSender, atLeastOnce()).sendTemplated(
                eq(email), anyString(), eq("welcome"), captor.capture());
        return captor.getValue();
    }

    private String extractToken(String verificationUrl) {
        int i = verificationUrl.indexOf("?token=");
        if (i < 0) throw new IllegalStateException("No ?token= in URL : " + verificationUrl);
        return verificationUrl.substring(i + "?token=".length());
    }

    private String readField(MvcResult result, String field) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get(field).asText();
    }

    private Map<String, Object> findAuditRow(UUID workspaceId, String action) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT workspace_id, user_id, action, entity_type, entity_id, " +
                        "       metadata::text AS metadata, ip_address, user_agent, created_at " +
                        "FROM audit_log WHERE workspace_id = ? AND action = ? " +
                        "ORDER BY created_at DESC LIMIT 1",
                workspaceId, action);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
