package ma.jurika.dataroom.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Non-régression du défaut <b>DR1</b> — <b>UN SEUL document « en vigueur » par slot</b>.
 *
 * <h2>Le défaut</h2>
 * Le socle de versioning existait (colonnes {@code version} / {@code is_current} /
 * {@code replaced_at}), mais RIEN en base n'interdisait deux lignes
 * {@code is_current = TRUE} sur le même slot logique : on pouvait afficher « deux
 * Statuts en vigueur » côte à côte au lieu d'un courant + un historique replié. Le
 * backfill de la migration V23 a d'ailleurs trouvé <b>99 lignes</b> dans cet état sur la
 * base de développement — le défaut était déjà installé, pas théorique.
 *
 * <h2>Ce que ce test verrouille</h2>
 * Le correctif applicatif (routage du dépôt vers le versioning) protège le chemin
 * nominal, mais une régression du code le contournerait en silence. La garantie DURE est
 * l'index unique partiel posé par la migration. Ce test applique donc la <b>vraie
 * migration V23</b> (lue depuis les ressources, pas recopiée) sur un PostgreSQL réel et
 * vérifie que la BASE refuse le doublon.
 *
 * <h2>Le slot retenu</h2>
 * {@code (workspace_id, dossier_id, document_type, title)} — et non {@code (dossier,
 * type)} : plusieurs exemplaires d'un même type coexistent légitimement (une CIN par
 * personne, cf. défaut A5), c'est le titre qui les distingue. Ce test couvre les deux
 * faces : le doublon est refusé, la coexistence légitime reste possible.
 *
 * <p><b>Exécution</b> : suffixe {@code IT} → profil {@code -Pit} (cf. pom parent).
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataroomUniciteCourantIT {

    private static final String MIGRATION =
            "/db/migration/V23__dataroom_documents_seance_types_et_unicite_courant.sql";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("jurika_dr1")
                    .withUsername("jurika_it")
                    .withPassword("jurika_it");

    private Connection cx;
    private final UUID workspace = UUID.randomUUID();
    private final UUID dossier = UUID.randomUUID();

    @BeforeAll
    void bootstrap() throws Exception {
        POSTGRES.start();
        cx = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());

        // Schéma minimal : les colonnes que la migration V23 touche.
        exec("""
             CREATE TABLE dataroom_documents (
                 id            UUID PRIMARY KEY,
                 workspace_id  UUID NOT NULL,
                 dossier_id    UUID NOT NULL,
                 document_type TEXT NOT NULL,
                 title         TEXT NOT NULL,
                 version       SMALLINT NOT NULL DEFAULT 1,
                 is_current    BOOLEAN  NOT NULL DEFAULT TRUE,
                 replaced_at   TIMESTAMPTZ,
                 created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
             )
             """);
    }

    @AfterAll
    void teardown() throws Exception {
        if (cx != null && !cx.isClosed()) cx.close();
    }

    @BeforeEach
    void resetFixture() throws Exception {
        exec("DROP INDEX IF EXISTS ux_dataroom_documents_courant_par_slot");
        // La migration V23 (re)pose la contrainte CHECK : sans ce drop, le test qui
        // l'installe lui-meme echouerait au 2e passage sur « constraint already exists ».
        exec("ALTER TABLE dataroom_documents "
                + "DROP CONSTRAINT IF EXISTS dataroom_documents_document_type_check");
        exec("DELETE FROM dataroom_documents");
    }

    // ── La migration corrige l'existant ────────────────────────────────

    @Test
    @DisplayName("DR1 — le backfill V23 ne laisse qu'UN courant par slot et renumérote les versions")
    void backfillRamèneUnSeulCourant() throws Exception {
        // Trois « Statuts » simultanément en vigueur : exactement l'état trouvé en base.
        insert("STATUTS", "statuts", 1, true, "2026-01-10");
        insert("STATUTS", "statuts", 1, true, "2026-03-15");
        insert("STATUTS", "statuts", 1, true, "2026-06-20");

        appliquerMigrationV23();

        assertThat(compter("WHERE is_current"))
                .describedAs("un seul document en vigueur doit subsister")
                .isEqualTo(1);
        assertThat(scalaire(
                "SELECT created_at::date::text FROM dataroom_documents WHERE is_current"))
                .describedAs("le PLUS RÉCENT est conservé comme courant")
                .isEqualTo("2026-06-20");
        assertThat(compter("WHERE NOT is_current AND replaced_at IS NOT NULL"))
                .describedAs("les précédents partent en historique, datés")
                .isEqualTo(2);
        assertThat(scalaire("SELECT string_agg(version::text, ',' ORDER BY created_at) "
                + "FROM dataroom_documents"))
                .describedAs("versions renumérotées dans l'ordre chronologique")
                .isEqualTo("1,2,3");
    }

    // ── La base interdit désormais le doublon ──────────────────────────

    @Test
    @DisplayName("DR1 — après V23, la BASE refuse un second courant sur le même slot")
    void baseRefuseUnSecondCourant() throws Exception {
        insert("STATUTS", "statuts", 1, true, "2026-01-10");
        appliquerMigrationV23();

        assertThatThrownBy(() -> insert("STATUTS", "statuts", 2, true, "2026-06-20"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ux_dataroom_documents_courant_par_slot");
    }

    @Test
    @DisplayName("DR1 — l'historique reste librement multiple (l'index ne porte que sur le courant)")
    void historiqueResteMultiple() throws Exception {
        appliquerMigrationV23();
        assertThatCode(() -> {
            insert("STATUTS", "statuts", 1, false, "2026-01-10");
            insert("STATUTS", "statuts", 2, false, "2026-03-15");
            insert("STATUTS", "statuts", 3, true, "2026-06-20");
        }).doesNotThrowAnyException();
        assertThat(compter("")).isEqualTo(3);
    }

    @Test
    @DisplayName("DR1 — deux CIN de personnes différentes coexistent (le slot inclut le TITRE)")
    void deuxCinDistinctesCoexistent() throws Exception {
        appliquerMigrationV23();
        // Le défaut A5 imposait ce choix de slot : regrouper sur (dossier, type) aurait
        // fait de la seconde CIN une « version » de la première — donc masqué une pièce.
        assertThatCode(() -> {
            insert("CIN_NOUVELLE", "CIN nouvelle - Ahmed ALAOUI", 1, true, "2026-01-10");
            insert("CIN_NOUVELLE", "CIN nouvelle - Salma BENJELLOUN", 1, true, "2026-01-10");
        }).doesNotThrowAnyException();
        assertThat(compter("WHERE is_current")).isEqualTo(2);
    }

    // ── Les types de séance manquants (DR2) ────────────────────────────

    @Test
    @DisplayName("DR2 — les 4 types de séance sont acceptés par la contrainte CHECK")
    void typesDeSeanceAcceptes() throws Exception {
        exec("ALTER TABLE dataroom_documents ADD CONSTRAINT dataroom_documents_document_type_check "
                + "CHECK (document_type IN ('STATUTS','AUTRE'))");
        appliquerMigrationV23();
        for (String type : new String[]{
                "CONVOCATION", "FEUILLE_PRESENCE", "RAPPORT_GESTION", "RAPPORT_LIQUIDATION"}) {
            String t = type;
            assertThatCode(() -> insert(t, "doc " + t, 1, true, "2026-01-10"))
                    .describedAs("le type %s doit être accepté", t)
                    .doesNotThrowAnyException();
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────

    /** Applique la VRAIE migration (ressource du module), jamais une copie. */
    private void appliquerMigrationV23() throws Exception {
        try (InputStream is = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(is).describedAs("migration %s introuvable", MIGRATION).isNotNull();
            exec(new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private void insert(String type, String title, int version, boolean current, String jour)
            throws SQLException {
        try (Statement st = cx.createStatement()) {
            st.executeUpdate("""
                    INSERT INTO dataroom_documents
                      (id, workspace_id, dossier_id, document_type, title, version, is_current, created_at)
                    VALUES ('%s', '%s', '%s', '%s', '%s', %d, %b, '%s'::timestamptz)
                    """.formatted(UUID.randomUUID(), workspace, dossier, type, title,
                    version, current, jour));
        }
    }

    private void exec(String sql) throws SQLException {
        try (Statement st = cx.createStatement()) {
            st.execute(sql);
        }
    }

    private long compter(String where) throws SQLException {
        return Long.parseLong(scalaire("SELECT COUNT(*) FROM dataroom_documents " + where));
    }

    private String scalaire(String sql) throws SQLException {
        try (Statement st = cx.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
