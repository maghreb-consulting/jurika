package ma.jurika.dashboard.integration;

import ma.jurika.common.security.TenantContext;
import ma.jurika.dashboard.application.aggregator.ClientDashboardAggregator;
import ma.jurika.dashboard.application.aggregator.EmployeDashboardAggregator;
import ma.jurika.dashboard.application.aggregator.SuperAdminDashboardAggregator;
import ma.jurika.dashboard.application.aggregator.SuperviseurDashboardAggregator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10 -- IT principal du dashboard-service couvrant les 4 aggregators.
 *
 * <p>On teste uniquement la logique d'agregation SQL, mais le contexte Spring
 * complet est charge : il faut donc un Redis reel pour cabler {@code RedisCacheConfig}
 * (le cache n'est PAS conditionnel en prod). On fournit un Redis Testcontainer
 * (comme {@code DashboardExtendedIT}) plutot que d'exclure l'auto-configuration
 * Redis, ce qui laissait {@code RedisCacheConfig.redisTemplate} sans
 * {@code RedisConnectionFactory} (BeanCreationException). Le client Jedis est
 * force via {@code application-it.yml} (contournement Lettuce/Netty sur Windows).
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
        }
)
@ActiveProfiles("it")
class DashboardSprint10IT {

    static {
        // Contournement Netty/Lettuce + JDK NIO sur Windows (cf. DashboardExtendedIT).
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("sun.nio.ch.bugLevel", "");
        System.setProperty("io.netty.transport.noNative", "true");
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_dashboard")
            .withUsername("jurika_it")
            .withPassword("jurika_it_pwd")
            .withInitScript("testcontainers-init.sql")
            .withReuse(false);

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private SuperviseurDashboardAggregator superviseur;
    @Autowired private EmployeDashboardAggregator employe;
    @Autowired private ClientDashboardAggregator client;
    @Autowired private SuperAdminDashboardAggregator superAdmin;

    private UUID ws;
    private UUID employeUser;
    private UUID clientUser;
    private UUID dossier;

    @BeforeEach
    void seed() {
        jdbc.execute("DELETE FROM dataroom_alertes_echeances");
        jdbc.execute("DELETE FROM tickets");
        jdbc.execute("DELETE FROM dataroom_documents");
        jdbc.execute("DELETE FROM entreprise_dossiers");
        jdbc.execute("DELETE FROM users");
        jdbc.execute("DELETE FROM workspaces");
        jdbc.execute("DELETE FROM audit_log");

        ws = UUID.randomUUID();
        employeUser = UUID.randomUUID();
        clientUser = UUID.randomUUID();
        dossier = UUID.randomUUID();

        jdbc.update("INSERT INTO workspaces(id,name,code_workspace) VALUES (?,?,?)",
                ws, "Cabinet S10", "JUR-S1000");
        jdbc.update("INSERT INTO users(id,workspace_id,email,role) VALUES (?,?,?,?)",
                employeUser, ws, "emp@s10.ma", "EMPLOYE");
        jdbc.update("INSERT INTO users(id,workspace_id,email,role) VALUES (?,?,?,?)",
                clientUser, ws, "client@s10.ma", "CLIENT");
        jdbc.update("INSERT INTO entreprise_dossiers(id,workspace_id,raison_sociale,client_id) VALUES (?,?,?,?)",
                dossier, ws, "SARL Test S10", clientUser);

        // 2 tickets ouverts + 1 cloturé
        jdbc.update("""
                INSERT INTO tickets(id,workspace_id,reference,titre,type,statut,priorite,dossier_id,assigne_id,created_at)
                VALUES (?,?,?,?,?,?,?,?,?, NOW())
                """, UUID.randomUUID(), ws, "T-001", "T1", "CREATION", "EN_COURS", "NORMALE", dossier, employeUser);
        jdbc.update("""
                INSERT INTO tickets(id,workspace_id,reference,titre,type,statut,priorite,dossier_id,assigne_id,created_at)
                VALUES (?,?,?,?,?,?,?,?,?, NOW())
                """, UUID.randomUUID(), ws, "T-002", "T2", "MODIFICATION", "NOUVEAU", "HAUTE", dossier, employeUser);
        jdbc.update("""
                INSERT INTO tickets(id,workspace_id,reference,titre,type,statut,priorite,dossier_id,assigne_id,created_at,cloture_at)
                VALUES (?,?,?,?,?,?,?,?,?, NOW() - INTERVAL '5 days', NOW() - INTERVAL '1 day')
                """, UUID.randomUUID(), ws, "T-003", "T3", "CREATION", "CLOTURE", "NORMALE", dossier, employeUser);

        // Echeances : 1 J-10 + 1 J-2 (rouge)
        UUID exercice = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO dataroom_alertes_echeances(id,workspace_id,dossier_id,exercice_fiscal_id,type_echeance,date_echeance,statut)
                VALUES (?,?,?,?,?, CURRENT_DATE + INTERVAL '10 day', 'PLANIFIEE')
                """, UUID.randomUUID(), ws, dossier, exercice, "TVA_MENSUELLE");
        jdbc.update("""
                INSERT INTO dataroom_alertes_echeances(id,workspace_id,dossier_id,exercice_fiscal_id,type_echeance,date_echeance,statut)
                VALUES (?,?,?,?,?, CURRENT_DATE + INTERVAL '2 day', 'PLANIFIEE')
                """, UUID.randomUUID(), ws, dossier, exercice, "IS_ACOMPTE_T1");

        TenantContext.set(ws);
    }

    @Test
    @DisplayName("SUPERVISEUR : agrege tickets ouverts + clos + temps moyen + echeances J-3/J-15")
    void superviseurAggregator() {
        var dto = superviseur.aggregate(ws);
        assertThat(dto.ticketsOuverts()).isEqualTo(2);
        assertThat(dto.ticketsClos30d()).isEqualTo(1);
        assertThat(dto.tempsMoyenClotureHeures()).isGreaterThan(0);
        assertThat(dto.echeancesJ30J15J3()).hasSize(3);
        // J-30 bucket inclut J-10 et J-2 ; J-15 inclut les deux ; J-3 inclut J-2 seulement
        var j3 = dto.echeancesJ30J15J3().stream()
                .filter(b -> "J-3".equals(b.label())).findFirst().orElseThrow();
        assertThat(j3.items()).hasSize(1);
    }

    @Test
    @DisplayName("EMPLOYE : 2 tickets ouverts assignes + charge semaine 5 jours")
    void employeAggregator() {
        var dto = employe.aggregate(ws, employeUser);
        assertThat(dto.mesTicketsOuverts()).isEqualTo(2);
        assertThat(dto.derniersTickets()).hasSize(3);
        assertThat(dto.maChargeSemaine()).hasSize(5);
    }

    @Test
    @DisplayName("CLIENT : ne voit que les dossiers ou il est client_id (RG-DASH-09)")
    void clientAggregator() {
        var dto = client.aggregate(ws, clientUser);
        assertThat(dto.mesDossiers()).hasSize(1);
        assertThat(dto.mesDossiers().get(0).raisonSociale()).isEqualTo("SARL Test S10");
        // Tickets en cours : 2 (NOUVEAU + EN_COURS)
        assertThat(dto.mesTicketsEnCours()).hasSize(2);
        // Echeances : 2 a venir
        assertThat(dto.mesEcheancesAVenir()).hasSize(2);
    }

    @Test
    @DisplayName("CLIENT : un client different ne voit pas les dossiers (multi-tenant + RG-DASH-09)")
    void clientIsolationByClientId() {
        var dto = client.aggregate(ws, UUID.randomUUID());
        assertThat(dto.mesDossiers()).isEmpty();
        assertThat(dto.mesTicketsEnCours()).isEmpty();
    }

    @Test
    @DisplayName("SUPER_ADMIN : agrege activeWorkspaces / signups / storage (RG-DASH-02)")
    void superAdminAggregator() {
        // Force audit event critical 24h
        jdbc.update("""
                INSERT INTO audit_log(workspace_id,user_id,action,entity_type,created_at)
                VALUES (?,?,?,?, NOW())
                """, ws, employeUser, "LOGIN_FAILED", "auth");
        var dto = superAdmin.aggregate();
        assertThat(dto.criticalAuditEvents24h()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(dto.servicesHealth()).containsKeys("dataroom-service", "ticket-service");
        assertThat(dto.storage()).isNotNull();
    }

    @Test
    @DisplayName("Performance RG-DASH-07 : aggregation < 800ms sur dataset modeste")
    void performance() {
        long start = System.currentTimeMillis();
        superviseur.aggregate(ws);
        long elapsed = System.currentTimeMillis() - start;
        assertThat(elapsed).as("aggregation duration").isLessThan(800);
    }
}
