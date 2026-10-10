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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
                "jurika.jwt.algorithm=HS256"
        }
)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TicketJurikaAppIT {

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
            .withDatabaseName("jurika_it_ticket")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("testcontainers-init.sql");

    private static boolean migree;

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        migrerChaineAmont();
        registry.add("jurika.jwt.hmac-secret", () -> SECRET);
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

    // ------------------------------------------------------------------
    // Lot L1, etape E4 : reaffectation d'office (RG-DOS-03) et transfert accepte
    // (RG-DOS-02) : le dossier ET ses tickets changent de responsable, trace en base.
    // ------------------------------------------------------------------

    static final UUID SUPERVISEUR_A = UUID.fromString("33333333-3333-3333-3333-0000000005a1");

    @Test
    @Order(20)
    void reaffectation_d_office_par_le_superviseur() throws Exception {
        owner.execute("CREATE TABLE IF NOT EXISTS u_l1 AS SELECT * FROM users WHERE id = '" + KARIM + "'");
        owner.update("UPDATE u_l1 SET id = ?, role = 'SUPERVISEUR', email = 'sup@rls.test', login_email = 'sup@rls.test'",
                SUPERVISEUR_A);
        owner.update("INSERT INTO users SELECT * FROM u_l1 WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = ?)", SUPERVISEUR_A);
        owner.execute("DROP TABLE u_l1");
        String superviseur = jeton(SUPERVISEUR_A, WS_A, "SUPERVISEUR");
        assertThat(owner.queryForObject("SELECT responsable_id FROM entreprise_dossiers WHERE id = ?", UUID.class,
                dossierId)).isEqualTo(KARIM);

        // Sans motif : refus. Par un employe : refus.
        mvc.perform(post("/api/v1/dossiers/" + dossierId + "/reaffectation").header("Authorization", superviseur)
                        .contentType("application/json")
                        .content("{\"nouveauResponsableId\":\"" + COLLEGUE + "\",\"motif\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/dossiers/" + dossierId + "/reaffectation").header("Authorization", karim())
                        .contentType("application/json")
                        .content("{\"nouveauResponsableId\":\"" + COLLEGUE + "\",\"motif\":\"Absence\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/dossiers/" + dossierId + "/reaffectation").header("Authorization", superviseur)
                        .contentType("application/json")
                        .content("{\"nouveauResponsableId\":\"" + COLLEGUE + "\",\"motif\":\"Absence prolongee\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nature").value("FORCEE"));

        assertThat(owner.queryForObject("SELECT responsable_id FROM entreprise_dossiers WHERE id = ?", UUID.class,
                dossierId)).isEqualTo(COLLEGUE);
        assertThat(owner.queryForObject("SELECT count(*) FROM tickets WHERE dossier_id = ? "
                + "AND assigne_id IS DISTINCT FROM ?", Integer.class, dossierId, COLLEGUE)).isZero();
        assertThat(owner.queryForMap("SELECT nature, auteur_id, ancien_responsable_id, motif "
                + "FROM dossier_reaffectations WHERE dossier_id = ? ORDER BY created_at DESC LIMIT 1", dossierId))
                .containsEntry("nature", "FORCEE")
                .containsEntry("auteur_id", SUPERVISEUR_A)
                .containsEntry("ancien_responsable_id", KARIM)
                .containsEntry("motif", "Absence prolongee");

        // Historique : le nouveau responsable le lit ; l'ancien ne voit plus le dossier.
        mvc.perform(get("/api/v1/dossiers/" + dossierId + "/reaffectations")
                        .header("Authorization", jeton(COLLEGUE, WS_A, "EMPLOYE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nature").value("FORCEE"));
        mvc.perform(get("/api/v1/dossiers/" + dossierId + "/reaffectations").header("Authorization", karim()))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(21)
    void transfert_accepte_deplace_dossier_et_tickets() throws Exception {
        String collegue = jeton(COLLEGUE, WS_A, "EMPLOYE");
        MvcResult demande = mvc.perform(post("/api/v1/dossiers/" + dossierId + "/transfer-requests")
                        .header("Authorization", collegue).contentType("application/json")
                        .content("{\"toUserId\":\"" + KARIM + "\",\"motif\":\"Retour\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String demandeId = json.readTree(demande.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(post("/api/v1/dossier-transfer-requests/" + demandeId + "/accept").header("Authorization", karim()))
                .andExpect(status().is2xxSuccessful());

        assertThat(owner.queryForObject("SELECT responsable_id FROM entreprise_dossiers WHERE id = ?", UUID.class,
                dossierId)).isEqualTo(KARIM);
        assertThat(owner.queryForObject("SELECT count(*) FROM tickets WHERE dossier_id = ? "
                + "AND assigne_id IS DISTINCT FROM ?", Integer.class, dossierId, KARIM)).isZero();
        assertThat(owner.queryForMap("SELECT nature, auteur_id, transfert_id::text AS transfert "
                + "FROM dossier_reaffectations WHERE dossier_id = ? ORDER BY created_at DESC LIMIT 1", dossierId))
                .containsEntry("nature", "ACCEPTEE")
                .containsEntry("auteur_id", KARIM)
                .containsEntry("transfert", demandeId);
    }

    @Test
    @Order(22)
    void note_de_ticket_interne() throws Exception {
        // Ouverte des la creation : vide sans enregistrement.
        mvc.perform(get("/api/v1/tickets/" + ticketId + "/note").header("Authorization", karim()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu").value(""));
        // Validable meme vide, puis modifiable.
        mvc.perform(put("/api/v1/tickets/" + ticketId + "/note").header("Authorization", karim())
                        .contentType("application/json").content("{\"contenu\":\"\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/tickets/" + ticketId + "/note").header("Authorization", karim())
                        .contentType("application/json").content("{\"contenu\":\"Rappeler le greffe\"}"))
                .andExpect(status().isOk());
        assertThat(owner.queryForMap("SELECT contenu, modifie_par FROM ticket_notes WHERE ticket_id = ?", ticketId))
                .containsEntry("contenu", "Rappeler le greffe")
                .containsEntry("modifie_par", KARIM);
        // Superviseur : lecture en observation ; autre employe : 404 ; client : jamais.
        mvc.perform(get("/api/v1/tickets/" + ticketId + "/note")
                        .header("Authorization", jeton(SUPERVISEUR_A, WS_A, "SUPERVISEUR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu").value("Rappeler le greffe"));
        mvc.perform(get("/api/v1/tickets/" + ticketId + "/note")
                        .header("Authorization", jeton(COLLEGUE, WS_A, "EMPLOYE")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/tickets/" + ticketId + "/note")
                        .header("Authorization", jeton(UUID.randomUUID(), WS_A, "CLIENT")))
                .andExpect(status().isForbidden());
        // Autre cabinet : rien.
        mvc.perform(get("/api/v1/tickets/" + ticketId + "/note")
                        .header("Authorization", jeton(EMPLOYE_B, WS_B, "EMPLOYE")))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(23)
    void versions_datees_de_la_taxe_professionnelle() throws Exception {
        String corps = "{\"taxeProfessionnelle\":\"%s\",\"taxeProfessionnelleDateEffet\":%s}";
        mvc.perform(patch("/api/v1/dossiers/" + dossierId + "/identifiants").header("Authorization", karim())
                        .contentType("application/json").content(String.format(corps, "TP-2025", "\"2025-01-01\"")))
                .andExpect(status().isOk());
        // Nouvelle patente sans date d'effet : version creee, date vide (jamais inventee).
        mvc.perform(patch("/api/v1/dossiers/" + dossierId + "/identifiants").header("Authorization", karim())
                        .contentType("application/json").content(String.format(corps, "TP-2026", "null")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/dossiers/" + dossierId + "/taxe-professionnelle/versions")
                        .header("Authorization", karim()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].numero").value("TP-2025"))
                .andExpect(jsonPath("$[0].dateEffet").value("2025-01-01"))
                .andExpect(jsonPath("$[0].enVigueur").value(false))
                .andExpect(jsonPath("$[1].numero").value("TP-2026"))
                .andExpect(jsonPath("$[1].dateEffet").doesNotExist())
                .andExpect(jsonPath("$[1].enVigueur").value(true));
        // La date se complete plus tard sur la version en vigueur.
        mvc.perform(patch("/api/v1/dossiers/" + dossierId + "/identifiants").header("Authorization", karim())
                        .contentType("application/json").content(String.format(corps, "TP-2026", "\"2026-02-01\"")))
                .andExpect(status().isOk());
        assertThat(owner.queryForObject("SELECT count(*) FROM dossier_tp_versions WHERE dossier_id = ?",
                Integer.class, dossierId)).isEqualTo(2);
        assertThat(owner.queryForObject("SELECT date_effet::text FROM dossier_tp_versions WHERE numero = 'TP-2026'",
                String.class)).isEqualTo("2026-02-01");
        mvc.perform(get("/api/v1/dossiers/" + dossierId + "/taxe-professionnelle/versions")
                        .header("Authorization", jeton(SUPERVISEUR_A, WS_A, "SUPERVISEUR")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/dossiers/" + dossierId + "/taxe-professionnelle/versions")
                        .header("Authorization", jeton(COLLEGUE, WS_A, "EMPLOYE")))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(24)
    void debours_visibles_par_le_client_selon_sa_permission() throws Exception {
        UUID client = UUID.fromString("33333333-3333-3333-3333-0000000c11e1");
        owner.execute("CREATE TABLE IF NOT EXISTS u_l1c AS SELECT * FROM users WHERE id = '" + KARIM + "'");
        owner.update("UPDATE u_l1c SET id = ?, role = 'CLIENT', email = 'client@rls.test', login_email = 'client@rls.test'",
                client);
        owner.update("INSERT INTO users SELECT * FROM u_l1c WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = ?)", client);
        owner.execute("DROP TABLE u_l1c");
        owner.update("UPDATE entreprise_dossiers SET client_id = ? WHERE id = ?", client, dossierId);
        String jetonClient = jeton(client, WS_A, "CLIENT");

        // Sans reglage : consultation permise (valeur par defaut) -> le client lit.
        mvc.perform(get("/api/v1/tickets/" + ticketId + "/debours").header("Authorization", jetonClient))
                .andExpect(status().isOk());
        // Un autre client du cabinet : rien.
        mvc.perform(get("/api/v1/tickets/" + ticketId + "/debours")
                        .header("Authorization", jeton(UUID.randomUUID(), WS_A, "CLIENT")))
                .andExpect(status().isNotFound());
        // Consultation retiree : refus.
        owner.update("INSERT INTO dataroom_settings (dossier_id, workspace_id, perm_consultation) VALUES (?, ?, FALSE) "
                + "ON CONFLICT (dossier_id) DO UPDATE SET perm_consultation = FALSE", dossierId, WS_A);
        mvc.perform(get("/api/v1/tickets/" + ticketId + "/debours").header("Authorization", jetonClient))
                .andExpect(status().isForbidden());
        // Le client n'ecrit jamais ; un employe non responsable non plus.
        mvc.perform(post("/api/v1/tickets/" + ticketId + "/debours").header("Authorization", jetonClient)
                        .contentType("application/json").content("{\"libelle\":\"x\",\"categorie\":\"FRAIS_TRIBUNAL\","
                                + "\"montant\":10,\"dateEngagement\":\"2026-10-01\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/tickets/" + ticketId + "/debours")
                        .header("Authorization", jeton(COLLEGUE, WS_A, "EMPLOYE")))
                .andExpect(status().isNotFound());
    }

    /**
     * RG-TKT-07 (cahier des charges mis a jour le 2026-10-10), point par point, apres
     * les scenarios precedents (note "Rappeler le greffe" posee par KARIM, responsable).
     */
    @Test
    @Order(25)
    void note_rg_tkt_07_suit_le_ticket_lecture_seule_apres_cloture_conservee_interne() throws Exception {
        String collegue = jeton(COLLEGUE, WS_A, "EMPLOYE");
        // 1. Elle suit le ticket a la reaffectation : le nouveau responsable la lit et
        //    l'ecrit ; l'ancien ne la voit plus.
        mvc.perform(post("/api/v1/dossiers/" + dossierId + "/reaffectation")
                        .header("Authorization", jeton(SUPERVISEUR_A, WS_A, "SUPERVISEUR"))
                        .contentType("application/json")
                        .content("{\"nouveauResponsableId\":\"" + COLLEGUE + "\",\"motif\":\"Conges\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/tickets/" + ticketId + "/note").header("Authorization", collegue))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu").value("Rappeler le greffe"));
        mvc.perform(put("/api/v1/tickets/" + ticketId + "/note").header("Authorization", collegue)
                        .contentType("application/json").content("{\"contenu\":\"Rappeler le greffe ; fait le 10\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/tickets/" + ticketId + "/note").header("Authorization", karim()))
                .andExpect(status().isNotFound());

        // 2. Lecture seule apres la cloture : lisible, plus modifiable.
        owner.update("UPDATE tickets SET statut = 'CLOTURE_DOSSIER' WHERE id = ?", ticketId);
        mvc.perform(get("/api/v1/tickets/" + ticketId + "/note").header("Authorization", collegue))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu").value("Rappeler le greffe ; fait le 10"));
        mvc.perform(put("/api/v1/tickets/" + ticketId + "/note").header("Authorization", collegue)
                        .contentType("application/json").content("{\"contenu\":\"apres cloture\"}"))
                .andExpect(status().isConflict());

        // 3. Conservee avec le dossier : la base refuse de la perdre avec son ticket.
        assertThat(owner.queryForObject("SELECT confdeltype FROM pg_constraint "
                + "WHERE conname = 'ticket_notes_ticket_id_fkey'", String.class)).isEqualTo("r");
        assertThat(owner.queryForObject("SELECT contenu FROM ticket_notes WHERE ticket_id = ?", String.class,
                ticketId)).isEqualTo("Rappeler le greffe ; fait le 10");

        // 4. Jamais visible du client : ni par sa route, ni dans la fiche du ticket.
        mvc.perform(get("/api/v1/tickets/" + ticketId + "/note")
                        .header("Authorization", jeton(UUID.randomUUID(), WS_A, "CLIENT")))
                .andExpect(status().isForbidden());
        String fiche = mvc.perform(get("/api/v1/tickets/" + ticketId).header("Authorization", collegue))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(fiche).doesNotContain("Rappeler le greffe");
    }

    /**
     * Ecrans du lot L1 (demande du 2026-10-10) : historique des responsables avec les noms,
     * et vue du superviseur sur les dossiers rattrapes par V28 (D1), a verifier.
     */
    @Test
    @Order(26)
    void historique_nomme_et_rattrapages_a_verifier_par_le_superviseur() throws Exception {
        String superviseur = jeton(SUPERVISEUR_A, WS_A, "SUPERVISEUR");
        // Historique : noms de l'ancien et du nouveau responsable, et de l'auteur.
        mvc.perform(get("/api/v1/dossiers/" + dossierId + "/reaffectations").header("Authorization", superviseur))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nature").value("FORCEE"))
                .andExpect(jsonPath("$[0].nouveauResponsableNom").isNotEmpty())
                .andExpect(jsonPath("$[0].auteurNom").isNotEmpty());

        // Un rattrapage comme ceux poses par V28 sur le Z440.
        UUID rattrapage = UUID.randomUUID();
        owner.update("INSERT INTO dossier_reaffectations (id, workspace_id, dossier_id, nouveau_responsable_id, nature, motif) "
                + "VALUES (?, ?, ?, ?, 'RATTRAPAGE', 'Migration V28 (lot L1) : dossier sans responsable')",
                rattrapage, WS_A, dossierId, COLLEGUE);
        mvc.perform(get("/api/v1/dossiers/rattrapages").header("Authorization", superviseur))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(rattrapage.toString()))
                .andExpect(jsonPath("$[0].raisonSociale").value("Societe L0"))
                .andExpect(jsonPath("$[0].responsableActuelNom").isNotEmpty())
                .andExpect(jsonPath("$[0].verifieLe").doesNotExist());
        // Reserve au superviseur.
        mvc.perform(get("/api/v1/dossiers/rattrapages").header("Authorization", karim()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/dossiers/reaffectations/" + rattrapage + "/verification").header("Authorization", karim()))
                .andExpect(status().isForbidden());

        // Le superviseur marque le rattrapage verifie : trace (qui, quand).
        mvc.perform(post("/api/v1/dossiers/reaffectations/" + rattrapage + "/verification")
                        .header("Authorization", superviseur))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifieLe").isNotEmpty())
                .andExpect(jsonPath("$.verifieParNom").isNotEmpty());
        assertThat(owner.queryForObject("SELECT verifie_par FROM dossier_reaffectations WHERE id = ?", UUID.class,
                rattrapage)).isEqualTo(SUPERVISEUR_A);
        // Seul un rattrapage se verifie.
        UUID forcee = owner.queryForObject("SELECT id FROM dossier_reaffectations WHERE nature = 'FORCEE' LIMIT 1",
                UUID.class);
        mvc.perform(post("/api/v1/dossiers/reaffectations/" + forcee + "/verification").header("Authorization", superviseur))
                .andExpect(status().isNotFound());
    }
}

