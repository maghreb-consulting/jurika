package ma.jurika.auth.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.model.UserStatus;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.EventPublisher;
import ma.jurika.auth.domain.port.SmsSender;
import ma.jurika.auth.domain.port.TokenIssuer;
import ma.jurika.common.observability.BusinessMetrics;
import ma.jurika.common.security.Role;
import ma.jurika.common.validation.DnsMxValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lot L6b (E2) : {@code GET} et {@code PUT /api/v1/users/me/preferences} de bout en bout (chaine
 * de securite reelle, base PostgreSQL reelle, role jurika_app sous RLS). Tout utilisateur
 * authentifie (super-admin, superviseur, employe, client) lit et modifie SES preferences, et
 * seulement les siennes ; valeur invalide : 400 nomme ; anonyme : refuse ; essai en lecture
 * seule : la preference reste modifiable (affichage), le reste du cabinet est fige.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
                "spring.main.allow-bean-definition-overriding=true"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("it")
class PreferencesCompteIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_pref")
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
    static class SyncWiring {
        @Bean @Primary public TaskExecutor taskExecutor() { return new SyncTaskExecutor(); }
        @Bean public MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
        @Bean public BusinessMetrics businessMetrics(MeterRegistry r) { return new BusinessMetrics(r); }
        @Bean public ConnectionFactory rabbitConnectionFactory() { return mock(ConnectionFactory.class); }
    }

    private static final UUID WS_DEMO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SUPER_ADMIN = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID EMPLOYE = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SUPERVISEUR = UUID.fromString("cccccccc-0000-0000-0000-0000000000a1");
    private static final UUID CLIENT = UUID.fromString("cccccccc-0000-0000-0000-0000000000a2");
    private static final UUID WS_ESSAI = UUID.fromString("cccccccc-0000-0000-0000-000000000e55");
    private static final UUID SUPERVISEUR_ESSAI = UUID.fromString("cccccccc-0000-0000-0000-0000000000a3");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private TokenIssuer tokenIssuer;
    /** Etat reel en proprietaire, hors RLS. */
    private final JdbcTemplate jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

    @MockBean private EmailSender emailSender;
    @MockBean private SmsSender smsSender;
    @MockBean private EventPublisher eventPublisher;
    @MockBean private DnsMxValidator dnsMxValidator;

    private static boolean comptesPoses;

    private void comptes() {
        if (comptesPoses) return;
        comptesPoses = true;
        copier(EMPLOYE, SUPERVISEUR, WS_DEMO, "SUPERVISEUR", "superviseur@preferences.test");
        copier(EMPLOYE, CLIENT, WS_DEMO, "CLIENT", "client@preferences.test");
        // Une seule instruction (une seule connexion) : la table temporaire vit le temps de la session.
        jdbc.execute("CREATE TEMP TABLE w_essai AS SELECT * FROM workspaces WHERE id = '" + WS_DEMO + "'; "
                + "UPDATE w_essai SET id = '" + WS_ESSAI + "', code = 'JUR-PREFE'; "
                + "INSERT INTO workspaces SELECT * FROM w_essai ON CONFLICT (id) DO NOTHING");
        jdbc.update("UPDATE workspaces SET trial_status = 'TRIAL_EXPIRED', trial_ends_at = now() - interval '1 day' "
                + "WHERE id = ?", WS_ESSAI);
        copier(EMPLOYE, SUPERVISEUR_ESSAI, WS_ESSAI, "SUPERVISEUR", "essai@preferences.test");
    }

    @BeforeEach
    void remettre() {
        comptes();
        jdbc.update("UPDATE users SET theme = 'SYSTEME', juri_masque = FALSE");
    }

    @Test
    @DisplayName("Chaque role lit puis modifie ses propres preferences ; les autres comptes ne changent pas")
    void chaque_role_lit_et_modifie_ses_preferences() throws Exception {
        Object[][] comptes = {
                {SUPER_ADMIN, WS_DEMO, Role.SUPER_ADMIN, "SOMBRE"},
                {SUPERVISEUR, WS_DEMO, Role.SUPERVISEUR, "CLAIR"},
                {EMPLOYE, WS_DEMO, Role.EMPLOYE, "SOMBRE"},
                {CLIENT, WS_DEMO, Role.CLIENT, "CLAIR"},
        };
        for (Object[] c : comptes) {
            UUID id = (UUID) c[0];
            String acces = jeton(id, (UUID) c[1], (Role) c[2]);
            JsonNode lu = lire(mvc.perform(get("/api/v1/users/me/preferences").header("Authorization", "Bearer " + acces))
                    .andExpect(status().isOk()).andReturn());
            assertThat(lu.get("theme").asText()).as(c[2].toString()).isEqualTo("SYSTEME");
            assertThat(lu.get("juriMasque").asBoolean()).as(c[2].toString()).isFalse();
            assertThat(lu.size()).as("seulement theme et juriMasque").isEqualTo(2);

            JsonNode ecrit = lire(mvc.perform(put("/api/v1/users/me/preferences").header("Authorization", "Bearer " + acces)
                            .contentType("application/json").content("{\"theme\":\"" + c[3] + "\",\"juriMasque\":true}"))
                    .andExpect(status().isOk()).andReturn());
            assertThat(ecrit.get("theme").asText()).isEqualTo(c[3]);
            assertThat(ecrit.get("juriMasque").asBoolean()).isTrue();
            assertThat(jdbc.queryForMap("SELECT theme, juri_masque FROM users WHERE id = ?", id))
                    .as(c[2].toString()).containsEntry("theme", c[3]).containsEntry("juri_masque", true);

            // Relu par GET : la valeur ecrite est celle qui revient (jamais une valeur par defaut).
            JsonNode relu = lire(mvc.perform(get("/api/v1/users/me/preferences").header("Authorization", "Bearer " + acces))
                    .andExpect(status().isOk()).andReturn());
            assertThat(relu.get("theme").asText()).isEqualTo(c[3]);
            assertThat(relu.get("juriMasque").asBoolean()).isTrue();

            // Ecriture partielle : seul juriMasque change.
            JsonNode partiel = lire(mvc.perform(put("/api/v1/users/me/preferences").header("Authorization", "Bearer " + acces)
                            .contentType("application/json").content("{\"juriMasque\":false}"))
                    .andExpect(status().isOk()).andReturn());
            assertThat(partiel.get("theme").asText()).isEqualTo(c[3]);
            assertThat(partiel.get("juriMasque").asBoolean()).isFalse();

            // Les autres comptes sont inchanges (l'identifiant vient du jeton, jamais de la requete).
            assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id <> ? AND (theme <> 'SYSTEME' OR juri_masque)",
                    Integer.class, id)).as(c[2].toString()).isZero();
            jdbc.update("UPDATE users SET theme = 'SYSTEME', juri_masque = FALSE");
        }
    }

    @Test
    @DisplayName("Valeur invalide ou cle inconnue : 400 nomme, rien n'est ecrit")
    void valeur_invalide_refusee() throws Exception {
        String acces = jeton(EMPLOYE, WS_DEMO, Role.EMPLOYE);
        Map<String, String> cas = Map.of(
                "{\"theme\":\"dark\"}", "PREFERENCE_THEME_INVALIDE",
                "{\"theme\":null}", "PREFERENCE_THEME_INVALIDE",
                "{\"juriMasque\":\"oui\"}", "PREFERENCE_JURI_INVALIDE",
                "{\"theme\":\"SOMBRE\",\"juri_masque\":true}", "PREFERENCE_INCONNUE");
        for (Map.Entry<String, String> e : cas.entrySet()) {
            JsonNode r = lire(mvc.perform(put("/api/v1/users/me/preferences").header("Authorization", "Bearer " + acces)
                            .contentType("application/json").content(e.getKey()))
                    .andExpect(status().isBadRequest()).andReturn());
            assertThat(r.get("code").asText()).as(e.getKey()).isEqualTo(e.getValue());
            assertThat(r.get("message").asText()).as(e.getKey()).isNotBlank();
        }
        assertThat(jdbc.queryForMap("SELECT theme, juri_masque FROM users WHERE id = ?", EMPLOYE))
                .containsEntry("theme", "SYSTEME").containsEntry("juri_masque", false);
    }

    @Test
    @DisplayName("Anonyme : refuse")
    void anonyme_refuse() throws Exception {
        int lecture = mvc.perform(get("/api/v1/users/me/preferences")).andReturn().getResponse().getStatus();
        int ecriture = mvc.perform(put("/api/v1/users/me/preferences").contentType("application/json")
                .content("{\"theme\":\"SOMBRE\"}")).andReturn().getResponse().getStatus();
        assertThat(lecture).isIn(401, 403);
        assertThat(ecriture).isIn(401, 403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE theme <> 'SYSTEME'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Essai en lecture seule : la preference d'affichage reste modifiable, l'ancien point d'entree est fige")
    void essai_en_lecture_seule() throws Exception {
        String acces = jeton(SUPERVISEUR_ESSAI, WS_ESSAI, Role.SUPERVISEUR);
        JsonNode r = lire(mvc.perform(put("/api/v1/users/me/preferences").header("Authorization", "Bearer " + acces)
                        .contentType("application/json").content("{\"theme\":\"SOMBRE\"}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(r.get("theme").asText()).isEqualTo("SOMBRE");
        // Temoin : la lecture seule de l'essai s'applique bien a ce cabinet.
        mvc.perform(post("/api/v1/users/me/theme").header("Authorization", "Bearer " + acces)
                        .contentType("application/json").content("{\"theme\":\"dark\"}"))
                .andExpect(status().isPaymentRequired());
    }

    // ------------------------------------------------------------ outils

    private void copier(UUID source, UUID id, UUID workspace, String role, String adresse) {
        if (jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, id) > 0) return;
        // Une seule instruction (une seule connexion) : la table temporaire vit le temps de la session.
        jdbc.execute("CREATE TEMP TABLE u_copie AS SELECT * FROM users WHERE id = '" + source + "'; "
                + "UPDATE u_copie SET id = '" + id + "', workspace_id = '" + workspace + "', role = '" + role
                + "', email = '" + adresse + "', login_email = '" + adresse + "', contact_email = '" + adresse + "'; "
                + "INSERT INTO users SELECT * FROM u_copie");
    }

    private String jeton(UUID id, UUID workspace, Role role) {
        Instant maintenant = Instant.now();
        User u = new User(id, workspace, "pref-" + id, "pref-" + id, "pref-" + id + "@example.org", null,
                "Prenom", "Nom", null, role, "secret-chiffre", true, false, "TOTP",
                maintenant, null, null, (short) 0, null, null, UserStatus.ACTIVE, maintenant);
        return tokenIssuer.issue(u).accessToken();
    }

    private JsonNode lire(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }
}
