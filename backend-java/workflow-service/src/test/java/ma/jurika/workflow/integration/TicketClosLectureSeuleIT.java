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
 * Lot L0, etape E23 (RG-TKT-11) : un ticket clos ({@code CLOTURE_DOSSIER})
 * s'ouvre en lecture seule, sans aucune action possible : le serveur refuse
 * toute ecriture du parcours (etape, sauvegarde, pieces). Role d'execution
 * jurika_app, vraies migrations.
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
class TicketClosLectureSeuleIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_ticket_clos")
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
        SchemaJurikaDb.workspace(jdbc, workspaceId, "Cabinet Clos", "JUR-K0001");
        jdbc.update("""
                INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale, forme_juridique,
                                                rc_numero, rc_tribunal, capital_social_mad, ville,
                                                responsable_id)
                VALUES (?, ?, 'NOVA INDUSTRIE', 'SARL', '123456', 'CASABLANCA', 100000, 'CASABLANCA', ?)
                """, dossierId, workspaceId, EMPLOYE);
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

    private WorkflowProgress demarrer() {
        return dansLeWorkspace(() -> workflows.startOrResume(workspaceId, ticketId, WorkflowType.CREATION, EMPLOYE));
    }

    private void clore() {
        jdbc.update("UPDATE tickets SET statut = 'CLOTURE_DOSSIER' WHERE id = ?", ticketId);
    }

    private String donnees(UUID progression) {
        return jdbc.queryForObject("SELECT data::text FROM workflow_progress WHERE id = ?", String.class, progression);
    }

    @Test
    void ticket_clos_refuse_toute_ecriture_du_parcours() {
        WorkflowProgress p = demarrer();
        clore();
        String avant = donnees(p.id());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> dansLeWorkspace(() -> workflows.save(workspaceId, ticketId, 1,
                Map.of("step1", Map.of("denomination", "APRES CLOTURE")), EMPLOYE)))
                .isInstanceOf(ma.jurika.common.exception.ConflictException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> dansLeWorkspace(() -> workflows.executeStep(workspaceId, ticketId, 1,
                Map.of("denomination", "APRES CLOTURE"), EMPLOYE)))
                .isInstanceOf(ma.jurika.common.exception.ConflictException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> dansLeWorkspace(() -> workflows.registerPiece(workspaceId, ticketId,
                new RegisterPieceRequest("CN", "Certificat", "cn.pdf", 1L, "application/pdf", 1))))
                .isInstanceOf(ma.jurika.common.exception.ConflictException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> dansLeWorkspace(() -> workflows.unregisterPiece(workspaceId, ticketId, "CN")))
                .isInstanceOf(ma.jurika.common.exception.ConflictException.class);

        assertThat(donnees(p.id())).isEqualTo(avant);
        // La lecture reste possible (resume du ticket clos).
        assertThat(dansLeWorkspace(() -> workflows.get(workspaceId, ticketId)).id()).isEqualTo(p.id());
    }

    @Test
    void ticket_en_cours_accepte_la_sauvegarde() {
        WorkflowProgress p = demarrer();
        dansLeWorkspace(() -> workflows.save(workspaceId, ticketId, 1,
                Map.of("step1", Map.of("denomination", "EN COURS")), EMPLOYE));
        assertThat(donnees(p.id())).contains("EN COURS");
    }
}
