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
 * Lot L6b (E15, parcours de bout en bout " import de dossier ") : la finalisation d'une
 * reprise sur PostgreSQL reel (TestContainers, vraies migrations, role jurika_app).
 *
 * <p>Deux defauts trouves a l'ecran sur le Z440 :
 * <ol>
 *   <li>un brouillon enregistre apres la validation d'une etape remplace son sac
 *       (step2 sans la niche {@code siege}, step3 sans {@code capital}) : la reprise
 *       finalisee laissait le siege et le capital du dossier VIDES, sans un mot ;</li>
 *   <li>un ICE deja porte par un autre dossier du cabinet : HTTP 500 "Erreur interne du
 *       serveur" a la finalisation (index unique viole), la saisie perdue sans
 *       explication.</li>
 * </ol>
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImportConsolidationIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("jurika_wf_import")
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
        SchemaJurikaDb.workspace(owner, workspace, "Cabinet Import", "JUR-IM001");
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
        // Dossier ouvert avec le ticket d'import (stub : ni ICE, ni siege, ni capital).
        owner.update("""
                        INSERT INTO entreprise_dossiers (id, workspace_id, raison_sociale, forme_juridique,
                          statut, responsable_id)
                        VALUES (?, ?, 'ATLAS IMPORT SARL', 'SARL', 'EN_CONSTITUTION', ?)
                        """, dossier, workspace, SchemaJurikaDb.EMPLOYE_SEME);
        owner.update("""
                        INSERT INTO tickets (id, workspace_id, reference, titre, type, cree_par_id, dossier_id)
                        VALUES (?, ?, 'IM-1', 'Import Atlas', 'IMPORT', ?, ?)
                        """, ticket, workspace, SchemaJurikaDb.EMPLOYE_SEME, dossier);
    }

    @Test
    @DisplayName("Sacs d'etape a plat (niches perdues) : siege, ville, greffe, capital et ICE (chiffres) arrivent au dossier")
    void sacsAPlat_siegeEtCapitalConsolides() {
        Map<String, Object> data = new HashMap<>();
        // Ce que le serveur portait sur le Z440 apres la reprise : les sacs remplaces par
        // le brouillon a plat (aucune niche denomination / siege / capital).
        data.put("step1", Map.of("denomination", "Atlas Import SARL", "ice", "001 234 567 000 089",
                "rcNumero", "123456", "ifFiscal", "12345678", "formeJuridique", "SARL"));
        data.put("step2", Map.of("adresse", "45 boulevard de la Demonstration", "commune", "Casablanca",
                "villeGreffe", "Casablanca"));
        data.put("step3", Map.of("capitalSocialMad", 100000, "nombreParts", 1000));

        inTx(() -> postCompletion(data));

        assertThat(colonne("raison_sociale")).isEqualTo("Atlas Import SARL");
        assertThat(colonne("ice")).isEqualTo("001234567000089");
        assertThat(colonne("rc_numero")).isEqualTo("123456");
        assertThat(colonne("identifiant_fiscal")).isEqualTo("12345678");
        assertThat(colonne("adresse_siege")).isEqualTo("45 boulevard de la Demonstration");
        assertThat(colonne("ville")).isEqualTo("Casablanca");
        // Ville du greffe de l'etape 2 : sans elle, l'annonce legale de la modification
        // suivante etait refusee (VILLE_GREFFE).
        assertThat(colonne("rc_tribunal")).isEqualTo("Casablanca");
        assertThat(new BigDecimal(String.valueOf(colonne("capital_social_mad")))).isEqualByComparingTo("100000");
        assertThat(colonne("statut")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("ICE deja porte par un autre dossier du cabinet : refus nomme (plus de 500), dossier intact")
    void iceDejaPris_refusNomme() {
        UUID autre = UUID.randomUUID();
        owner.update("""
                        INSERT INTO entreprise_dossiers (id, workspace_id, raison_sociale, forme_juridique, ice,
                          statut, responsable_id)
                        VALUES (?, ?, 'NOUR CONSEIL SARL', 'SARL', '001234567000089', 'ACTIVE', ?)
                        """, autre, workspace, SchemaJurikaDb.EMPLOYE_SEME);
        Map<String, Object> data = new HashMap<>();
        data.put("step1", Map.of("denomination", Map.of("denomination", "Atlas Import SARL",
                "ice", "001234567000089", "rcNumero", "123456")));

        assertThatThrownBy(() -> inTx(() -> postCompletion(data)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("001234567000089")
                .hasMessageContaining("NOUR CONSEIL SARL");
        assertThat(colonne("ice")).isNull();
        assertThat(colonne("statut")).isEqualTo("EN_CONSTITUTION");
    }

    @Test
    @DisplayName("Denomination deja celle d'un autre dossier vivant : refus nomme ; le meme dossier n'est pas un doublon")
    void denominationDejaPrise_refusNomme() {
        owner.update("""
                        INSERT INTO entreprise_dossiers (id, workspace_id, raison_sociale, forme_juridique,
                          statut, responsable_id)
                        VALUES (?, ?, 'NOUR CONSEIL SARL', 'SARL', 'ACTIVE', ?)
                        """, UUID.randomUUID(), workspace, SchemaJurikaDb.EMPLOYE_SEME);
        assertThatThrownBy(() -> inTx(() -> useCases.refuserDoublonDossier(workspace, dossier, null, "nour conseil sarl")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("d\u00e9nomination");
        // Sa propre denomination (meme dossier) n'est jamais un doublon.
        inTx(() -> useCases.refuserDoublonDossier(workspace, dossier, null, "ATLAS IMPORT SARL"));
    }

    // ?? Helpers ????????????????????????????????????????????????????????

    private void postCompletion(Map<String, Object> data) {
        ReflectionTestUtils.invokeMethod(useCases, "applyPostCompletion", WorkflowType.IMPORT, workspace, ticket, data);
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
