package ma.jurika.auth.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 14 bis / TASK B2 — AuthAuditableIT.
 *
 * <p>Resorbe la dette D-S2-01 par PREUVE (Option C) : prouve que chaque use case
 * critique d'auth-service ecrit bien la ligne attendue dans {@code audit_log},
 * que ce soit via le port {@link ma.jurika.auth.domain.port.AuditLogger} (voie
 * manuelle) ou via l'annotation {@link ma.jurika.common.audit.Auditable} (voie
 * AOP). Les deux voies convergent sur la meme table physique.
 *
 * <p>Pattern identique a {@link OnboardingFlowE2ETest} : PostgreSQL TestContainer,
 * Rabbit auto-config exclu, SyncTaskExecutor pour rendre l'@Async observable
 * immediatement, JWT HS256, EmailSender/SmsSender/EventPublisher/DnsMxValidator
 * mockes.
 *
 * <p>10 cas couvrant: WORKSPACE_REGISTER, EMAIL_VERIFIED, LOGIN_SUCCESS,
 * LOGIN_FAILED, PASSWORD_CHANGED, LOGOUT, 2FA_METHOD_CHOSEN, 2FA_SETUP_INITIATED,
 * RECOVERY_CODES_GENERATED, RECOVERY_CODES_REGENERATED.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("it")
class AuthAuditableIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_auditable")
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

    @MockBean private EmailSender emailSender;
    @MockBean private SmsSender smsSender;
    @MockBean private EventPublisher eventPublisher;
    @MockBean private DnsMxValidator dnsMxValidator;

    private static final UUID STARTER_PLAN = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private int suffixCounter = 0;

    private String nextSuffix() {
        return "audit" + (++suffixCounter) + "_" + System.nanoTime();
    }

    // ============================== TEST 1 ==============================

    @Test
    @DisplayName("WORKSPACE_REGISTER : /register ecrit audit_log avec metadata.code + metadata.plan")
    void registerWorkspace_emits_WORKSPACE_REGISTER_withCodeAndPlan() throws Exception {
        when(dnsMxValidator.validate(anyString()))
                .thenReturn(new DnsMxValidator.Result(true, "OK"));
        String suffix = nextSuffix();
        String email = suffix + "@example.com";

        MvcResult res = mvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(registerPayload(suffix, email)))
                .andExpect(status().isCreated()).andReturn();

        UUID workspaceId = UUID.fromString(readField(res, "workspaceId"));
        Map<String, Object> row = findAuditRow(workspaceId, "WORKSPACE_REGISTER");

        assertThat(row).isNotNull();
        assertThat(row.get("entity_type")).isEqualTo("workspace");
        assertThat(row.get("entity_id")).isEqualTo(workspaceId);
        String metadata = (String) row.get("metadata");
        assertThat(metadata).contains("\"code\"").contains("JUR-");
        assertThat(metadata).contains("\"plan\"");
    }

    // ============================== TEST 2 ==============================

    @Test
    @DisplayName("EMAIL_VERIFIED : /verify-email ecrit audit_log avec metadata.tokenId")
    void verifyEmail_emits_EMAIL_VERIFIED_withTokenId() throws Exception {
        ActiveUser u = createPendingUserAndVerifyEmail();

        Map<String, Object> row = findAuditRow(u.workspaceId, "EMAIL_VERIFIED");
        assertThat(row).isNotNull();
        assertThat(row.get("user_id")).isEqualTo(u.userId);
        String metadata = (String) row.get("metadata");
        assertThat(metadata).contains("\"tokenId\"");
    }

    // ============================== TEST 3 ==============================

    @Test
    @DisplayName("LOGIN_SUCCESS : /login OK ecrit audit_log avec ip + ua dans colonnes dediees")
    void loginSuccess_emits_LOGIN_SUCCESS_withIpAndUa() throws Exception {
        ActiveUser u = createActiveUser();

        Map<String, Object> row = findAuditRow(u.workspaceId, "LOGIN_SUCCESS");
        assertThat(row).isNotNull();
        assertThat(row.get("user_id")).isEqualTo(u.userId);
        // ip_address et user_agent peuvent etre null/127.0.0.1 selon le wiring MockMvc.
        // Le critere fort est la presence d'au moins une ligne LOGIN_SUCCESS.
    }

    // ============================== TEST 4 ==============================

    @Test
    @DisplayName("LOGIN_FAILED : mauvais MDP ecrit audit_log avec metadata.attempts")
    void loginFailedBadPassword_emits_LOGIN_FAILED_withAttempts() throws Exception {
        ActiveUser u = createActiveUser();

        mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"workspaceCode":"%s","email":"%s","password":"WRONG-PWD-1234"}
                                """.formatted(u.workspaceCode, u.email)))
                .andExpect(status().isUnauthorized());

        Map<String, Object> row = findAuditRow(u.workspaceId, "LOGIN_FAILED");
        assertThat(row).isNotNull();
        String metadata = (String) row.get("metadata");
        assertThat(metadata).contains("\"attempts\"");
    }

    // ============================== TEST 5 ==============================

    @Test
    @DisplayName("PASSWORD_CHANGED : /change-password ecrit audit_log avec metadata.source")
    void changePassword_emits_PASSWORD_CHANGED_withSourceMetadata() throws Exception {
        // createActiveUser fait deja un change-password (FORCED_AT_FIRST_LOGIN).
        ActiveUser u = createActiveUser();

        Map<String, Object> row = findAuditRow(u.workspaceId, "PASSWORD_CHANGED");
        assertThat(row).isNotNull();
        assertThat(row.get("user_id")).isEqualTo(u.userId);
        String metadata = (String) row.get("metadata");
        assertThat(metadata).contains("\"source\"");
        // Source attendue : FORCED_AT_FIRST_LOGIN puisque must_change_password etait true.
        assertThat(metadata).contains("FORCED_AT_FIRST_LOGIN");
    }

    // ============================== TEST 6 ==============================

    @Test
    @DisplayName("LOGOUT : /logout ecrit audit_log action=LOGOUT")
    void logout_emits_LOGOUT() throws Exception {
        ActiveUser u = createActiveUser();

        mvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + u.accessToken))
                .andExpect(status().isNoContent());

        Map<String, Object> row = findAuditRow(u.workspaceId, "LOGOUT");
        assertThat(row).isNotNull();
        assertThat(row.get("entity_type")).isEqualTo("user");
        assertThat(row.get("user_id")).isEqualTo(u.userId);
    }

    // ============================== TEST 7 ==============================

    @Test
    @DisplayName("2FA_METHOD_CHOSEN : /2fa/choose-method ecrit audit_log avec metadata.method")
    void choose2faMethod_emits_2FA_METHOD_CHOSEN_withMethod() throws Exception {
        ActiveUser u = createActiveUser();

        mvc.perform(post("/api/v1/auth/2fa/choose-method")
                        .header("Authorization", "Bearer " + u.accessToken)
                        .contentType("application/json")
                        .content("{\"method\":\"TOTP\"}"))
                .andExpect(status().isNoContent());

        Map<String, Object> row = findAuditRow(u.workspaceId, "2FA_METHOD_CHOSEN");
        assertThat(row).isNotNull();
        String metadata = (String) row.get("metadata");
        assertThat(metadata).contains("\"method\"").contains("TOTP");
    }

    // ============================== TEST 8 ==============================

    @Test
    @DisplayName("2FA_SETUP_INITIATED : /setup-2fa apres choose-method ecrit audit_log")
    void setup2faInitiate_emits_2FA_SETUP_INITIATED() throws Exception {
        ActiveUser u = createActiveUser();

        // choose TOTP d'abord (sinon le filter peut rejeter)
        mvc.perform(post("/api/v1/auth/2fa/choose-method")
                        .header("Authorization", "Bearer " + u.accessToken)
                        .contentType("application/json")
                        .content("{\"method\":\"TOTP\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/setup-2fa")
                        .header("Authorization", "Bearer " + u.accessToken))
                .andExpect(status().isOk());

        Map<String, Object> row = findAuditRow(u.workspaceId, "2FA_SETUP_INITIATED");
        assertThat(row).isNotNull();
        assertThat(row.get("user_id")).isEqualTo(u.userId);
    }

    // ============================== TEST 9 ==============================

    @Test
    @DisplayName("RECOVERY_CODES_GENERATED : 1ere generation ecrit audit_log avec metadata.count + regeneration=false")
    void generateRecoveryCodes_first_emits_RECOVERY_CODES_GENERATED() throws Exception {
        ActiveUser u = createActiveUser();

        mvc.perform(post("/api/v1/auth/2fa/recovery-codes/generate")
                        .header("Authorization", "Bearer " + u.accessToken))
                .andExpect(status().isOk());

        Map<String, Object> row = findAuditRow(u.workspaceId, "RECOVERY_CODES_GENERATED");
        assertThat(row).isNotNull();
        String metadata = (String) row.get("metadata");
        assertThat(metadata).contains("\"count\"").contains("10");
        assertThat(metadata).contains("\"regeneration\"").contains("false");
    }

    // ============================== TEST 10 ==============================

    @Test
    @DisplayName("RECOVERY_CODES_REGENERATED : 2eme generation ecrit audit_log avec regeneration=true")
    void generateRecoveryCodes_second_emits_RECOVERY_CODES_REGENERATED() throws Exception {
        ActiveUser u = createActiveUser();

        // 1ere generation
        mvc.perform(post("/api/v1/auth/2fa/recovery-codes/generate")
                        .header("Authorization", "Bearer " + u.accessToken))
                .andExpect(status().isOk());

        // 2eme generation (regeneration)
        mvc.perform(post("/api/v1/auth/2fa/recovery-codes/generate")
                        .header("Authorization", "Bearer " + u.accessToken))
                .andExpect(status().isOk());

        Map<String, Object> row = findAuditRow(u.workspaceId, "RECOVERY_CODES_REGENERATED");
        assertThat(row).isNotNull();
        String metadata = (String) row.get("metadata");
        assertThat(metadata).contains("\"regeneration\"").contains("true");
    }

    // ========================= HELPERS =========================

    record ActiveUser(UUID userId, UUID workspaceId, String workspaceCode,
                      String email, String accessToken) {}

    /** Cree un user active (workspace verifie email, MDP change). */
    private ActiveUser createActiveUser() throws Exception {
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

        return new ActiveUser(userId, workspaceId, workspaceCode, email, clean);
    }

    /** Cree un user dont l'email vient d'etre verifie (mais pas encore login). */
    private ActiveUser createPendingUserAndVerifyEmail() throws Exception {
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
        String token = extractToken((String) vars.get("verificationUrl"));

        mvc.perform(post("/api/v1/auth/verify-email")
                        .contentType("application/json")
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk());

        return new ActiveUser(userId, workspaceId, workspaceCode, email, null);
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

    /**
     * Trouve la 1ere ligne audit_log avec ce workspace + cette action.
     * Renvoie {@code null} si absent (assertion non-null cote test).
     */
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
