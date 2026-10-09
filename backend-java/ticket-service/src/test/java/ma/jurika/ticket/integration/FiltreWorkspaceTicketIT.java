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
 * Lot L0, etape E21 (P9) : les ecritures de TicketRepositoryAdapter
 * (updateStatut, updateAssignment, markTransferred) relisent le ticket filtre par
 * le workspace courant, en plus de la RLS. L'application se connecte ici en
 * PROPRIETAIRE (hors RLS) : seul le filtre applicatif protege. Workspace courant
 * A ; ticket du cabinet B.
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
                "jurika.jwt.algorithm=HS256"
        }
)
class FiltreWorkspaceTicketIT {

    /**
     * Secret HMAC des jetons de test, tire au hasard a chaque execution (aucune
     * valeur ecrite dans le depot) ; transmis par @DynamicPropertySource.
     */
    static final String SECRET = java.util.UUID.randomUUID() + "-" + java.util.UUID.randomUUID();
    static final UUID WS_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID KARIM = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final UUID COLLEGUE = UUID.fromString("33333333-3333-3333-3333-0000000000c2");
    static final UUID WS_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    static final UUID EMPLOYE_B = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000e1");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_filtre_ticket")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("testcontainers-init.sql");

    private static boolean migree;

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        migrerChaineAmont();
        registry.add("jurika.jwt.hmac-secret", () -> SECRET);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        // E21 : PROPRIETAIRE (hors RLS), pour eprouver le filtre applicatif seul.
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
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

    @Autowired private ma.jurika.ticket.infrastructure.persistence.TicketRepositoryAdapter tickets;
    @MockBean private MemberDirectory annuaire;
    @MockBean private TicketEventPublisher evenements;

    private final JdbcTemplate owner = proprietaire();
    private UUID ticketB;

    @org.junit.jupiter.api.BeforeEach
    void ticketDuCabinetB() {
        ticketB = UUID.randomUUID();
        owner.update("""
                INSERT INTO tickets(id, workspace_id, reference, titre, type, statut, cree_par_id)
                VALUES (?, ?, ?, 'Ticket de B', 'CREATION', 'CREATION_TICKET', ?)
                """, ticketB, WS_B, "T-B-" + ticketB.toString().substring(0, 8), EMPLOYE_B);
    }

    @org.junit.jupiter.api.AfterEach
    void vider() {
        ma.jurika.common.security.TenantContext.clear();
    }

    private void depuisA(Runnable ecriture) {
        ma.jurika.common.security.TenantContext.set(WS_A);
        org.assertj.core.api.Assertions.assertThatThrownBy(ecriture::run)
                .isInstanceOf(ma.jurika.common.exception.NotFoundException.class);
    }

    private String colonne(String nom) {
        return owner.queryForObject("SELECT " + nom + "::text FROM tickets WHERE id = ?", String.class, ticketB);
    }

    @Test
    void le_statut_d_un_ticket_d_un_autre_workspace_n_est_pas_modifie() {
        depuisA(() -> tickets.updateStatut(ticketB, ma.jurika.ticket.domain.model.TicketStatut.ANNULE,
                "intrusion", null, Instant.now()));
        assertThat(colonne("statut")).isEqualTo("CREATION_TICKET");
    }

    @Test
    void l_affectation_d_un_ticket_d_un_autre_workspace_n_est_pas_modifiee() {
        depuisA(() -> tickets.updateAssignment(ticketB, KARIM, null, null, "titre force", null));
        assertThat(colonne("titre")).isEqualTo("Ticket de B");
    }

    @Test
    void le_transfert_d_un_ticket_d_un_autre_workspace_est_refuse() {
        depuisA(() -> tickets.markTransferred(ticketB, KARIM, Instant.now()));
        assertThat(colonne("transferred_at")).isNull();
    }
}
