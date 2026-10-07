package ma.jurika.ticket.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import ma.jurika.common.security.JwtClaims;
import ma.jurika.common.security.JwtKeyConfig;
import ma.jurika.ticket.domain.port.MemberDirectory;
import ma.jurika.ticket.domain.port.TicketEventPublisher;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lot L0, etape E14 : ticket-service de bout en bout en role d'execution
 * jurika_app (RLS active, garde « hors transaction »), un scenario par cas
 * d'usage de l'inventaire E1 (section 2.6) : chacun ATTEND des lignes.
 *
 * <p>Schema : VRAIES migrations dans l'ordre de jurika_db (auth, ticket jusqu'a
 * V20, dataroom, fin de ticket, workflow), appliquees avant le contexte en
 * proprietaire.
 * Jetons JWT signes en HMAC par le test (profil de developpement du filtre).
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
                "spring.cloud.discovery.enabled=false",
                "eureka.client.enabled=false",
                "jurika.trial.remote.enabled=false",
                // Audit en JDBC direct : la voie RabbitMQ (consommateur) est prouvee
                // sous jurika_app par AuditConsommateurRlsIT.
                "jurika.audit.async-enabled=false",
                "jurika.demo-seed=false",
                "jurika.jwt.algorithm=HS256",
                "jurika.jwt.hmac-secret=" + TicketJurikaAppIT.SECRET
        }
)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TicketJurikaAppIT {

    static final String SECRET = "secret-de-test-L0-ticket-au-moins-32-caracteres";
    static final UUID WS_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID KARIM = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final UUID COLLEGUE = UUID.fromString("33333333-3333-3333-3333-0000000000c2");
    static final UUID WS_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    static final UUID EMPLOYE_B = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000e1");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_ticket")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("testcontainers-init.sql");

    private static boolean migree;

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        migrerChaineAmont();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "jurika_app");
        registry.add("spring.datasource.password", () -> "jurika_app_it");
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    /** Ticket V21 reference dataroom_documents, que dataroom V5 cree en referencant tickets. */
    private static synchronized void migrerChaineAmont() {
        if (migree) return;
        migrer("filesystem:../auth-service/src/main/resources/db/migration", "flyway_history_auth", null);
        migrer("classpath:db/migration", "flyway_history_ticket", "20");
        migrer("filesystem:../dataroom-service/src/main/resources/db/migration", "flyway_history_dataroom", null);
        migrer("classpath:db/migration", "flyway_history_ticket", null);
        // Les demarches lisent workflow_progress (DemarcheUseCases).
        migrer("filesystem:../workflow-service/src/main/resources/db/migration", "flyway_history_workflow", null);
        JdbcTemplate owner = proprietaire();
        owner.execute((java.sql.Connection c) -> {
            try (java.sql.Statement st = c.createStatement()) {
                st.execute("CREATE TEMP TABLE w AS SELECT * FROM workspaces WHERE id = '" + WS_A + "'");
                st.execute("UPDATE w SET id = '" + WS_B + "', code = 'JUR-TKTBB'");
                st.execute("INSERT INTO workspaces SELECT * FROM w");
                st.execute("CREATE TEMP TABLE u AS SELECT * FROM users WHERE id = '" + KARIM + "'");
                st.execute("UPDATE u SET id = '" + COLLEGUE + "', email = 'collegue@rls.test', login_email = 'collegue@rls.test'");
                st.execute("INSERT INTO users SELECT * FROM u");
                st.execute("UPDATE u SET id = '" + EMPLOYE_B + "', workspace_id = '" + WS_B
                        + "', email = 'emp.b@rls.test', login_email = 'emp.b@rls.test'");
                st.execute("INSERT INTO users SELECT * FROM u");
            }
            return null;
        });
        migree = true;
    }

    private static void migrer(String emplacement, String historique, String cible) {
        var config = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations(emplacement).table(historique)
                .baselineOnMigrate(true).baselineVersion("0");
        if (cible != null) config.target(cible);
        config.load().migrate();
    }

    private static JdbcTemplate proprietaire() {
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    @TestConfiguration
    static class Infrastructure {
        @Bean
        ConnectionFactory rabbitConnectionFactory() {
            return mock(ConnectionFactory.class);
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockBean private MemberDirectory annuaire;
    @MockBean private TicketEventPublisher evenements;

    private final JdbcTemplate owner = proprietaire();

    private static UUID ticketId;
    private static UUID dossierId;

    @BeforeEach
    void annuaire() {
        when(annuaire.roleOf(any(), any())).thenReturn(Optional.of("EMPLOYE"));
    }

    private static String jeton(UUID userId, UUID workspace, String role) {
        return "Bearer " + Jwts.builder()
                .claim(JwtClaims.USER_ID, userId.toString())
                .claim(JwtClaims.WORKSPACE_ID, workspace.toString())
                .claim(JwtClaims.ROLE, role)
                .claim(JwtClaims.EMAIL, "test@rls.test")
                .claim(JwtClaims.TYPE, JwtClaims.TYPE_ACCESS)
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(JwtKeyConfig.legacyHmacKey(SECRET))
                .compact();
    }

    private static String karim() {
        return jeton(KARIM, WS_A, "EMPLOYE");
    }

    @Test
    @Order(1)
    void creation_d_un_ticket_et_de_son_dossier() throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/tickets").header("Authorization", karim())
                        .contentType("application/json").content("""
                                { "titre": "Creation Societe L0", "type": "CREATION",
                                  "companyInfo": { "raisonSociale": "Societe L0", "formeJuridique": "SARL" } }
                                """))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        JsonNode t = json.readTree(r.getResponse().getContentAsString());
        ticketId = UUID.fromString(t.get("id").asText());
        dossierId = UUID.fromString(t.get("dossierId").asText());
        assertThat(owner.queryForObject("SELECT COUNT(*) FROM tickets WHERE id = ? AND workspace_id = ?",
                Integer.class, ticketId, WS_A)).isEqualTo(1);
        assertThat(owner.queryForObject("SELECT COUNT(*) FROM entreprise_dossiers WHERE id = ? AND workspace_id = ?",
                Integer.class, dossierId, WS_A)).isEqualTo(1);
    }

    @Test
    @Order(2)
    void lecture_recherche_et_mise_a_jour() throws Exception {
        mvc.perform(get("/api/v1/tickets/" + ticketId).header("Authorization", karim()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId.toString()));
        mvc.perform(get("/api/v1/tickets").header("Authorization", karim()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(patch("/api/v1/tickets/" + ticketId).header("Authorization", karim())
                        .contentType("application/json").content("{\"titre\":\"Creation Societe L0 (modifie)\"}"))
                .andExpect(status().isOk());
        assertThat(owner.queryForObject("SELECT titre FROM tickets WHERE id = ?", String.class, ticketId))
                .isEqualTo("Creation Societe L0 (modifie)");
    }

    @Test
    @Order(3)
    void autre_cabinet_ne_voit_pas_le_ticket() throws Exception {
        String employeB = jeton(EMPLOYE_B, WS_B, "EMPLOYE");
        mvc.perform(get("/api/v1/tickets/" + ticketId).header("Authorization", employeB))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/tickets").header("Authorization", employeB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @Order(4)
    void debours_ajout_liste_et_export() throws Exception {
        mvc.perform(post("/api/v1/tickets/" + ticketId + "/debours").header("Authorization", karim())
                        .contentType("application/json").content("""
                                { "libelle": "Frais de greffe", "categorie": "FRAIS_TRIBUNAL",
                                  "montant": 350.00, "dateEngagement": "2026-10-01" }
                                """))
                .andExpect(status().is2xxSuccessful());
        mvc.perform(get("/api/v1/tickets/" + ticketId + "/debours").header("Authorization", karim()))
                .andExpect(status().isOk());
        assertThat(owner.queryForObject("SELECT COUNT(*) FROM ticket_debours WHERE ticket_id = ?",
                Integer.class, ticketId)).isEqualTo(1);
        mvc.perform(get("/api/v1/tickets/" + ticketId + "/debours/export-pdf").header("Authorization", karim()))
                .andExpect(status().isOk());
    }

    @Test
    @Order(5)
    void echeances_creation_et_liste() throws Exception {
        mvc.perform(post("/api/v1/deadlines").header("Authorization", karim())
                        .contentType("application/json").content("""
                                { "ticketId": "%s", "title": "Depot au greffe", "dueAt": "2030-01-01T10:00:00Z" }
                                """.formatted(ticketId)))
                .andExpect(status().is2xxSuccessful());
        mvc.perform(get("/api/v1/deadlines/count-open").header("Authorization", karim()))
                .andExpect(status().isOk());
        assertThat(owner.queryForObject("SELECT COUNT(*) FROM deadlines WHERE ticket_id = ? AND title = 'Depot au greffe'",
                Integer.class, ticketId)).isEqualTo(1);
    }

    @Test
    @Order(6)
    void demarches_et_recapitulatif() throws Exception {
        mvc.perform(get("/api/v1/tickets/" + ticketId + "/demarches").header("Authorization", karim()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/tickets/" + ticketId + "/demarches/recapitulatif").header("Authorization", karim()))
                .andExpect(status().isOk());
    }

    @Test
    @Order(7)
    void identifiants_succursales_et_transfert_du_dossier() throws Exception {
        mvc.perform(patch("/api/v1/dossiers/" + dossierId + "/identifiants").header("Authorization", karim())
                        .contentType("application/json").content("{\"ice\":\"001234567000089\"}"))
                .andExpect(status().is2xxSuccessful());
        assertThat(owner.queryForObject("SELECT ice FROM entreprise_dossiers WHERE id = ?", String.class, dossierId))
                .isEqualTo("001234567000089");
        mvc.perform(get("/api/v1/dossiers/" + dossierId + "/succursales").header("Authorization", karim()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/dossiers/" + dossierId + "/transfer-requests").header("Authorization", karim())
                        .contentType("application/json").content("{\"toUserId\":\"" + COLLEGUE + "\"}"))
                .andExpect(status().is2xxSuccessful());
        assertThat(owner.queryForObject("SELECT COUNT(*) FROM dossier_transfert_requests WHERE dossier_id = ?",
                Integer.class, dossierId)).isEqualTo(1);
        mvc.perform(get("/api/v1/dossier-transfer-requests").param("box", "inbox")
                        .header("Authorization", jeton(COLLEGUE, WS_A, "EMPLOYE")))
                .andExpect(status().isOk());
    }

    @Test
    @Order(8)
    void transition_vers_annule() throws Exception {
        mvc.perform(post("/api/v1/tickets/" + ticketId + "/transition").header("Authorization", karim())
                        .contentType("application/json")
                        .content("{\"target\":\"ANNULE\",\"comment\":\"Annulation de test du lot L0\"}"))
                .andExpect(status().is2xxSuccessful());
        assertThat(owner.queryForObject("SELECT statut FROM tickets WHERE id = ?", String.class, ticketId))
                .isEqualTo("ANNULE");
    }
}
