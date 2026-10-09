package ma.jurika.workflow.integration;

import ma.jurika.common.security.TenantContext;
import ma.jurika.workflow.api.WorkflowController.RegisterPieceRequest;
import ma.jurika.workflow.application.MagasinVariables;
import ma.jurika.workflow.application.WorkflowFinalizationService;
import ma.jurika.workflow.application.WorkflowProgressLookup;
import ma.jurika.workflow.application.WorkflowUseCases;
import ma.jurika.workflow.domain.model.VariableDuDossier;
import ma.jurika.workflow.domain.model.WorkflowProgress;
import ma.jurika.workflow.domain.model.WorkflowType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lot L0, etape E16 : workflow-service en role d'execution jurika_app (RLS
 * active, garde « hors transaction »), sur les vraies migrations. Un test par
 * chemin inventorie (L0_inventaire_sans_workspace.md, WF1 et section 2.6), et
 * chacun attend des LIGNES : sans workspace effectif, ces chemins rendaient
 * vide en silence (identite de la societe, magasin de variables, dossier de
 * fin de parcours non cree).
 *
 * <p>Le workspace courant est pose AVANT l'appel, comme le fait JwtAuthFilter
 * sur les routes authentifiees ; la route interne WF1, sans JWT, le recoit en
 * parametre.
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
@AutoConfigureMockMvc
class WorkflowJurikaAppIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_workflow")
            .withUsername("jurika_it")
            .withPassword("jurika_it_pwd")
            .withInitScript("testcontainers-init.sql")
            .withReuse(false);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        SchemaJurikaDb.migrer(POSTGRES);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        // L'application tourne en jurika_app (la RLS s'applique) ; Flyway migre
        // avec le proprietaire.
        registry.add("spring.datasource.username", () -> "jurika_app");
        registry.add("spring.datasource.password", () -> "jurika_app_it");
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    private static final UUID EMPLOYE = SchemaJurikaDb.EMPLOYE_SEME;

    /** Preparation et assertions en PROPRIETAIRE, hors RLS. */
    private final JdbcTemplate jdbc = SchemaJurikaDb.proprietaire(POSTGRES);
    @Autowired private MockMvc mvc;
    @Autowired private WorkflowUseCases workflows;
    @Autowired private WorkflowProgressLookup lookup;
    @Autowired private WorkflowFinalizationService finalisation;
    @Autowired private MagasinVariables magasin;

    private UUID workspaceId;
    private UUID dossierId;
    private UUID ticketId;

    @BeforeEach
    void seed() {
        jdbc.execute("DELETE FROM dossier_variables");
        jdbc.execute("DELETE FROM workflow_progress");
        jdbc.execute("DELETE FROM tickets");
        jdbc.execute("DELETE FROM entreprise_dossiers");
        jdbc.execute("DELETE FROM workspaces");

        workspaceId = UUID.randomUUID();
        dossierId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
        SchemaJurikaDb.workspace(jdbc, workspaceId, "Cabinet Workflow", "JUR-F0001");
        jdbc.update("""
                INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale, forme_juridique,
                                                rc_numero, rc_tribunal, capital_social_mad, ville)
                VALUES (?, ?, 'NOVA INDUSTRIE', 'SARL', '123456', 'CASABLANCA', 100000, 'CASABLANCA')
                """, dossierId, workspaceId);
        jdbc.update("""
                INSERT INTO tickets(id, workspace_id, reference, titre, type, statut, cree_par_id)
                VALUES (?, ?, 'T-2026-00901', 'Creation SARL', 'CREATION', 'CREATION_TICKET', ?)
                """, ticketId, workspaceId, EMPLOYE);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** Comme JwtAuthFilter : le workspace est pose avant le proxy transactionnel. */
    private <T> T dansLeWorkspace(Supplier<T> appel) {
        TenantContext.set(workspaceId);
        try {
            return appel.get();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void wf1_identite_interne_du_dossier_lue_sans_jwt() throws Exception {
        mvc.perform(get("/internal/dossiers/" + dossierId + "/identite").param("workspaceId", workspaceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.denomination").value("NOVA INDUSTRIE"))
                .andExpect(jsonPath("$.rcNumero").value("123456"));
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void wf1_identite_d_un_autre_workspace_reste_vide() throws Exception {
        mvc.perform(get("/internal/dossiers/" + dossierId + "/identite").param("workspaceId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.denomination").doesNotExist());
    }

    @Test
    void parcours_demarre_sauve_relit_et_gere_les_pieces() {
        WorkflowProgress demarre = dansLeWorkspace(() ->
                workflows.startOrResume(workspaceId, ticketId, WorkflowType.CREATION, EMPLOYE));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workflow_progress WHERE ticket_id = ? AND workspace_id = ?",
                Integer.class, ticketId, workspaceId)).isEqualTo(1);

        dansLeWorkspace(() -> workflows.save(workspaceId, ticketId, 1,
                Map.of("step1", Map.of("denomination", "NOVA INDUSTRIE")), EMPLOYE));
        WorkflowProgress relu = dansLeWorkspace(() -> workflows.get(workspaceId, ticketId));
        assertThat(relu.id()).isEqualTo(demarre.id());
        assertThat(relu.data()).containsKey("step1");

        dansLeWorkspace(() -> workflows.registerPiece(workspaceId, ticketId,
                new RegisterPieceRequest("CN", "Certificat negatif", "cn.pdf", 10L, "application/pdf", 1)));
        assertThat(jdbc.queryForObject("SELECT data->'pieces'->'CN' IS NOT NULL FROM workflow_progress WHERE id = ?",
                Boolean.class, demarre.id())).isTrue();
        dansLeWorkspace(() -> workflows.unregisterPiece(workspaceId, ticketId, "CN"));
        assertThat(jdbc.queryForObject("SELECT data->'pieces'->'CN' IS NOT NULL FROM workflow_progress WHERE id = ?",
                Boolean.class, demarre.id())).isFalse();

        // WorkflowProgressLookup : transaction NEUVE (REQUIRES_NEW), meme workspace.
        assertThat(dansLeWorkspace(() -> lookup.findInNewTransaction(workspaceId, ticketId)))
                .hasValueSatisfying(p -> assertThat(p.id()).isEqualTo(demarre.id()));
    }

    @Test
    void magasin_de_variables_ecrit_puis_relu() {
        dansLeWorkspace(() -> {
            magasin.poser(workspaceId, ticketId, "DENOMINATION", "NOVA INDUSTRIE",
                    VariableDuDossier.Origine.SAISIE, EMPLOYE, "test");
            return null;
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dossier_variables WHERE ticket_id = ? AND workspace_id = ?",
                Integer.class, ticketId, workspaceId)).isEqualTo(1);

        List<VariableDuDossier> lues = dansLeWorkspace(() -> magasin.lire(workspaceId, ticketId));
        assertThat(lues).hasSize(1);
        Map<String, Object> charge = dansLeWorkspace(() -> magasin.lirePourGeneration(workspaceId, ticketId));
        assertThat(charge).isNotEmpty();
    }

    @Test
    void fin_de_parcours_creation_cree_le_dossier_dans_le_workspace() {
        UUID cree = dansLeWorkspace(() -> finalisation.createEntrepriseDossierInNewTransaction(
                workspaceId, ticketId, EMPLOYE,
                Map.of("denomination", Map.of("denomination", "ATLAS CONSEIL", "formeJuridique", "SARL_AU"))));

        assertThat(cree).isNotNull();
        assertThat(jdbc.queryForObject("SELECT raison_sociale FROM entreprise_dossiers WHERE id = ? AND workspace_id = ?",
                String.class, cree, workspaceId)).isEqualTo("ATLAS CONSEIL");
    }
}
