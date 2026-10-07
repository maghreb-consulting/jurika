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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
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
 * Lot L0, etape E13a : chemins PUBLICS d'auth-service en role d'execution
 * jurika_app (RLS active, garde « hors transaction »), non couverts par les
 * autres tests de bout en bout : controle du code workspace, defi SMS de
 * connexion, renouvellement de session, reinitialisation du mot de passe,
 * inscription d'un cabinet. Le workspace est pose avant la transaction par
 * ContexteWorkspacePublic (fonctions SECURITY DEFINER d'auth V34).
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
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class CheminsPublicsJurikaAppIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_publics")
            .withUsername("jurika_it")
            .withPassword("jurika_it_pwd")
            // Active uuid-ossp + pgcrypto (idem infrastructure/scripts/init-db.sh en prod)
            .withInitScript("testcontainers-init.sql")
            .withReuse(false);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        // Lot L0 : l'application tourne en role d'execution jurika_app (non
        // proprietaire, NOSUPERUSER, NOBYPASSRLS : la RLS s'applique) ; Flyway
        // migre avec le proprietaire. Comme en production apres la bascule.
        registry.add("spring.datasource.username", () -> "jurika_app");
        registry.add("spring.datasource.password", () -> "jurika_app_it");
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        // Pas de Redis utilise par auth-service en code, mais l'autoconfig le pique. On laisse.
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "16379");
    }

    /**
     * Rend l'@Async audit synchrone -> les compteurs audit_log sont lisibles
     * juste apres la requete HTTP.
     */
    /**
     * Lot L0 (E10d) : horloge maitrisee pour la verification TOTP. Avec
     * l'anti-rejeu, un code ne vaut qu'une fois : le test avance l'horloge d'un
     * pas (30 s) entre la confirmation (etape 9) et la connexion (etape 13), au
     * lieu de dependre du hasard des fenetres de 30 s.
     */
    static final HorlogeReglable HORLOGE = new HorlogeReglable(Instant.now());

    static final class HorlogeReglable extends Clock {
        private final AtomicReference<Instant> instant;

        HorlogeReglable(Instant depart) { this.instant = new AtomicReference<>(depart); }

        void avancer(Duration duree) { instant.updateAndGet(i -> i.plus(duree)); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }

    @TestConfiguration
    static class SyncTaskExecutorConfig {
        @Bean
        @Primary
        public TaskExecutor taskExecutor() {
            return new SyncTaskExecutor();
        }

        @Bean
        @Primary
        public Clock horlogeTest() {
            return HORLOGE;
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
    /**
     * Preparation et assertions en PROPRIETAIRE, hors RLS : le test lit l'etat
     * reel de la base, quel que soit le workspace (lot L0).
     */
    private final JdbcTemplate jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

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

    private static final String CODE_DEMO = "JUR-DEMO1";
    private static final String KARIM = "karim@jurika.ma";
    private static final UUID KARIM_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID WS_DEMO = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    @org.junit.jupiter.api.Order(1)
    void controle_du_code_workspace() throws Exception {
        mvc.perform(post("/api/v1/auth/workspace-check").contentType("application/json")
                        .content("{\"workspaceCode\":\"" + CODE_DEMO + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(WS_DEMO.toString()));
        mvc.perform(post("/api/v1/auth/workspace-check").contentType("application/json")
                        .content("{\"workspaceCode\":\"JUR-ZZZZZ\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void defi_sms_de_connexion_cree_le_code() throws Exception {
        mvc.perform(post("/api/v1/auth/login/sms/challenge").contentType("application/json")
                        .content("{\"userId\":\"" + KARIM_ID + "\",\"workspaceId\":\"" + WS_DEMO + "\"}"))
                .andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sms_otp_codes WHERE user_id = ? AND purpose = '2FA_LOGIN'",
                Integer.class, KARIM_ID)).isEqualTo(1);
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void renouvellement_de_session() throws Exception {
        String refresh = readField(connexion("Admin@2026"), "refreshToken");
        assertThat(refresh).isNotBlank();
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString());
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                        .content("{\"refreshToken\":\"jeton-inconnu\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @SuppressWarnings("unchecked")
    void reinitialisation_du_mot_de_passe() throws Exception {
        mvc.perform(post("/api/v1/auth/password-reset/request").contentType("application/json")
                        .content("{\"workspaceCode\":\"" + CODE_DEMO + "\",\"email\":\"" + KARIM + "\"}"))
                .andExpect(status().isAccepted());
        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(emailSender, atLeastOnce()).sendTemplated(anyString(), anyString(), anyString(), vars.capture());
        String lien = vars.getAllValues().stream()
                .map(v -> (String) v.get("resetUrl")).filter(java.util.Objects::nonNull)
                .reduce((a, b) -> b).orElseThrow();
        String jeton = lien.substring(lien.indexOf("token=") + "token=".length());

        String confirmation = "{\"token\":\"" + jeton + "\",\"newPassword\":\"Nouveau@Mdp2026!\"}";
        mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType("application/json")
                        .content(confirmation))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType("application/json")
                        .content(confirmation))
                .andExpect(status().isUnauthorized());
        connexion("Nouveau@Mdp2026!");
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    void inscription_d_un_cabinet() throws Exception {
        mvc.perform(post("/api/v1/public/signup/cabinet").contentType("application/json")
                        .content("""
                                { "workspaceName": "Cabinet Signup L0", "city": "Casablanca",
                                  "firstName": "Salma", "lastName": "Signup",
                                  "email": "salma.signup@example.com", "phone": "+212600000099",
                                  "cguAccepted": true }
                                """))
                .andExpect(status().is2xxSuccessful());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workspaces WHERE name = 'Cabinet Signup L0'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users u JOIN workspaces w ON w.id = u.workspace_id "
                + "WHERE w.name = 'Cabinet Signup L0'", Integer.class)).isEqualTo(1);
    }

    private MvcResult connexion(String motDePasse) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("{\"workspaceCode\":\"" + CODE_DEMO + "\",\"email\":\"" + KARIM
                                + "\",\"password\":\"" + motDePasse + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String readField(MvcResult r, String field) throws Exception {
        JsonNode node = json.readTree(r.getResponse().getContentAsString()).get(field);
        return node == null || node.isNull() ? null : node.asText();
    }
}
