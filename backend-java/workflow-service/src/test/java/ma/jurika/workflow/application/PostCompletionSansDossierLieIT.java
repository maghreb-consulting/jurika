package ma.jurika.workflow.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import ma.jurika.workflow.domain.model.WorkflowType;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Non-regression du defaut <b>D2</b> (simulation 2026-08-15, GROUPE 0).
 *
 * <p><b>Le bug</b> : {@code fetchDossierId} faisait
 * {@code getResultStream().findFirst()} sur {@code SELECT dossier_id FROM tickets}.
 * Quand la ligne EXISTE mais que {@code dossier_id} vaut {@code NULL}, le stream
 * produit un element nul et {@code findFirst()} leve une {@link NullPointerException}
 * ({@code Optional} ne peut pas contenir {@code null}). C'est exactement le cas des
 * tickets MODIFICATION / DISSOLUTION / LIQUIDATION : la societe est CHOISIE a l'etape 1
 * ({@code step1.dossierId}) sans jamais lier le ticket.
 *
 * <p><b>Pourquoi c'etait invisible</b> : la NPE etait avalee par le try/catch de
 * {@code applyPostCompletion}. Les documents sortaient parfaitement, mais AUCUN effet
 * metier n'etait applique — ni statut DISSOUTE/LIQUIDEE, ni modification de la fiche,
 * ni persistance du liquidateur. Le GROUPE 7 (non-vacuite documentaire) ne peut pas
 * detecter ce defaut : il ne touche pas au contenu des documents. Ce test est donc
 * le SEUL garde-fou.
 *
 * <p><b>Pourquoi TestContainers</b> : le bug est dans l'interaction JPA/JDBC avec une
 * colonne SQL NULL. Un {@link EntityManager} mocke retournerait ce qu'on lui dit de
 * retourner et ne reproduirait jamais la NPE. Il faut une vraie base — meme approche
 * que {@code LiquidateurPersistenceIT}.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostCompletionSansDossierLieIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("jurika_wf_d2")
                    .withUsername("jurika_it")
                    .withPassword("jurika_it")
                    .withInitScript("testcontainers-init.sql");

    private static EntityManagerFactory emf;
    private EntityManager em;
    private WorkflowUseCases useCases;

    private final UUID workspace = UUID.randomUUID();
    private final UUID autreWorkspace = UUID.randomUUID();

    /** Le ticket du workflow : il existe, mais son {@code dossier_id} est NULL. */
    private UUID ticketSansDossier;
    /** La societe reellement visee, choisie a l'etape 1 du wizard. */
    private UUID dossier;

    /** Preparation et relectures de controle en PROPRIETAIRE, hors RLS (lot L0). */
    private JdbcTemplate owner;

    @BeforeAll
    void bootstrap() {
        POSTGRES.start();
        // Lot L0 (E16) : vraies migrations dans l'ordre de jurika_db (dont
        // tickets.dossier_id, nullable), puis le code teste tourne en role
        // d'execution jurika_app (la RLS s'applique).
        SchemaJurikaDb.migrer(POSTGRES);
        owner = SchemaJurikaDb.proprietaire(POSTGRES);
        SchemaJurikaDb.workspace(owner, workspace, "Cabinet D2", "JUR-D2001");
        SchemaJurikaDb.workspace(owner, autreWorkspace, "Autre cabinet", "JUR-D2002");
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
        useCases = newUseCases();
        ticketSansDossier = UUID.randomUUID();
        dossier = UUID.randomUUID();
        owner.update("DELETE FROM tickets");
        owner.update("DELETE FROM entreprise_dossiers");
        insertTicket(ticketSansDossier, workspace, null);
        insertDossier(dossier, workspace, "NOVA INDUSTRIE", "ACTIVE");
    }

    // ── (a) La cause racine : dossier_id NULL ne doit plus lever ────────

    @Test
    @DisplayName("D2/a — fetchDossierId sur un ticket dont dossier_id est NULL renvoie null sans lever")
    void fetchDossierId_surColonneNulle_renvoieNullSansNpe() {
        // Avant le correctif : NullPointerException levee par Optional.findFirst().
        assertThatCode(() -> {
            UUID scoped = dansTx(workspace, () -> ReflectionTestUtils.<UUID>invokeMethod(useCases, "fetchDossierId", workspace, ticketSansDossier));
            assertThat(scoped).isNull();
        }).doesNotThrowAnyException();

        // La surcharge deprecated (mono-argument) porte le meme piege.
        assertThatCode(() -> {
            UUID legacy = dansTx(workspace, () -> ReflectionTestUtils.<UUID>invokeMethod(useCases, "fetchDossierId", ticketSansDossier));
            assertThat(legacy).isNull();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("D2/a — ticket inexistant : null egalement (aucune ligne, pas d'element nul)")
    void fetchDossierId_ticketInexistant_renvoieNull() {
        UUID absent = dansTx(workspace, () -> ReflectionTestUtils.<UUID>invokeMethod(useCases, "fetchDossierId", workspace, UUID.randomUUID()));
        assertThat(absent).isNull();
    }

    @Test
    @DisplayName("D2/a — ticket reellement lie : la valeur est bien remontee (pas de regression)")
    void fetchDossierId_ticketLie_remonteLaValeur() {
        UUID ticketLie = UUID.randomUUID();
        insertTicket(ticketLie, workspace, dossier);
        UUID lu = dansTx(workspace, () -> ReflectionTestUtils.<UUID>invokeMethod(useCases, "fetchDossierId", workspace, ticketLie));
        assertThat(lu).isEqualTo(dossier);
    }

    // ── (b) L'effet metier : le post-completion s'applique via step1.dossierId ──

    @Test
    @DisplayName("D2/b — DISSOLUTION sans dossier_id sur le ticket : statut DISSOUTE + date + liquidateur persistes")
    void dissolution_sansDossierLie_appliqueLesEffetsMetier() {
        Map<String, Object> liquidateur = new HashMap<>();
        liquidateur.put("source", "BD");
        liquidateur.put("civilite", "M.");
        liquidateur.put("prenom", "Ahmed");
        liquidateur.put("nom", "ALAOUI");

        Map<String, Object> step1 = new HashMap<>();
        step1.put("dossierId", dossier.toString());
        step1.put("dateAGE", "2026-05-15");
        step1.put("liquidateur", liquidateur);
        step1.put("siegeLiquidation", "12 RUE DES FOULES, CASABLANCA");

        inTx(workspace, () -> applyPostCompletion(WorkflowType.DISSOLUTION, Map.of("step1", step1)));

        assertThat(colonne("statut")).isEqualTo("DISSOUTE");
        assertThat(colonne("date_dissolution")).hasToString("2026-05-15");
        // Le liquidateur nommé à la dissolution doit exister en base : c'est lui que
        // la LIQUIDATION relira au lieu de le faire re-saisir (defaut L1).
        String fiche = String.valueOf(colonne("fiche_structuree"));
        assertThat(fiche).contains("ALAOUI").contains("siegeLiquidation");
    }

    @Test
    @DisplayName("D2/b — LIQUIDATION sans dossier_id sur le ticket : statut LIQUIDEE persiste")
    void liquidation_sansDossierLie_appliqueLeStatut() {
        Map<String, Object> step1 = new HashMap<>();
        step1.put("dossierId", dossier.toString());

        inTx(workspace, () -> applyPostCompletion(WorkflowType.LIQUIDATION, Map.of("step1", step1)));

        assertThat(colonne("statut")).isEqualTo("LIQUIDEE");
    }

    @Test
    @DisplayName("D2/b — MODIFICATION sans dossier_id sur le ticket : la nouvelle denomination est persistee")
    void modification_sansDossierLie_persisteLaNouvelleDenomination() {
        Map<String, Object> step1 = new HashMap<>();
        step1.put("dossierId", dossier.toString());
        Map<String, Object> step2 = Map.of("valeurs", Map.of(
                "CHANGEMENT_DENOMINATION", Map.of("nouvelleDenomination", "NOVA INDUSTRIE MAROC"),
                "AUGMENTATION_CAPITAL", Map.of("nouveauCapital", 500000)));

        inTx(workspace, () -> applyPostCompletion(WorkflowType.MODIFICATION,
                Map.of("step1", step1, "step2", step2)));

        assertThat(colonne("raison_sociale")).isEqualTo("NOVA INDUSTRIE MAROC");
        assertThat((BigDecimal) colonne("capital_social_mad"))
                .isEqualByComparingTo(new BigDecimal("500000"));
    }

    // ── Garde-fous : le repli ne doit pas ouvrir de breche ─────────────

    @Test
    @DisplayName("D2 — defense multi-tenant : un step1.dossierId d'un autre workspace ne mute rien")
    void repli_resteScopeAuWorkspace() {
        UUID dossierAutreTenant = UUID.randomUUID();
        insertDossier(dossierAutreTenant, autreWorkspace, "CIBLE ETRANGERE", "ACTIVE");

        Map<String, Object> step1 = new HashMap<>();
        step1.put("dossierId", dossierAutreTenant.toString());
        // Le workflow tourne dans `workspace`, mais vise un dossier de `autreWorkspace`.
        inTx(workspace, () -> applyPostCompletion(WorkflowType.DISSOLUTION, Map.of("step1", step1)));

        // Les UPDATE portent tous « AND workspace_id = ?» : aucune ligne touchee.
        assertThat(colonne(dossierAutreTenant, "statut")).isEqualTo("ACTIVE");
        assertThat(colonne(dossierAutreTenant, "date_dissolution")).isNull();
    }

    @Test
    @DisplayName("D2 — ni dossier lie ni step1.dossierId : sortie propre, aucune mutation")
    void aucuneCible_sortieSilencieuse() {
        assertThatCode(() -> inTx(workspace, () ->
                applyPostCompletion(WorkflowType.DISSOLUTION, Map.of("step1", Map.of()))))
                .doesNotThrowAnyException();
        assertThat(colonne("statut")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("D2 — le ticket lie garde la priorite sur step1.dossierId")
    void ticketLie_prioritaireSurStep1() {
        UUID ticketLie = UUID.randomUUID();
        insertTicket(ticketLie, workspace, dossier);
        UUID leurre = UUID.randomUUID();
        insertDossier(leurre, workspace, "LEURRE", "ACTIVE");

        Map<String, Object> step1 = new HashMap<>();
        step1.put("dossierId", leurre.toString());
        inTx(workspace, () -> ReflectionTestUtils.invokeMethod(useCases, "applyPostCompletion",
                WorkflowType.LIQUIDATION, workspace, ticketLie, Map.of("step1", step1)));

        assertThat(colonne(dossier, "statut")).isEqualTo("LIQUIDEE");
        assertThat(colonne(leurre, "statut")).isEqualTo("ACTIVE");
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private void applyPostCompletion(WorkflowType type, Map<String, Object> data) {
        ReflectionTestUtils.invokeMethod(useCases, "applyPostCompletion",
                type, workspace, ticketSansDossier, data);
    }

    private WorkflowUseCases newUseCases() {
        WorkflowUseCases svc = new WorkflowUseCases(null, null, null, null, null, null);
        ReflectionTestUtils.setField(svc, "em", em);
        return svc;
    }

    private void insertTicket(UUID id, UUID ws, UUID dossierId) {
        owner.update("""
                        INSERT INTO tickets (id, workspace_id, reference, titre, type, cree_par_id, dossier_id)
                        VALUES (?, ?, ?, 'Ticket D2', 'MODIFICATION', ?, ?)
                        """, id, ws, "D2-" + id.toString().substring(0, 8), SchemaJurikaDb.EMPLOYE_SEME, dossierId);
    }

    private void insertDossier(UUID id, UUID ws, String raisonSociale, String statut) {
        owner.update("""
                        INSERT INTO entreprise_dossiers
                          (id, workspace_id, raison_sociale, forme_juridique, rc_numero,
                           rc_tribunal, capital_social_mad, adresse_siege, ville, statut)
                        VALUES (?, ?, ?, 'SARL', '123456', 'CASABLANCA', 100000,
                                '12 RUE DES FOULES', 'CASABLANCA', ?)
                        """, id, ws, raisonSociale, statut);
    }

    private Object colonne(String name) {
        return colonne(dossier, name);
    }

    private Object colonne(UUID dossierId, String name) {
        List<Object> rows = owner.queryForList(
                "SELECT " + name + " FROM entreprise_dossiers WHERE id = ?", Object.class, dossierId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private <T> T dansTx(UUID ws, java.util.function.Supplier<T> action) {
        List<T> lu = new java.util.ArrayList<>();
        inTx(ws, () -> lu.add(action.get()));
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
