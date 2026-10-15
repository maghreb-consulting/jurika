package ma.jurika.ticket.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import ma.jurika.common.trial.TrialAccessChecker;
import ma.jurika.ticket.domain.port.MemberDirectory;
import ma.jurika.ticket.domain.port.TicketEventPublisher;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Decision du 2026-10-14 (RG-ESS-05) : le filtre de l'essai est BRANCHE dans la chaine de securite
 * de ticket-service. Pendant l'essai, l'etat des debours en PDF (lien direct) est refuse (402,
 * corps JSON nomme, aucun octet du PDF) ; pour un cabinet abonne, le filtre laisse passer.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
                "spring.cloud.discovery.enabled=false",
                "eureka.client.enabled=false",
                "jurika.audit.async-enabled=false",
                "jurika.demo-seed=false",
                "jurika.jwt.algorithm=HS256"
        }
)
@AutoConfigureMockMvc
class EssaiTicketIT {

    static final String SECRET = UUID.randomUUID() + "-" + UUID.randomUUID();
    static final UUID WS_ESSAI = UUID.fromString("e5a20000-0000-0000-0000-000000000001");
    static final UUID WS_ABONNE = UUID.fromString("e5a20000-0000-0000-0000-000000000002");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_essai_ticket")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("testcontainers-init.sql");

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        migrer("filesystem:../auth-service/src/main/resources/db/migration", "flyway_history_auth", null);
        migrer("classpath:db/migration", "flyway_history_ticket", "20");
        migrer("filesystem:../dataroom-service/src/main/resources/db/migration", "flyway_history_dataroom", null);
        migrer("classpath:db/migration", "flyway_history_ticket", null);
        migrer("filesystem:../workflow-service/src/main/resources/db/migration", "flyway_history_workflow", null);
        registry.add("jurika.jwt.hmac-secret", () -> SECRET);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    private static void migrer(String emplacement, String historique, String cible) {
        var config = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations(emplacement).table(historique)
                .baselineOnMigrate(true).baselineVersion("0");
        if (cible != null) config.target(cible);
        config.load().migrate();
    }

    @TestConfiguration
    static class Infrastructure {
        @Bean
        ConnectionFactory rabbitConnectionFactory() {
            return mock(ConnectionFactory.class);
        }

        /** Etat d'essai fixe par cabinet (auth-service n'est pas lance) : le filtre est cree. */
        @Bean
        TrialAccessChecker verificateurEssaiDeTest() {
            return ws -> Optional.of(new TrialAccessChecker.TrialState(ws,
                    WS_ESSAI.equals(ws) ? "TRIAL_ACTIVE" : "CONVERTED", 3, Instant.now(), null));
        }
    }

    @Autowired private MockMvc mvc;
    @MockBean private MemberDirectory annuaire;
    @MockBean private TicketEventPublisher evenements;

    private static String jeton(UUID ws) {
        return "Bearer " + Jwts.builder()
                .issuer("jurika.ma")
                .claim("typ", "access")
                .claim("uid", "33333333-3333-3333-3333-333333333333")
                .claim("wsid", ws.toString())
                .claim("email", "superviseur@essai.ma")
                .claim("role", "SUPERVISEUR")
                .claim("mcp", false)
                .claim("r2s", false)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void essai_etat_des_debours_en_pdf_refuse_par_le_filtre_de_la_chaine() throws Exception {
        byte[] corps = mvc.perform(get("/api/v1/tickets/" + UUID.randomUUID() + "/debours/etat-pdf")
                        .header("Authorization", jeton(WS_ESSAI)))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("ESSAI_TELECHARGEMENT_INTERDIT"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(corps, StandardCharsets.ISO_8859_1)).doesNotContain("%PDF");
    }

    @Test
    void cabinet_abonne_le_filtre_laisse_passer() throws Exception {
        int statut = mvc.perform(get("/api/v1/tickets/" + UUID.randomUUID() + "/debours/etat-pdf")
                        .header("Authorization", jeton(WS_ABONNE)))
                .andReturn().getResponse().getStatus();
        assertThat(statut).isNotEqualTo(402);
    }
}
