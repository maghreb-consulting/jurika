package ma.jurika.workflow.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import ma.jurika.workflow.domain.model.WorkflowType;
import ma.jurika.workflow.integration.SchemaJurikaDb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L6b (E15, parcours de bout en bout « approbation des comptes », « modification ») :
 * le ticket d'un parcours qui choisit sa societe a l'etape 1 est rattache a ce dossier.
 *
 * <p>Constate sur le Z440 : le ticket d'approbation des comptes restait sans dossier ; sa
 * fiche affichait « Forme juridique du dossier inconnue », ne proposait ni decisions ni
 * demarches, et le ticket ne pouvait jamais etre clos. PostgreSQL reel, role jurika_app.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RattachementTicketDossierIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("jurika_wf_rattachement")
                    .withUsername("jurika_it")
                    .withPassword("jurika_it")
                    .withInitScript("testcontainers-init.sql");

    private static EntityManagerFactory emf;
    private EntityManager em;
    private WorkflowUseCases useCases;
    private JdbcTemplate owner;

    private final UUID workspace = UUID.randomUUID();
    private final UUID autreWorkspace = UUID.randomUUID();
    private UUID ticket;
    private UUID dossier;

    @BeforeAll
    void bootstrap() {
        POSTGRES.start();
        SchemaJurikaDb.migrer(POSTGRES);
        owner = SchemaJurikaDb.proprietaire(POSTGRES);
        SchemaJurikaDb.workspace(owner, workspace, "Cabinet Rattachement", "JUR-RT001");
        SchemaJurikaDb.workspace(owner, autreWorkspace, "Autre cabinet", "JUR-RT002");
        Map<String, Object> props = new HashMap<>();
        props.put("jakarta.persistence.jdbc.url", POSTGRES.getJdbcUrl());
        props.put("jakarta.persistence.jdbc.user", "jurika_app");
        props.put("jakarta.persistence.jdbc.password", "jurika_app_it");
        props.put("jakarta.persistence.jdbc.driver", "org.postgresql.Driver");
        emf = Persistence.createEntityManagerFactory("workflow-it", props);
        em = emf.createEntityManager();
    }

    @AfterAll
    void teardown() {
        if (em != null && em.isOpen()) em.close();
        if (emf != null && emf.isOpen()) emf.close();
    }

    @BeforeEach
    void resetFixture() {
        useCases = new WorkflowUseCases(null, null, null, null, null, null);
        ReflectionTestUtils.setField(useCases, "em", em);
        owner.update("DELETE FROM tickets");
        owner.update("DELETE FROM entreprise_dossiers");
        dossier = UUID.randomUUID();
        ticket = UUID.randomUUID();
        insertDossier(dossier, workspace, "ATLAS NORD SARL");
        owner.update("""
                        INSERT INTO tickets (id, workspace_id, reference, titre, type, cree_par_id, dossier_id)
                        VALUES (?, ?, 'RT-1', 'Approbation des comptes', 'PV_AGO', ?, NULL)
                        """, ticket, workspace, SchemaJurikaDb.EMPLOYE_SEME);
    }

    @Test
    @DisplayName("Parcours a societe choisie : approbation des comptes, modification, dissolution... (pas creation ni reprise)")
    void typesConcernes() {
        assertThat(WorkflowUseCases.TYPES_SOCIETE_CHOISIE).contains(WorkflowType.PV_AGO, WorkflowType.MODIFICATION,
                WorkflowType.DISSOLUTION, WorkflowType.LIQUIDATION, WorkflowType.SUCCURSALE_MA,
                WorkflowType.FERMETURE_SUCCURSALE);
        assertThat(WorkflowUseCases.TYPES_SOCIETE_CHOISIE).doesNotContain(WorkflowType.CREATION, WorkflowType.IMPORT,
                WorkflowType.SUCCURSALE_ETR);
    }

    @Test
    @DisplayName("Ticket sans dossier : rattache au dossier choisi a l'etape 1")
    void ticketSansDossier_estRattache() {
        inTx(workspace, () -> useCases.lierTicketAuDossierChoisi(workspace, ticket, dossier));
        assertThat(dossierDuTicket()).isEqualTo(dossier);
    }

    @Test
    @DisplayName("Ticket deja rattache : jamais deplace")
    void ticketDejaRattache_inchange() {
        UUID autre = UUID.randomUUID();
        insertDossier(autre, workspace, "NOUR CONSEIL SARL");
        owner.update("UPDATE tickets SET dossier_id = ? WHERE id = ?", autre, ticket);
        inTx(workspace, () -> useCases.lierTicketAuDossierChoisi(workspace, ticket, dossier));
        assertThat(dossierDuTicket()).isEqualTo(autre);
    }

    @Test
    @DisplayName("Dossier d'un autre cabinet : aucun rattachement")
    void dossierAutreCabinet_refuse() {
        UUID etranger = UUID.randomUUID();
        insertDossier(etranger, autreWorkspace, "ETRANGER SARL");
        inTx(workspace, () -> useCases.lierTicketAuDossierChoisi(workspace, ticket, etranger));
        assertThat(dossierDuTicket()).isNull();
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private void insertDossier(UUID id, UUID ws, String raison) {
        owner.update("""
                        INSERT INTO entreprise_dossiers (id, workspace_id, raison_sociale, forme_juridique,
                          statut, responsable_id)
                        VALUES (?, ?, ?, 'SARL', 'ACTIVE', ?)
                        """, id, ws, raison, SchemaJurikaDb.EMPLOYE_SEME);
    }

    private Object dossierDuTicket() {
        List<Object> rows = owner.queryForList("SELECT dossier_id FROM tickets WHERE id = ?", Object.class, ticket);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void inTx(UUID ws, Runnable action) {
        em.getTransaction().begin();
        try {
            em.createNativeQuery("SELECT set_config('app.current_workspace_id', ?1, true)")
                    .setParameter(1, ws.toString())
                    .getSingleResult();
            action.run();
            em.getTransaction().commit();
        } catch (RuntimeException ex) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw ex;
        }
    }
}
