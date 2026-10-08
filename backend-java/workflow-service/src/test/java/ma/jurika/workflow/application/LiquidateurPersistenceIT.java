package ma.jurika.workflow.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ma.jurika.workflow.integration.SchemaJurikaDb;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'INTEGRATION sur PostgreSQL reel (TestContainers) du cycle
 * <b>dissolution → persistance du liquidateur → relecture par la liquidation</b>.
 *
 * <p><b>Pourquoi</b> : {@code WorkflowUseCasesHwmTest} et
 * {@code DossierIdentityQueryServiceTest} mockent l'{@link EntityManager} — ils prouvent la
 * logique Java mais <b>pas le SQL</b>. Or ce lot repose entierement sur du SQL natif :
 * un merge JSONB ({@code fiche_structuree}) et un SELECT elargi a {@code statut} et
 * {@code date_dissolution}. Une colonne absente, un cast {@code jsonb} invalide ou un
 * index de colonne decale ne serait vu qu'en production. Ce test execute le CODE DE
 * PRODUCTION contre une vraie base.
 *
 * <p>Aucun contexte Spring : les deux services n'utilisent que
 * {@code em.createNativeQuery}, on leur injecte donc un {@link EntityManager} Hibernate
 * branche sur le conteneur (unite de persistance {@code workflow-it}).
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LiquidateurPersistenceIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("jurika_wf_it")
                    .withUsername("jurika_it")
                    .withPassword("jurika_it")
                    .withInitScript("testcontainers-init.sql");

    private static EntityManagerFactory emf;
    private EntityManager em;
    private WorkflowUseCases useCases;
    private DossierIdentityQueryService identityService;

    private final UUID workspace = UUID.randomUUID();
    private final UUID autreWorkspace = UUID.randomUUID();
    private UUID dossier;

    /** Preparation et relectures de controle en PROPRIETAIRE, hors RLS (lot L0). */
    private JdbcTemplate owner;

    @BeforeAll
    void bootstrap() {
        POSTGRES.start();
        // Lot L0 (E16) : vraies migrations dans l'ordre de jurika_db, puis le code
        // teste tourne en role d'execution jurika_app (la RLS s'applique).
        SchemaJurikaDb.migrer(POSTGRES);
        owner = SchemaJurikaDb.proprietaire(POSTGRES);
        SchemaJurikaDb.workspace(owner, workspace, "Cabinet Liquidation", "JUR-LQ001");
        SchemaJurikaDb.workspace(owner, autreWorkspace, "Autre cabinet", "JUR-LQ002");
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
        dossier = UUID.randomUUID();
        useCases = newUseCases();
        identityService = newIdentityService();
        owner.update("DELETE FROM entreprise_dossiers");
        insertDossier(dossier, workspace, "ACTIVE", null,
                "{\"associes\":[{\"nom\":\"BENALI\",\"prenom\":\"Karim\"}],\"nombreParts\":1000}");
    }

    // ── Le cycle complet ───────────────────────────────────────────────

    @Test
    @DisplayName("Dissolution → le liquidateur est ecrit en base ; la liquidation le relit")
    void cycleComplet_dissolutionPuisLiquidation() {
        // 1) DISSOLUTION : le workflow persiste le liquidateur qu'il vient de nommer.
        inTx(workspace, () -> useCases.persistLiquidateur(workspace, dossier, liquidateur(),
                "12 RUE DES FOULES, CASABLANCA"));
        // Le workflow marque aussi le dossier DISSOUTE + la date d'effet.
        inTx(workspace, () -> em.createNativeQuery(
                        "UPDATE entreprise_dossiers SET statut = 'DISSOUTE', date_dissolution = ?1 "
                                + "WHERE id = ?2")
                .setParameter(1, LocalDate.of(2026, 5, 15))
                .setParameter(2, dossier)
                .executeUpdate());

        // 2) LIQUIDATION : tout est relu depuis la base, rien n'est re-saisi.
        Map<String, Object> identity = identite(workspace, dossier);

        assertThat(identity.get("statut")).isEqualTo("DISSOUTE");
        assertThat(identity.get("dateDissolution")).isEqualTo("2026-05-15");
        assertThat(identity.get("siegeLiquidation")).isEqualTo("12 RUE DES FOULES, CASABLANCA");
        @SuppressWarnings("unchecked")
        Map<String, Object> liq = (Map<String, Object>) identity.get("liquidateur");
        assertThat(liq).containsEntry("nom", "ALAOUI");
        assertThat(liq).containsEntry("prenom", "Ahmed");
        assertThat(liq).containsEntry("civilite", "M.");
        assertThat(liq).containsEntry("source", "BD");
        // Le siege voyage aussi DANS le bloc liquidateur (clef consommee par ai-service).
        assertThat(liq).containsEntry("siege", "12 RUE DES FOULES, CASABLANCA");
    }

    @Test
    @DisplayName("Le merge JSONB preserve les cles existantes de la fiche")
    void mergeJsonb_preserveLesAutresCles() {
        inTx(workspace, () -> useCases.persistLiquidateur(workspace, dossier, liquidateur(), "SIEGE LIQ"));

        Map<String, Object> identity = identite(workspace, dossier);
        // Les associes / parts prealables n'ont pas ete ecrases par le merge.
        assertThat(identity).containsKey("associes");
        assertThat(identity.get("nombreParts")).isEqualTo(1000);
        assertThat(identity).containsKey("liquidateur");
    }

    @Test
    @DisplayName("Ecriture idempotente : rejouer la persistance ne duplique rien")
    void persistance_idempotente() {
        inTx(workspace, () -> useCases.persistLiquidateur(workspace, dossier, liquidateur(), "SIEGE LIQ"));
        Map<String, Object> liquidateurMaj = liquidateur();
        liquidateurMaj.put("adresse", "9 RUE DE PARIS, RABAT");
        inTx(workspace, () -> useCases.persistLiquidateur(workspace, dossier, liquidateurMaj, "SIEGE LIQ 2"));

        Map<String, Object> identity = identite(workspace, dossier);
        @SuppressWarnings("unchecked")
        Map<String, Object> liq = (Map<String, Object>) identity.get("liquidateur");
        assertThat(liq).containsEntry("adresse", "9 RUE DE PARIS, RABAT");
        assertThat(identity.get("siegeLiquidation")).isEqualTo("SIEGE LIQ 2");
        assertThat(countDossiers()).isEqualTo(1);
    }

    @Test
    @DisplayName("Fiche initialement NULL : la persistance la cree sans erreur")
    void ficheNulle_estCreee() {
        UUID vierge = UUID.randomUUID();
        insertDossier(vierge, workspace, "ACTIVE", null, null);

        inTx(workspace, () -> useCases.persistLiquidateur(workspace, vierge, liquidateur(), "SIEGE LIQ"));

        @SuppressWarnings("unchecked")
        Map<String, Object> liq =
                (Map<String, Object>) identite(workspace, vierge).get("liquidateur");
        assertThat(liq).containsEntry("nom", "ALAOUI");
    }

    // ── Cas de secours et garde-fous ───────────────────────────────────

    @Test
    @DisplayName("Cas de secours : sans liquidateur en base, la relecture n'en expose aucun")
    void dossierSansLiquidateur_neRemonteRien() {
        Map<String, Object> identity = identite(workspace, dossier);
        assertThat(identity).doesNotContainKeys("liquidateur", "siegeLiquidation");
        // La denomination et les associes restent lisibles : seul le liquidateur manque.
        assertThat(identity).containsKey("associes");
    }

    @Test
    @DisplayName("Liquidateur absent du payload → no-op (la fiche existante est intacte)")
    void liquidateurAbsent_noOp() {
        inTx(workspace, () -> useCases.persistLiquidateur(workspace, dossier, null, "SIEGE LIQ"));
        inTx(workspace, () -> useCases.persistLiquidateur(workspace, dossier, Map.of(), "SIEGE LIQ"));

        Map<String, Object> identity = identite(workspace, dossier);
        assertThat(identity).doesNotContainKey("liquidateur");
        assertThat(identity).containsKey("associes");
    }

    @Test
    @DisplayName("Defense multi-tenant : on n'ecrit pas le liquidateur d'un autre workspace")
    void crossTenant_aucuneEcriture() {
        inTx(autreWorkspace, () -> useCases.persistLiquidateur(autreWorkspace, dossier, liquidateur(), "SIEGE LIQ"));

        // Le dossier appartient a `workspace` : l'UPDATE filtre par workspace_id n'a rien mute.
        Map<String, Object> identity = identite(workspace, dossier);
        assertThat(identity).doesNotContainKey("liquidateur");
        // Et la lecture cross-tenant ne renvoie rien non plus.
        assertThat(identite(autreWorkspace, dossier)).isEmpty();
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private static Map<String, Object> liquidateur() {
        Map<String, Object> l = new HashMap<>();
        l.put("source", "BD");
        l.put("civilite", "M.");
        l.put("prenom", "Ahmed");
        l.put("nom", "ALAOUI");
        l.put("adresse", "45 BD ZERKTOUNI, CASABLANCA");
        return l;
    }

    /** {@link WorkflowUseCases} reduit a ce que la persistance utilise : l'EntityManager. */
    private WorkflowUseCases newUseCases() {
        WorkflowUseCases svc = new WorkflowUseCases(null, null, null, null, null, null);
        ReflectionTestUtils.setField(svc, "em", em);
        return svc;
    }

    private DossierIdentityQueryService newIdentityService() {
        DossierIdentityQueryService svc = new DossierIdentityQueryService();
        ReflectionTestUtils.setField(svc, "em", em);
        return svc;
    }

    private void insertDossier(UUID id, UUID ws, String statut, LocalDate dateDissolution,
                               String ficheJson) {
        // Raison sociale distincte par dossier : l'index unique reel
        // uq_dossier_workspace_raison_alive (ticket V6) l'exige dans un workspace.
        String raison = id.equals(dossier) ? "PARACOSME" : "PARACOSME " + id.toString().substring(0, 8);
        owner.update("""
                        INSERT INTO entreprise_dossiers
                          (id, workspace_id, raison_sociale, forme_juridique, rc_numero,
                           rc_tribunal, capital_social_mad, adresse_siege, ville, statut,
                           date_dissolution, fiche_structuree)
                        VALUES (?, ?, ?, 'SARL', '123456', 'CASABLANCA', 100000,
                                '12 RUE DES FOULES', 'CASABLANCA', ?, ?, CAST(? AS jsonb))
                        """, id, ws, raison, statut, dateDissolution, ficheJson);
    }

    private long countDossiers() {
        return owner.queryForObject("SELECT COUNT(*) FROM entreprise_dossiers", Long.class);
    }

    /** Relecture par le service, dans une transaction du workspace demande. */
    private Map<String, Object> identite(UUID ws, UUID dossierId) {
        List<Map<String, Object>> lu = new ArrayList<>();
        inTx(ws, () -> lu.add(identityService.identity(ws, dossierId)));
        return lu.get(0);
    }

    /**
     * Transaction du workspace {@code ws} : comme TenantAwareJpaTransactionManager
     * en production, le workspace est pose a l'ouverture, local a la transaction.
     */
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
