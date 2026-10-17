package ma.jurika.workflow.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import ma.jurika.common.exception.ValidationException;
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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L6b (E14, parcours de creation de bout en bout) : la finalisation d'une creation sur
 * PostgreSQL reel. Constate sur le Z440 (journaux de workflow-service) : un ICE deja porte par
 * un autre dossier du cabinet violait uq_dossier_workspace_ice_when_present pendant la
 * consolidation : HTTP 500 "Erreur interne du serveur" au bouton "Finaliser le dossier".
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CreationDoublonIceIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("jurika_wf_creation_ice")
                    .withUsername("jurika_it")
                    .withPassword("jurika_it")
                    .withInitScript("testcontainers-init.sql");

    private static EntityManagerFactory emf;
    private EntityManager em;
    private WorkflowUseCases useCases;
    private JdbcTemplate owner;

    private final UUID workspace = UUID.randomUUID();
    private UUID ticket;
    private UUID dossier;

    @BeforeAll
    void bootstrap() {
        POSTGRES.start();
        SchemaJurikaDb.migrer(POSTGRES);
        owner = SchemaJurikaDb.proprietaire(POSTGRES);
        SchemaJurikaDb.workspace(owner, workspace, "Cabinet Creation", "JUR-CR001");
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
        // Dossier ouvert avec le ticket de creation (stub : ni ICE, ni siege, ni capital).
        owner.update("""
                        INSERT INTO entreprise_dossiers (id, workspace_id, raison_sociale, forme_juridique,
                          statut, responsable_id)
                        VALUES (?, ?, 'ATLAS PARCOURS SARL', 'SARL', 'EN_CONSTITUTION', ?)
                        """, dossier, workspace, SchemaJurikaDb.EMPLOYE_SEME);
        owner.update("""
                        INSERT INTO tickets (id, workspace_id, reference, titre, type, cree_par_id, dossier_id)
                        VALUES (?, ?, 'CR-1', 'Constitution Atlas', 'CREATION', ?, ?)
                        """, ticket, workspace, SchemaJurikaDb.EMPLOYE_SEME, dossier);
    }

    @Test
    @DisplayName("Creation : ICE deja porte par un autre dossier du cabinet : refus nomme (plus de 500), dossier intact")
    void iceDejaPris_refusNomme() {
        owner.update("""
                        INSERT INTO entreprise_dossiers (id, workspace_id, raison_sociale, forme_juridique, ice,
                          statut, responsable_id)
                        VALUES (?, ?, 'NOUR CONSEIL SARL', 'SARL', '001234567000089', 'ACTIVE', ?)
                        """, UUID.randomUUID(), workspace, SchemaJurikaDb.EMPLOYE_SEME);
        Map<String, Object> data = new HashMap<>();
        data.put("step1", Map.of("denomination", "Atlas Parcours SARL", "ice", "001 234 567 000 089"));
        data.put("step3", Map.of("capitalSocialMad", 10000, "nombreParts", 100));

        assertThatThrownBy(() -> inTx(() -> postCompletion(data)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("001234567000089")
                .hasMessageContaining("NOUR CONSEIL SARL");
        assertThat(colonne("ice")).isNull();
        assertThat(colonne("statut")).isEqualTo("EN_CONSTITUTION");
    }

    @Test
    @DisplayName("Creation : ICE libre, saisi formate : consolide en chiffres avec le capital")
    void iceLibre_consolideEnChiffres() {
        Map<String, Object> data = new HashMap<>();
        data.put("step1", Map.of("denomination", "Atlas Parcours SARL", "ice", "001 234 567 000 089"));
        data.put("step3", Map.of("capitalSocialMad", 10000, "nombreParts", 100));

        inTx(() -> postCompletion(data));

        assertThat(colonne("ice")).isEqualTo("001234567000089");
        assertThat(new BigDecimal(String.valueOf(colonne("capital_social_mad")))).isEqualByComparingTo("10000");
        assertThat(colonne("statut")).isEqualTo("ACTIVE");
    }

    // Helpers

    private void postCompletion(Map<String, Object> data) {
        ReflectionTestUtils.invokeMethod(useCases, "applyPostCompletion", WorkflowType.CREATION, workspace, ticket, data);
    }

    private Object colonne(String name) {
        List<Object> rows = owner.queryForList(
                "SELECT " + name + " FROM entreprise_dossiers WHERE id = ?", Object.class, dossier);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void inTx(Runnable action) {
        em.getTransaction().begin();
        try {
            em.createNativeQuery("SELECT set_config('app.current_workspace_id', ?1, true)")
                    .setParameter(1, workspace.toString())
                    .getSingleResult();
            action.run();
            em.getTransaction().commit();
        } catch (RuntimeException ex) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw ex;
        }
    }
}
