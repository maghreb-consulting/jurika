package ma.jurika.dataroom.integration;

import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.AccessLogPage;
import ma.jurika.dataroom.application.access.ClientAccessLogQueryService;
import ma.jurika.dataroom.application.access.ClientAccessLogger;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L6b (E13) : le journal d'acces du client s'ecrit reellement.
 *
 * <p>Avant le correctif, chaque insertion echouait ("column ip_address is of type
 * inet but expression is of type character varying" : l'adresse etait liee en
 * VARCHAR, meme nulle) et l'echec n'etait qu'un avertissement : le panneau
 * "Activite client" restait vide (motif 9). Sur le vrai schema (migrations), en
 * role d'execution jurika_app (RLS), par le chemin reel (logger, puis insertion
 * asynchrone) : la ligne est lue en base, avec l'adresse quand elle est valide,
 * NULL sinon (la trace n'est jamais perdue pour une adresse illisible).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"})
@ActiveProfiles("it")
class JournalAccesClientEcritureIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_journal_acces")
            .withUsername("jurika_it")
            .withPassword("jurika_it_pwd")
            .withInitScript("testcontainers-init.sql");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        SchemaJurikaDb.migrer(POSTGRES);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "jurika_app");
        registry.add("spring.datasource.password", () -> "jurika_app_it");
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("jurika.minio.endpoint", () -> "http://localhost:9099");
        registry.add("jurika.minio.access-key", () -> "test");
        registry.add("jurika.minio.secret-key", () -> "test-secret");
        registry.add("jurika.minio.bucket", () -> "jurika-it");
    }

    private final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    @Autowired private ClientAccessLogger logger;
    @Autowired private ClientAccessLogQueryService journal;
    @MockBean private ObjectStorage storage;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final UUID dossierId = UUID.randomUUID();

    @BeforeEach
    void seed() {
        SchemaJurikaDb.workspace(jdbc, workspaceId, "Cabinet Journal",
                "JUR-" + workspaceId.toString().substring(0, 5).toUpperCase());
        SchemaJurikaDb.utilisateur(jdbc, clientId, workspaceId);
        jdbc.update("INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale, forme_juridique, "
                        + "responsable_id, client_id) VALUES (?, ?, ?, 'SARL', ?, ?)",
                dossierId, workspaceId, "JOURNAL " + dossierId, SchemaJurikaDb.EMPLOYE_SEME, clientId);
    }

    @AfterEach
    void nettoyer() {
        RequestContextHolder.resetRequestAttributes();
        TenantContext.clear();
    }

    private void tracer(String action, String forwardedFor) {
        // Le contexte de test MOCK pose une requete factice (127.0.0.1) : sans
        // en-tete fourni, on simule un appel hors requete HTTP.
        RequestContextHolder.resetRequestAttributes();
        if (forwardedFor != null) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.addHeader("X-Forwarded-For", forwardedFor);
            req.addHeader("User-Agent", "Navigateur de test");
            req.setRemoteAddr("10.9.9.9");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        }
        logger.log(dossierId, null, action, new AuthenticatedUser(clientId, workspaceId, "c@rls.test", Role.CLIENT));
    }

    /** L'insertion est asynchrone : attend la ligne (10 s au plus). */
    private Map<String, Object> ligne(String action) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT host(ip_address) AS ip, user_agent, user_id, workspace_id "
                            + "FROM dataroom_client_access_log WHERE dossier_id = ? AND action = ?",
                    dossierId, action);
            if (!rows.isEmpty()) {
                assertThat(rows).hasSize(1);
                return rows.get(0);
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Aucune ligne " + action + " dans le journal d'acces du client");
    }

    @Test
    void la_consultation_est_ecrite_avec_l_adresse_et_relue_par_le_panneau() throws Exception {
        tracer("VIEW_DOSSIER", "203.0.113.7, 10.0.0.1");

        Map<String, Object> l = ligne("VIEW_DOSSIER");
        assertThat(l.get("ip")).isEqualTo("203.0.113.7");
        assertThat(l.get("user_agent")).isEqualTo("Navigateur de test");
        assertThat(l.get("user_id")).isEqualTo(clientId);
        assertThat(l.get("workspace_id")).isEqualTo(workspaceId);

        TenantContext.set(workspaceId);
        AccessLogPage page = journal.findByDossier(dossierId, 50, 0);
        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.items().get(0).action()).isEqualTo("VIEW_DOSSIER");
        assertThat(page.items().get(0).ipAddress()).isEqualTo("203.0.113.7");
    }

    @Test
    void une_adresse_ipv6_est_ecrite() throws Exception {
        tracer("PREVIEW_DOC", "2001:db8::1");
        assertThat(ligne("PREVIEW_DOC").get("ip")).isEqualTo("2001:db8::1");
    }

    @Test
    void sans_requete_http_la_trace_est_ecrite_sans_adresse() throws Exception {
        tracer("DOWNLOAD_DOC", null);
        Map<String, Object> l = ligne("DOWNLOAD_DOC");
        assertThat(l.get("ip")).isNull();
        assertThat(l.get("user_id")).isEqualTo(clientId);
    }

    @Test
    void une_adresse_illisible_est_remplacee_par_null_sans_perdre_la_trace() throws Exception {
        tracer("PRINT_DOC", "inconnu");
        Map<String, Object> l = ligne("PRINT_DOC");
        assertThat(l.get("ip")).isNull();
        assertThat(l.get("user_agent")).isEqualTo("Navigateur de test");
    }
}
