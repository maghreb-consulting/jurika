package ma.jurika.dataroom.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import ma.jurika.common.exception.PlanLimitException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.common.trial.TrialAccessChecker;
import ma.jurika.dataroom.application.DataroomJuridiqueService;
import ma.jurika.dataroom.application.PurgeObjetsEssaiService;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lot L7 (E4) : essai gratuit dans dataroom-service, contexte complet.
 *
 * <ul>
 *   <li>le filtre de l'essai est BRANCHE dans la chaine de securite : le lien
 *       direct de telechargement d'un cabinet en essai est refuse (402), celui
 *       d'un cabinet abonne passe le filtre (RG-ESS-05) ;</li>
 *   <li>500 Mo de stockage pendant l'essai (RG-ESS-04) ;</li>
 *   <li>purge des objets MinIO d'un essai en lecture seule, et la fonction de
 *       suppression definitive d'auth (V41) sur le schema complet de jurika_db
 *       (auth, ticket, dataroom, workflow) : toutes les lignes du cabinet
 *       disparaissent malgre les cles etrangeres (RG-ESS-10).</li>
 * </ul>
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
class EssaiDataroomIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_essai_dataroom")
            .withUsername("jurika_it")
            .withPassword("jurika_it_pwd")
            .withInitScript("testcontainers-init.sql")
            .withReuse(false);

    private static final String SECRET = "dev-only-jwt-secret-min-32-chars-not-for-prod-CHANGE-ME-12345";
    static final UUID WS_ESSAI = UUID.fromString("e5a10000-0000-0000-0000-000000000001");
    static final UUID WS_EXPIRE = UUID.fromString("e5a10000-0000-0000-0000-000000000002");
    static final UUID WS_ABONNE = UUID.fromString("e5a10000-0000-0000-0000-000000000003");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        SchemaJurikaDb.migrer(POSTGRES);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("jurika.minio.endpoint", () -> "http://localhost:9099");
        registry.add("jurika.minio.access-key", () -> "test");
        registry.add("jurika.minio.secret-key", () -> "test-secret");
        registry.add("jurika.minio.bucket", () -> "jurika-it");
    }

    /** Etat d'essai fixe par cabinet (auth-service n'est pas lance). */
    @TestConfiguration
    static class EtatEssai {
        @Bean
        TrialAccessChecker verificateurEssaiDeTest() {
            return ws -> {
                String statut = WS_ESSAI.equals(ws) ? "TRIAL_ACTIVE"
                        : WS_EXPIRE.equals(ws) ? "TRIAL_EXPIRED"
                        : WS_ABONNE.equals(ws) ? "CONVERTED" : null;
                return statut == null ? Optional.empty()
                        : Optional.of(new TrialAccessChecker.TrialState(ws, statut, 3, Instant.now(), null));
            };
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private DataroomJuridiqueService juridique;
    @Autowired private PurgeObjetsEssaiService purge;
    @MockBean private ObjectStorage storage;

    private final JdbcTemplate jdbc = SchemaJurikaDb.proprietaire(POSTGRES);
    private final UUID dossierEssai = UUID.randomUUID();

    @BeforeEach
    void seed() {
        for (UUID ws : List.of(WS_ESSAI, WS_EXPIRE, WS_ABONNE)) {
            jdbc.update("DELETE FROM essais_registre WHERE workspace_id = ?", ws);
            jdbc.update("DELETE FROM dataroom_depots WHERE workspace_id = ?", ws);
            jdbc.update("DELETE FROM dataroom_documents WHERE workspace_id = ?", ws);
            jdbc.update("DELETE FROM entreprise_dossiers WHERE workspace_id = ?", ws);
            jdbc.update("DELETE FROM workspaces WHERE id = ?", ws);
        }
        SchemaJurikaDb.workspace(jdbc, WS_ESSAI, "Cabinet Essai", "JUR-E0001");
        SchemaJurikaDb.workspace(jdbc, WS_EXPIRE, "Cabinet Expire", "JUR-E0002");
        SchemaJurikaDb.workspace(jdbc, WS_ABONNE, "Cabinet Abonne", "JUR-E0003");
        jdbc.update("""
                INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale, forme_juridique, responsable_id)
                VALUES (?, ?, 'ESSAI SARL', 'SARL', '33333333-3333-3333-3333-333333333333')
                """, dossierEssai, WS_ESSAI);
    }

    @AfterEach
    void vider() {
        TenantContext.clear();
    }

    private static String jeton(UUID ws, String role) {
        return "Bearer " + Jwts.builder()
                .issuer("jurika.ma")
                .claim("typ", "access")
                .claim("uid", "33333333-3333-3333-3333-333333333333")
                .claim("wsid", ws.toString())
                .claim("email", "employe@essai.ma")
                .claim("role", role)
                .claim("mcp", false)
                .claim("r2s", false)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void lien_direct_de_telechargement_refuse_pendant_l_essai_par_le_filtre_de_la_chaine() throws Exception {
        UUID inconnu = UUID.randomUUID();
        mvc.perform(get("/api/v1/dataroom/documents/" + inconnu + "/download")
                        .header("Authorization", jeton(WS_ESSAI, "SUPERVISEUR")))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("ESSAI_TELECHARGEMENT_INTERDIT"));
        mvc.perform(post("/api/v1/dataroom/dossiers/" + dossierEssai + "/juridique/export-zip")
                        .header("Authorization", jeton(WS_ESSAI, "SUPERVISEUR")))
                .andExpect(status().isPaymentRequired());
        // Cabinet abonne : le filtre laisse passer, le controleur repond (document inconnu).
        mvc.perform(get("/api/v1/dataroom/documents/" + inconnu + "/download")
                        .header("Authorization", jeton(WS_ABONNE, "SUPERVISEUR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void lecture_seule_refuse_les_modifications_par_le_filtre_de_la_chaine() throws Exception {
        mvc.perform(post("/api/v1/dataroom/dossiers/" + UUID.randomUUID() + "/juridique/documents")
                        .header("Authorization", jeton(WS_EXPIRE, "SUPERVISEUR")))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("ESSAI_LECTURE_SEULE"));
    }

    @Test
    void stockage_de_l_essai_limite_a_500_mo() {
        jdbc.update("""
                INSERT INTO dataroom_depots(workspace_id, dossier_id, filename, object_key, size_bytes, uploaded_by)
                VALUES (?, ?, 'gros.pdf', 'ws/x/gros.pdf', ?, '33333333-3333-3333-3333-333333333333')
                """, WS_ESSAI, dossierEssai, 500L * 1024 * 1024 - 10);
        TenantContext.set(WS_ESSAI);
        var fichier = new MockMultipartFile("file", "statuts.pdf", "application/pdf", "%PDF-1.7 statuts".getBytes());
        assertThatThrownBy(() -> juridique.uploadVersion(dossierEssai, "AUTRE", "Statuts", null, fichier,
                UUID.fromString("33333333-3333-3333-3333-333333333333")))
                .isInstanceOf(PlanLimitException.class)
                .hasMessageContaining("500 Mo");
    }

    @Test
    void purge_minio_refusee_hors_lecture_seule_et_complete_en_lecture_seule() {
        assertThatThrownBy(() -> purge.purger(WS_ESSAI)).hasMessageContaining("lecture seule");
        assertThatThrownBy(() -> purge.purger(WS_ABONNE)).hasMessageContaining("lecture seule");

        UUID dossier = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale, forme_juridique, responsable_id)
                VALUES (?, ?, 'EXPIRE SARL', 'SARL', '33333333-3333-3333-3333-333333333333')
                """, dossier, WS_EXPIRE);
        jdbc.update("""
                INSERT INTO dataroom_depots(workspace_id, dossier_id, filename, object_key, size_bytes, uploaded_by)
                VALUES (?, ?, 'ancien.pdf', 'ancienne-cle/ancien.pdf', 10, '33333333-3333-3333-3333-333333333333')
                """, WS_EXPIRE, dossier);
        when(storage.lister(anyString())).thenReturn(List.of("ws/" + WS_EXPIRE + "/dossier/x/a.pdf"));
        int n = purge.purger(WS_EXPIRE);
        assertThat(n).isEqualTo(3);
        verify(storage).delete("ws/" + WS_EXPIRE + "/dossier/x/a.pdf");
        verify(storage).delete("ancienne-cle/ancien.pdf");
        verify(storage).delete("ancienne-cle/ancien.pdf.preview.pdf");
    }

    @Test
    void suppression_definitive_d_un_essai_sur_le_schema_complet() {
        UUID dossier = UUID.randomUUID();
        UUID ticket = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale, forme_juridique, responsable_id)
                VALUES (?, ?, 'EXPIRE SARL', 'SARL', '33333333-3333-3333-3333-333333333333')
                """, dossier, WS_EXPIRE);
        jdbc.update("""
                INSERT INTO tickets(id, workspace_id, reference, titre, type, statut, dossier_id, created_at, cree_par_id)
                VALUES (?, ?, 'T-2026-09999', 'Creation', 'CREATION', 'DEROULEMENT_DEMARCHE', ?, now(),
                        '33333333-3333-3333-3333-333333333333')
                """, ticket, WS_EXPIRE, dossier);
        jdbc.update("""
                INSERT INTO dataroom_documents(workspace_id, dossier_id, ticket_id, document_type, title, object_key,
                                               filename, size_bytes)
                VALUES (?, ?, ?, 'AUTRE', 'Statuts', 'ws/k/statuts.pdf', 'statuts.pdf', 10)
                """, WS_EXPIRE, dossier, ticket);
        jdbc.update("""
                INSERT INTO essais_registre(workspace_id, workspace_code, email_empreinte, telephone_empreinte)
                VALUES (?, 'JUR-E0002', repeat('a', 64), repeat('b', 64))
                """, WS_EXPIRE);
        jdbc.update("""
                UPDATE workspaces SET trial_status = 'TRIAL_EXPIRED', subscription_status = NULL,
                       essai_expire_le = now() - interval '31 days', essai_avertissement_le = now() - interval '31 days'
                WHERE id = ?
                """, WS_EXPIRE);

        List<Map<String, Object>> lignes = jdbc.queryForList("SELECT * FROM essai_supprimer_definitivement(?)", WS_EXPIRE);
        assertThat(lignes).extracting(l -> l.get("table_nom"))
                .contains("tickets", "entreprise_dossiers", "dataroom_documents", "users", "workspaces");
        for (String table : List.of("tickets", "entreprise_dossiers", "dataroom_documents", "users")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE workspace_id = ?",
                    Long.class, WS_EXPIRE)).as(table).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workspaces WHERE id = ?", Long.class, WS_EXPIRE)).isZero();
        assertThat(jdbc.queryForObject("SELECT supprime_le IS NOT NULL FROM essais_registre WHERE workspace_id = ?",
                Boolean.class, WS_EXPIRE)).isTrue();
        // Les autres cabinets sont intacts.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workspaces WHERE id IN (?, ?)", Long.class,
                WS_ESSAI, WS_ABONNE)).isEqualTo(2L);
    }
}
