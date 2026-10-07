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
 * Lot L0, etape E13b : chemins du SUPER-ADMIN (vues transverses, actions sur
 * un workspace ou un utilisateur cible), routes INTERNES appelees par les
 * autres services, purge planifiee et lectures authentifiees autrefois hors
 * transaction (/me, consommation), en role d'execution jurika_app (RLS active,
 * garde « hors transaction »).
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
class CheminsAdminInternesJurikaAppIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_admin")
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

    private static final UUID WS_DEMO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WS_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ADMIN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID KARIM_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    /** Employe et superviseur du second cabinet (clones de l'employe seme). */
    private static final UUID EMPLOYE_B = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000e1");
    private static final UUID SUPERVISEUR_B = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000f1");

    @Autowired private ma.jurika.auth.domain.port.TokenIssuer tokenIssuer;
    @Autowired private ma.jurika.auth.domain.port.UserRepository userRepository;
    @Autowired private ma.jurika.auth.infrastructure.scheduling.RefreshTokenCleanupJob purge;

    private static boolean prepare;

    @BeforeEach
    void preparerUneFois() {
        if (prepare) return;
        // Base partagee : les compteurs de consommation lisent entreprise_dossiers
        // (ticket) et les documents (dataroom). VRAIES migrations, dans l'ordre de
        // jurika_db, apres celles d'auth appliquees par le contexte (cf. E12).
        migrer("filesystem:../ticket-service/src/main/resources/db/migration", "flyway_history_ticket", "20");
        migrer("filesystem:../dataroom-service/src/main/resources/db/migration", "flyway_history_dataroom", null);
        migrer("filesystem:../ticket-service/src/main/resources/db/migration", "flyway_history_ticket", null);
        // 2FA configuree (sinon Setup2faRequiredEnforcer bloque les routes metier)
        // et second cabinet, en PROPRIETAIRE.
        jdbc.update("UPDATE users SET twofa_method = 'TOTP', totp_enabled = TRUE WHERE id IN (?, ?)", ADMIN_ID, KARIM_ID);
        jdbc.execute((java.sql.Connection c) -> {
            try (java.sql.Statement st = c.createStatement()) {
                // Une seule connexion : la table temporaire y reste visible.
                st.execute("CREATE TEMP TABLE w AS SELECT * FROM workspaces WHERE id = '" + WS_DEMO + "'");
                st.execute("UPDATE w SET id = '" + WS_B + "', code = 'JUR-ADMBB'");
                st.execute("INSERT INTO workspaces SELECT * FROM w");
                st.execute("CREATE TEMP TABLE u AS SELECT * FROM users WHERE id = '" + KARIM_ID + "'");
                st.execute("UPDATE u SET id = '" + EMPLOYE_B + "', workspace_id = '" + WS_B
                        + "', email = 'emp.b@rls.test', login_email = 'emp.b@rls.test'");
                st.execute("INSERT INTO users SELECT * FROM u");
                st.execute("UPDATE u SET id = '" + SUPERVISEUR_B + "', role = 'SUPERVISEUR', "
                        + "email = 'sup.b@rls.test', login_email = 'sup.b@rls.test'");
                st.execute("INSERT INTO users SELECT * FROM u");
                // Une entree d'audit par utilisateur, dans chacun des deux cabinets.
                st.execute("INSERT INTO audit_log (workspace_id, user_id, action) VALUES "
                        + "('" + WS_DEMO + "', '" + KARIM_ID + "', 'L0_FILTRE_ROLE'), "
                        + "('" + WS_B + "', '" + EMPLOYE_B + "', 'L0_FILTRE_ROLE'), "
                        + "('" + WS_B + "', '" + SUPERVISEUR_B + "', 'L0_FILTRE_ROLE'), "
                        + "('" + WS_DEMO + "', '" + ADMIN_ID + "', 'L0_FILTRE_ROLE')");
            }
            return null;
        });
        prepare = true;
    }

    private static void migrer(String emplacement, String historique, String cible) {
        var config = org.flywaydb.core.Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations(emplacement)
                .table(historique)
                .baselineOnMigrate(true)
                .baselineVersion("0");
        if (cible != null) {
            config.target(cible);
        }
        config.load().migrate();
    }

    private String jeton(UUID userId) {
        return jeton(userId, WS_DEMO);
    }

    private String jeton(UUID userId, UUID workspace) {
        ma.jurika.common.security.TenantContext.set(workspace);
        try {
            return tokenIssuer.issue(userRepository.findById(userId).orElseThrow()).accessToken();
        } finally {
            ma.jurika.common.security.TenantContext.clear();
        }
    }

    @Test
    void super_admin_voit_tous_les_workspaces_et_leur_detail() throws Exception {
        String admin = jeton(ADMIN_ID);
        mvc.perform(get("/api/v1/admin/workspaces").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + WS_DEMO + "')]").exists())
                .andExpect(jsonPath("$[?(@.id == '" + WS_B + "')]").exists());
        mvc.perform(get("/api/v1/admin/workspaces/" + WS_B).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    @Test
    void super_admin_suspend_puis_reactive_un_autre_workspace() throws Exception {
        String admin = jeton(ADMIN_ID);
        mvc.perform(post("/api/v1/admin/workspaces/" + WS_B + "/suspend").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM workspaces WHERE id = ?", String.class, WS_B))
                .isEqualTo("SUSPENDED");
        mvc.perform(post("/api/v1/admin/workspaces/" + WS_B + "/activate").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM workspaces WHERE id = ?", String.class, WS_B))
                .isEqualTo("ACTIVE");
    }

    @Test
    void super_admin_liste_et_suspend_un_utilisateur() throws Exception {
        String admin = jeton(ADMIN_ID);
        // Filtre par role : exactement les employes, tous cabinets confondus.
        MvcResult liste = mvc.perform(get("/api/v1/admin/users").param("role", "EMPLOYE")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andReturn();
        assertThat(identifiants(liste, "id")).containsExactlyInAnyOrder(KARIM_ID.toString(), EMPLOYE_B.toString());
        mvc.perform(post("/api/v1/admin/users/" + KARIM_ID + "/suspend").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, KARIM_ID))
                .isEqualTo("INACTIVE");
        mvc.perform(post("/api/v1/admin/users/" + KARIM_ID + "/reactivate").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, KARIM_ID))
                .isEqualTo("ACTIVE");
        mvc.perform(get("/api/v1/admin/audit").param("role", "EMPLOYE").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    @Test
    void routes_internes_lisent_le_workspace_du_chemin() throws Exception {
        mvc.perform(get("/internal/workspaces/" + WS_DEMO + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(WS_DEMO.toString()));
        mvc.perform(get("/internal/workspaces/" + WS_DEMO + "/users/" + KARIM_ID + "/role"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EMPLOYE"));
        mvc.perform(get("/internal/workspaces/" + WS_DEMO + "/usage"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void lectures_authentifiees_passent_en_transaction() throws Exception {
        String karim = jeton(KARIM_ID);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + karim))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginEmail").value("karim@jurika.ma"));
        mvc.perform(get("/api/v1/workspace/usage").header("Authorization", "Bearer " + karim))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void purge_planifiee_des_jetons_expires() {
        jdbc.update("INSERT INTO refresh_tokens (workspace_id, user_id, token_hash, expires_at) "
                + "VALUES (?, ?, 'purge-l0-expire', NOW() - INTERVAL '30 days')", WS_DEMO, KARIM_ID);
        purge.run();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = 'purge-l0-expire'",
                Integer.class)).isZero();
    }

    @Test
    void filtre_par_role_du_journal_d_audit_tous_cabinets_confondus() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/admin/audit").param("role", "EMPLOYE")
                        .param("action", "L0_FILTRE_ROLE").param("limit", "100")
                        .header("Authorization", "Bearer " + jeton(ADMIN_ID)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(identifiants(r, "userId")).containsExactlyInAnyOrder(KARIM_ID.toString(), EMPLOYE_B.toString());
    }

    /**
     * Les fonctions SECURITY DEFINER qui lisent plusieurs cabinets ne sont
     * protegees QUE par la garde de leur route : un employe ou un superviseur
     * recoit 403 sur chacune (et non le refus de Setup2faRequiredEnforcer : leur
     * 2FA est configuree).
     */
    @org.junit.jupiter.params.ParameterizedTest(name = "{0} {1}")
    @org.junit.jupiter.params.provider.CsvSource({
            "GET,/api/v1/admin/workspaces",
            "GET,/api/v1/admin/users",
            "GET,/api/v1/admin/audit?role=EMPLOYE",
            "POST,/api/v1/admin/users/33333333-3333-3333-3333-333333333333/suspend",
            "POST,/api/v1/admin/users/33333333-3333-3333-3333-333333333333/reactivate"
    })
    void route_transverse_refusee_hors_super_admin(String verbe, String route) throws Exception {
        for (String jetonNonAdmin : new String[]{jeton(KARIM_ID), jeton(SUPERVISEUR_B, WS_B)}) {
            var requete = ("GET".equals(verbe) ? get(route) : post(route))
                    .header("Authorization", "Bearer " + jetonNonAdmin);
            MvcResult r = mvc.perform(requete).andExpect(status().isForbidden()).andReturn();
            assertThat(r.getResponse().getContentAsString()).doesNotContain("SETUP_2FA_REQUIRED");
        }
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, KARIM_ID))
                .isEqualTo("ACTIVE");
    }

    private java.util.List<String> identifiants(MvcResult r, String champ) throws Exception {
        java.util.List<String> ids = new java.util.ArrayList<>();
        json.readTree(r.getResponse().getContentAsString()).get("items").forEach(n -> ids.add(n.get(champ).asText()));
        return ids;
    }
}
