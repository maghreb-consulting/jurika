package ma.jurika.auth.e2e;

import ma.jurika.auth.application.essai.AcceptationEssaiLecture;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.EventPublisher;
import ma.jurika.auth.domain.port.SmsSender;
import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.common.validation.DnsMxValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lot L7 (E13) : lecture, par billing-service, de l'acceptation des conditions de l'essai du
 * cabinet ({@code GET /api/v1/workspace/essai/acceptation}), en role d'execution jurika_app (RLS
 * active). Un cabinet ne lit que son propre essai ; superviseur seul ; sans essai, reponse
 * explicite ; donnee incoherente, refus nomme.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
                "jurika.essai.cycle.cron=-"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("it")
class EssaiAcceptationLectureIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_essai_acceptation")
            .withUsername("jurika_it")
            .withPassword("jurika_it_pwd")
            .withInitScript("testcontainers-init.sql")
            .withReuse(false);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "jurika_app");
        registry.add("spring.datasource.password", () -> "jurika_app_it");
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "16379");
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        public TaskExecutor taskExecutor() {
            return new SyncTaskExecutor();
        }

        @Bean
        public ConnectionFactory rabbitConnectionFactory() {
            return mock(ConnectionFactory.class);
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private AcceptationEssaiLecture lecture;
    @Autowired private ma.jurika.auth.domain.port.TokenIssuer tokenIssuer;
    @Autowired private ma.jurika.auth.domain.port.UserRepository userRepository;

    @MockBean private EmailSender emailSender;
    @MockBean private SmsSender smsSender;
    @MockBean private EventPublisher eventPublisher;
    @MockBean private DnsMxValidator dnsMxValidator;

    /** Preparation en PROPRIETAIRE, hors RLS. */
    private final JdbcTemplate jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

    /** Cabinet seme (V3) : il a eu un essai, conditions acceptees le 12 octobre 2026. */
    private static final UUID WS_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    /** Cabinet sans essai. */
    private static final UUID WS_B = UUID.fromString("e5a10000-0000-0000-0000-00000000000b");
    /** Cabinet au registre des essais sans acceptation enregistree (donnee incoherente). */
    private static final UUID WS_C = UUID.fromString("e5a10000-0000-0000-0000-00000000000c");
    private static final UUID KARIM_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SUP_A = UUID.fromString("e5a10000-0000-0000-0000-0000000000a1");
    private static final UUID SUP_B = UUID.fromString("e5a10000-0000-0000-0000-0000000000b1");
    private static final UUID SUP_C = UUID.fromString("e5a10000-0000-0000-0000-0000000000c1");
    static final Instant ACCEPTEES_LE = Instant.parse("2026-10-12T09:32:41Z");

    private static boolean prepare;

    @BeforeEach
    void preparerUneFois() {
        TenantContext.clear();
        if (prepare) return;
        jdbc.update("UPDATE users SET twofa_method = 'TOTP', totp_enabled = TRUE WHERE id = ?", KARIM_ID);
        jdbc.execute((java.sql.Connection c) -> {
            try (java.sql.Statement st = c.createStatement()) {
                st.execute("CREATE TEMP TABLE w AS SELECT * FROM workspaces WHERE id = '" + WS_A + "'");
                st.execute("UPDATE w SET id = '" + WS_B + "', code = 'JUR-ESSAB'");
                st.execute("INSERT INTO workspaces SELECT * FROM w");
                st.execute("UPDATE w SET id = '" + WS_C + "', code = 'JUR-ESSAC'");
                st.execute("INSERT INTO workspaces SELECT * FROM w");
                st.execute("CREATE TEMP TABLE u AS SELECT * FROM users WHERE id = '" + KARIM_ID + "'");
                superviseur(st, SUP_A, WS_A, "sup.a@essai.test");
                superviseur(st, SUP_B, WS_B, "sup.b@essai.test");
                superviseur(st, SUP_C, WS_C, "sup.c@essai.test");
            }
            return null;
        });
        jdbc.update("""
                INSERT INTO essai_conditions_acceptations (workspace_id, user_id, version, texte, empreinte,
                    acceptees_le, adresse_ip)
                VALUES (?, ?, 'D-V1', 'Conditions de l''essai', ?, ?, '10.1.1.1')
                """, WS_A, SUP_A, "a".repeat(64), Timestamp.from(ACCEPTEES_LE));
        jdbc.update("""
                INSERT INTO essais_registre (workspace_id, workspace_code, email_empreinte, telephone_empreinte)
                VALUES (?, 'JUR-ESSAC', ?, ?)
                """, WS_C, "b".repeat(64), "c".repeat(64));
        prepare = true;
    }

    private static void superviseur(java.sql.Statement st, UUID id, UUID ws, String email) throws java.sql.SQLException {
        st.execute("UPDATE u SET id = '" + id + "', workspace_id = '" + ws + "', role = 'SUPERVISEUR', email = '"
                + email + "', login_email = '" + email + "'");
        st.execute("INSERT INTO users SELECT * FROM u");
    }

    @AfterEach
    void nettoyer() {
        TenantContext.clear();
    }

    private String jeton(UUID userId, UUID workspace) {
        TenantContext.set(workspace);
        try {
            return "Bearer " + tokenIssuer.issue(userRepository.findById(userId).orElseThrow()).accessToken();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void superviseur_lit_la_version_et_la_date_d_acceptation_de_son_essai() throws Exception {
        mvc.perform(get("/api/v1/workspace/essai/acceptation").header("Authorization", jeton(SUP_A, WS_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.essai").value(true))
                .andExpect(jsonPath("$.version").value("D-V1"))
                .andExpect(jsonPath("$.accepteesLe").value("2026-10-12T09:32:41Z"));
    }

    @Test
    void cabinet_sans_essai_reponse_explicite_sans_donnees() throws Exception {
        mvc.perform(get("/api/v1/workspace/essai/acceptation").header("Authorization", jeton(SUP_B, WS_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.essai").value(false))
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.accepteesLe").doesNotExist());
    }

    @Test
    void employe_refuse() throws Exception {
        mvc.perform(get("/api/v1/workspace/essai/acceptation").header("Authorization", jeton(KARIM_ID, WS_A)))
                .andExpect(status().isForbidden());
    }

    @Test
    void sans_jeton_refuse() throws Exception {
        mvc.perform(get("/api/v1/workspace/essai/acceptation"))
                .andExpect(status().isForbidden()); // comportement de la chaine de securite d auth sans jeton
    }

    @Test
    void la_rls_cache_l_essai_d_un_autre_cabinet_meme_si_le_filtre_applicatif_manquait() {
        // Contexte du cabinet B, lecture demandee pour A : la RLS ne rend aucune ligne de A.
        TenantContext.set(WS_B);
        assertThat(lecture.lire(WS_A).essai()).isFalse();
        TenantContext.set(WS_A);
        assertThat(lecture.lire(WS_A).essai()).isTrue();
    }

    @Test
    void cabinet_au_registre_sans_acceptation_refus_nomme() throws Exception {
        mvc.perform(get("/api/v1/workspace/essai/acceptation").header("Authorization", jeton(SUP_C, WS_C)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("introuvable")));
        TenantContext.set(WS_C);
        assertThatThrownBy(() -> lecture.lire(WS_C)).isInstanceOf(ConflictException.class);
    }
}
