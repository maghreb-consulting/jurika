package ma.jurika.auth.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.EventPublisher;
import ma.jurika.auth.domain.port.SmsSender;
import ma.jurika.common.validation.DnsMxValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

import static org.mockito.Mockito.mock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 3 / TASK 4 — Tests e2e du parcours d'onboarding complet.
 *
 * <p>Couvre cinq scenarios :
 * <ol>
 *     <li>{@code completeOnboardingFlow_withTotp} — happy path : signup -> verify-email
 *         -> 1er login (must change MDP) -> change MDP -> choose TOTP -> setup-2fa
 *         -> confirm 2FA -> recovery codes -> logout -> relogin -> verify-2fa -> /me</li>
 *     <li>{@code smsPhoneVerification_marksPhoneVerified} — flow SMS OTP (PHONE_VERIFICATION)</li>
 *     <li>{@code recoveryCodes_regenerationEmitsEmail} — RG-AU33 : seconde generation
 *         declenche l'email recovery-codes-regenerated (Sprint 3 / TASK 2)</li>
 *     <li>{@code expiredEmailToken_canBeResent} — resend-verification apres expiration</li>
 *     <li>{@code invalidEmailDomain_blocksRegistration} — DnsMxValidator KO -&gt; 400</li>
 * </ol>
 *
 * <p>Infrastructure : PostgreSQL via TestContainers, Rabbit auto-config exclue,
 * EmailSender / SmsSender / EventPublisher / DnsMxValidator mockes, JWT HS256
 * (clef test in-memory pour eviter les fichiers PEM), TaskExecutor synchrone
 * (sinon les ecritures audit_log async pourraient ne pas etre visibles).
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
class OnboardingFlowE2ETest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it")
            .withUsername("jurika_it")
            .withPassword("jurika_it_pwd")
            // Active uuid-ossp + pgcrypto (idem infrastructure/scripts/init-db.sh en prod)
            .withInitScript("testcontainers-init.sql")
            .withReuse(false);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Pas de Redis utilise par auth-service en code, mais l'autoconfig le pique. On laisse.
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "16379");
    }

    /**
     * Rend l'@Async audit synchrone -> les compteurs audit_log sont lisibles
     * juste apres la requete HTTP.
     */
    @TestConfiguration
    static class SyncTaskExecutorConfig {
        @Bean
        @Primary
        public TaskExecutor taskExecutor() {
            return new SyncTaskExecutor();
        }

        // MeterRegistry + BusinessMetrics : depuis Sprint 14 ter, jurika-common
        // ObservabilityAutoConfiguration$BusinessMetricsConfig fournit un fallback
        // SimpleMeterRegistry (@ConditionalOnMissingBean) + le BusinessMetrics qui va
        // avec. Plus besoin de les redefinir ici (sinon BeanDefinitionOverrideException).

        /**
         * RabbitConfig (prod code) cree un RabbitTemplate qui exige une ConnectionFactory.
         * On fournit un mock pour casser la chaine de dependance et eviter le module Spring Rabbit.
         */
        @Bean
        public ConnectionFactory rabbitConnectionFactory() {
            return mock(ConnectionFactory.class);
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    @MockBean private EmailSender emailSender;
    @MockBean private SmsSender smsSender;
    @MockBean private EventPublisher eventPublisher;
    @MockBean private DnsMxValidator dnsMxValidator;

    private static final UUID SUBSCRIPTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void resetMocks() {
        reset(emailSender, smsSender, eventPublisher, dnsMxValidator);
        when(dnsMxValidator.validate(anyString())).thenReturn(new DnsMxValidator.Result(true, "OK"));
    }

    // ============================== TEST 1 ==============================

    @Test
    @DisplayName("Onboarding complet : register -> verify-email -> 1er login (mustChangePassword) "
            + "-> change-password -> choose TOTP -> setup-2fa -> confirm 2FA -> recovery codes "
            + "-> logout -> relogin -> verify-2fa -> /me OK")
    void completeOnboardingFlow_withTotp() throws Exception {
        String email = "karim.it1@example.com";
        String registerPayload = """
                { "workspaceName": "Cabinet IT 1",
                  "contactEmail": "contact.it1@example.com",
                  "subscriptionId": "%s",
                  "firstName": "Karim",
                  "lastName": "Test",
                  "phone": "+212600000001",
                  "email": "%s",
                  "password": "ignored-by-server" }
                """.formatted(SUBSCRIPTION_ID, email);

        MvcResult regResult = mvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json").content(registerPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workspaceCode").exists())
                .andReturn();
        JsonNode reg = json.readTree(regResult.getResponse().getContentAsString());
        String workspaceCode = reg.get("workspaceCode").asText();
        UUID workspaceId = UUID.fromString(reg.get("workspaceId").asText());
        assertThat(workspaceCode).matches("^JUR-[A-Z0-9]{5}$");

        // 2. Capture du "welcome" pour extraire tempPwd + token
        Map<String, Object> vars = captureWelcomeVars(email);
        String tempPassword = (String) vars.get("temporaryPassword");
        String emailToken = extractToken((String) vars.get("verificationUrl"));
        assertThat(tempPassword).isNotBlank().hasSize(12);

        // En DB : workspace PENDING_VERIFICATION, user must_change_password=true
        assertThat(workspaceStatus(workspaceId)).isEqualTo("PENDING_VERIFICATION");
        assertThat(jdbc.queryForObject(
                "SELECT must_change_password FROM users WHERE workspace_id = ?",
                Boolean.class, workspaceId)).isTrue();

        // 3. POST /verify-email
        mvc.perform(post("/api/v1/auth/verify-email")
                        .contentType("application/json")
                        .content("{\"token\":\"" + emailToken + "\"}"))
                .andExpect(status().isOk());
        assertThat(workspaceStatus(workspaceId)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
                "SELECT email_verified_at FROM users WHERE workspace_id = ?",
                Instant.class, workspaceId)).isNotNull();

        // 4. POST /login avec MDP temp
        MvcResult login1 = mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"workspaceCode":"%s","email":"%s","password":"%s"}
                                """.formatted(workspaceCode, email, tempPassword)))
                .andExpect(status().isOk())
                // mustChangePassword n'est pas remonte dans le body par LoginUseCase
                // (le flag voyage uniquement dans le claim JWT 'mcp' -> verifie via le
                // ChangePasswordEnforcer ci-dessous qui renvoie 403).
                .andExpect(jsonPath("$.requires2fa").value(false))
                .andExpect(jsonPath("$.accessToken").isString())
                .andReturn();
        String tempAccessToken = readField(login1, "accessToken");

        // 4b. Tentative POST /setup-2fa -> 403 PASSWORD_CHANGE_REQUIRED (filter)
        mvc.perform(post("/api/v1/auth/setup-2fa")
                        .header("Authorization", "Bearer " + tempAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        // 5. POST /change-password — hotfix 2026-06-04 : renvoie 200 OK + nouveau couple
        // de tokens (mcp=false dans le claim) pour debloquer immediatement /setup-2fa.
        String newPassword = "NewStrong@2026!";
        MvcResult chPwd = mvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + tempAccessToken)
                        .contentType("application/json")
                        .content("""
                                {"oldPassword":"%s","newPassword":"%s","confirmPassword":"%s"}
                                """.formatted(tempPassword, newPassword, newPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn();
        assertThat(jdbc.queryForObject(
                "SELECT must_change_password FROM users WHERE workspace_id = ?",
                Boolean.class, workspaceId)).isFalse();
        verify(emailSender, atLeastOnce()).sendTemplated(
                eq(email), anyString(), eq("password-changed"), anyMap());

        // 5b. Le NOUVEAU token doit immediatement permettre /setup-2fa
        // (= la preuve que le bug "PASSWORD_CHANGE_REQUIRED en boucle" est fixe).
        String postChangeToken = readField(chPwd, "accessToken");
        mvc.perform(post("/api/v1/auth/setup-2fa")
                        .header("Authorization", "Bearer " + postChangeToken))
                .andExpect(status().isOk());

        // 6. Re-login avec nouveau MDP — toujours possible (sanity check) ; on
        // continue le scenario avec ce token "propre" pour rester proche du flux reel.
        MvcResult login2 = mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"workspaceCode":"%s","email":"%s","password":"%s"}
                                """.formatted(workspaceCode, email, newPassword)))
                .andExpect(status().isOk())
                .andReturn();
        String cleanAccessToken = readField(login2, "accessToken");

        // 7. POST /2fa/choose-method TOTP
        mvc.perform(post("/api/v1/auth/2fa/choose-method")
                        .header("Authorization", "Bearer " + cleanAccessToken)
                        .contentType("application/json")
                        .content("{\"method\":\"TOTP\"}"))
                .andExpect(status().isNoContent());

        // 8. POST /setup-2fa -> recupere secret + QR
        MvcResult setupRes = mvc.perform(post("/api/v1/auth/setup-2fa")
                        .header("Authorization", "Bearer " + cleanAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").isString())
                .andExpect(jsonPath("$.qrCodePngBase64").isString())
                .andReturn();
        String totpSecret = readField(setupRes, "secret");

        // 9. POST /setup-2fa/confirm — hotfix 2026-06-04 : renvoie 200 OK + nouveau
        // couple de tokens (r2s=false dans le claim) pour debloquer immediatement
        // tous les endpoints metier non whitelistes par Setup2faRequiredEnforcer.
        int currentCode = new GoogleAuthenticator().getTotpPassword(totpSecret);
        MvcResult conf2fa = mvc.perform(post("/api/v1/auth/setup-2fa/confirm")
                        .header("Authorization", "Bearer " + cleanAccessToken)
                        .contentType("application/json")
                        .content("{\"code\":" + currentCode + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn();
        verify(emailSender, atLeastOnce()).sendTemplated(
                eq(email), anyString(), eq("2fa-enabled"), anyMap());
        // Le nouveau token (r2s=false) doit immediatement permettre /me
        // sans declencher Setup2faRequiredEnforcer.
        String postSetupToken = readField(conf2fa, "accessToken");
        mvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + postSetupToken))
                .andExpect(status().isOk());

        // 10. POST /2fa/recovery-codes/generate -> 10 codes
        mvc.perform(post("/api/v1/auth/2fa/recovery-codes/generate")
                        .header("Authorization", "Bearer " + cleanAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codes").isArray())
                .andExpect(jsonPath("$.codes.length()").value(10));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM recovery_codes WHERE workspace_id = ?",
                Integer.class, workspaceId)).isEqualTo(10);

        // 11. POST /logout
        mvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + cleanAccessToken))
                .andExpect(status().isNoContent());

        // 12. POST /login -> requires2fa=true
        MvcResult login3 = mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"workspaceCode":"%s","email":"%s","password":"%s"}
                                """.formatted(workspaceCode, email, newPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requires2fa").value(true))
                .andExpect(jsonPath("$.twofaMethod").value("TOTP"))
                .andReturn();
        JsonNode l3 = json.readTree(login3.getResponse().getContentAsString());
        String userId = l3.get("userId").asText();
        String wsId = l3.get("workspaceId").asText();

        // 13. POST /verify-2fa
        int verifyCode = new GoogleAuthenticator().getTotpPassword(totpSecret);
        MvcResult v2fa = mvc.perform(post("/api/v1/auth/verify-2fa")
                        .contentType("application/json")
                        .content("""
                                {"userId":"%s","workspaceId":"%s","code":%d}
                                """.formatted(userId, wsId, verifyCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andReturn();
        String finalAccess = readField(v2fa, "accessToken");

        // 14. GET /me OK
        mvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + finalAccess))
                .andExpect(status().isOk());

        // 15. Audit log : >= 6 actions cles
        Long auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE workspace_id = ? AND action IN " +
                        "('WORKSPACE_REGISTER','EMAIL_VERIFIED','PASSWORD_CHANGED','2FA_ENABLED',"
                        + "'LOGIN_SUCCESS','LOGIN_SUCCESS_2FA','LOGOUT','LOGIN_2FA_REQUIRED')",
                Long.class, workspaceId);
        assertThat(auditCount).as("au moins 6 actions auditees").isGreaterThanOrEqualTo(6L);

        // Events Rabbit publies (mockes)
        verify(eventPublisher, atLeast(1)).publishWorkspaceCreated(eq(workspaceId), eq(workspaceCode));
        verify(eventPublisher, atLeastOnce()).publishUser2faEnabled(eq(workspaceId), any(UUID.class));
        verify(eventPublisher, atLeast(2)).publishUserLoggedIn(eq(workspaceId), any(UUID.class));
    }

    // ============================== TEST 2 ==============================

    @Test
    @DisplayName("SMS PHONE_VERIFICATION : choose SMS + send + verify capture le code du SmsSender mock")
    void smsPhoneVerification_marksPhoneVerified() throws Exception {
        OnboardingTestUser u = createActiveUser("smsuser1@example.com", "+212600000002");

        // 1. choose SMS
        mvc.perform(post("/api/v1/auth/2fa/choose-method")
                        .header("Authorization", "Bearer " + u.accessToken)
                        .contentType("application/json").content("{\"method\":\"SMS\"}"))
                .andExpect(status().isNoContent());

        // 2. send SMS OTP
        mvc.perform(post("/api/v1/auth/2fa/sms/send")
                        .header("Authorization", "Bearer " + u.accessToken)
                        .contentType("application/json").content("{\"purpose\":\"PHONE_VERIFICATION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedPhone").isString());

        // 3. Capture body envoye, extraire 6 chiffres
        ArgumentCaptor<String> smsBody = ArgumentCaptor.forClass(String.class);
        verify(smsSender, atLeastOnce()).send(eq("+212600000002"), smsBody.capture());
        String code = smsBody.getValue().replaceAll(".*?(\\d{6}).*", "$1");
        assertThat(code).matches("\\d{6}");

        // 4. verify SMS OTP
        mvc.perform(post("/api/v1/auth/2fa/sms/verify")
                        .header("Authorization", "Bearer " + u.accessToken)
                        .contentType("application/json")
                        .content("{\"purpose\":\"PHONE_VERIFICATION\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isNoContent());

        // 5. DB : phone_verified_at != null
        assertThat(jdbc.queryForObject(
                "SELECT phone_verified_at FROM users WHERE id = ?", Instant.class, u.userId))
                .isNotNull();
    }

    // ============================== TEST 3 ==============================

    @Test
    @DisplayName("Recovery codes : 2eme generation envoie l'email recovery-codes-regenerated (RG-AU33)")
    void recoveryCodes_regenerationEmitsEmail() throws Exception {
        OnboardingTestUser u = createActiveUser("regen1@example.com", "+212600000003");

        // 1ere generation
        mvc.perform(post("/api/v1/auth/2fa/recovery-codes/generate")
                        .header("Authorization", "Bearer " + u.accessToken))
                .andExpect(status().isOk());

        // Reset le mock email apres premiere generation
        reset(emailSender);

        // 2eme generation
        mvc.perform(post("/api/v1/auth/2fa/recovery-codes/generate")
                        .header("Authorization", "Bearer " + u.accessToken))
                .andExpect(status().isOk());

        verify(emailSender, atLeastOnce()).sendTemplated(
                eq("regen1@example.com"), anyString(),
                eq("recovery-codes-regenerated"), anyMap());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE workspace_id = ? AND action = 'RECOVERY_CODES_REGENERATED'",
                Integer.class, u.workspaceId)).isGreaterThanOrEqualTo(1);
    }

    // ============================== TEST 4 ==============================

    @Test
    @DisplayName("Token email expire : verify-email -> 400, resend-verification OK")
    void expiredEmailToken_canBeResent() throws Exception {
        String email = "expired1@example.com";
        String registerPayload = """
                { "workspaceName": "Cabinet IT 4",
                  "contactEmail": "contact.it4@example.com",
                  "subscriptionId": "%s",
                  "firstName": "Aicha", "lastName": "Test",
                  "phone": "+212600000004",
                  "email": "%s", "password": "ignored-by-server-v2-strict" }
                """.formatted(SUBSCRIPTION_ID, email);
        MvcResult reg = mvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json").content(registerPayload))
                .andExpect(status().isCreated()).andReturn();
        String workspaceCode = readField(reg, "workspaceCode");

        Map<String, Object> vars = captureWelcomeVars(email);
        String token1 = extractToken((String) vars.get("verificationUrl"));

        // Forcer expiration cote DB
        int updated = jdbc.update(
                "UPDATE email_verification_tokens SET expires_at = NOW() - INTERVAL '1 day' WHERE email = ?",
                email);
        assertThat(updated).isGreaterThanOrEqualTo(1);

        // verify avec token expire -> 400 (ValidationException EMAIL_VERIFICATION_EXPIRED)
        mvc.perform(post("/api/v1/auth/verify-email")
                        .contentType("application/json")
                        .content("{\"token\":\"" + token1 + "\"}"))
                .andExpect(status().isBadRequest());

        // resend
        reset(emailSender);
        mvc.perform(post("/api/v1/auth/resend-verification")
                        .contentType("application/json")
                        .content("{\"workspaceCode\":\"" + workspaceCode + "\",\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());

        // Un nouvel email est parti
        verify(emailSender, atLeastOnce()).sendTemplated(eq(email), anyString(), anyString(), anyMap());
    }

    // ============================== TEST 5 ==============================

    @Test
    @DisplayName("DnsMxValidator KO -> /register echoue avec 400 et message EMAIL_DOMAIN_NOT_FOUND")
    void invalidEmailDomain_blocksRegistration() throws Exception {
        when(dnsMxValidator.validate(anyString()))
                .thenReturn(new DnsMxValidator.Result(false, "EMAIL_DOMAIN_NOT_FOUND:bogus.invalid"));

        String registerPayload = """
                { "workspaceName": "Cabinet IT 5",
                  "contactEmail": "contact.it5@bogus.invalid",
                  "subscriptionId": "%s",
                  "firstName": "Test", "lastName": "DnsKo",
                  "phone": "+212600000005",
                  "email": "nope@bogus.invalid", "password": "ignored-by-server-v2-strict" }
                """.formatted(SUBSCRIPTION_ID);

        mvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json").content(registerPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("EMAIL_DOMAIN_NOT_FOUND")));
    }

    // ========================= HELPERS =========================

    record OnboardingTestUser(UUID userId, UUID workspaceId, String workspaceCode,
                              String email, String accessToken) {}

    OnboardingTestUser createActiveUser(String email, String phone) throws Exception {
        String suffix = email.split("@")[0];
        String payload = """
                { "workspaceName": "Cabinet %s",
                  "contactEmail": "contact-%s@example.com",
                  "subscriptionId": "%s",
                  "firstName": "User", "lastName": "%s",
                  "phone": "%s",
                  "email": "%s", "password": "ignored-by-server-v2-strict" }
                """.formatted(suffix, suffix, SUBSCRIPTION_ID, suffix, phone, email);

        MvcResult reg = mvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json").content(payload))
                .andExpect(status().isCreated()).andReturn();
        JsonNode regJson = json.readTree(reg.getResponse().getContentAsString());
        String workspaceCode = regJson.get("workspaceCode").asText();
        UUID workspaceId = UUID.fromString(regJson.get("workspaceId").asText());
        UUID userId = UUID.fromString(regJson.get("userId").asText());

        Map<String, Object> vars = captureWelcomeVars(email);
        String tempPwd = (String) vars.get("temporaryPassword");
        String token = extractToken((String) vars.get("verificationUrl"));

        mvc.perform(post("/api/v1/auth/verify-email")
                        .contentType("application/json").content("{\"token\":\"" + token + "\"}"))
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
                .andExpect(status().isOk()); // hotfix 2026-06-04 : 200 + tokens (au lieu de 204)

        MvcResult login2 = mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"workspaceCode":"%s","email":"%s","password":"%s"}
                                """.formatted(workspaceCode, email, newPwd)))
                .andExpect(status().isOk()).andReturn();
        String clean = readField(login2, "accessToken");

        return new OnboardingTestUser(userId, workspaceId, workspaceCode, email, clean);
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

    private String workspaceStatus(UUID workspaceId) {
        return jdbc.queryForObject(
                "SELECT status FROM workspaces WHERE id = ?", String.class, workspaceId);
    }

    private String readField(MvcResult result, String field) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get(field).asText();
    }
}
