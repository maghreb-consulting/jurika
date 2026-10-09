package ma.jurika.workflow.integration;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Lot L0 (E16) : schema de test de workflow construit par les VRAIES migrations,
 * dans l'ordre de la base partagee jurika_db (meme chaine que dataroom, E15a).
 * Ticket V21 reference dataroom_documents, que dataroom V5 cree en referencant
 * tickets : auth, ticket jusqu'a V20, dataroom, fin de ticket, workflow.
 */
public final class SchemaJurikaDb {

    /** Workspace et employe semes par auth V2 et V3. */
    public static final UUID WORKSPACE_SEME = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID EMPLOYE_SEME = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final Set<String> MIGREES = Collections.synchronizedSet(new HashSet<>());

    private SchemaJurikaDb() { }

    /** Applique la chaine une fois par conteneur (en proprietaire). */
    public static void migrer(PostgreSQLContainer<?> pg) {
        if (!MIGREES.add(pg.getJdbcUrl())) return;
        passe(pg, "filesystem:../auth-service/src/main/resources/db/migration", "flyway_history_auth", null);
        passe(pg, "filesystem:../ticket-service/src/main/resources/db/migration", "flyway_history_ticket", "20");
        passe(pg, "filesystem:../dataroom-service/src/main/resources/db/migration", "flyway_history_dataroom", null);
        passe(pg, "filesystem:../ticket-service/src/main/resources/db/migration", "flyway_history_ticket", null);
        passe(pg, "classpath:db/migration", "flyway_history_workflow", null);
        // Modeles conserves a part : les tests vident workspaces (et, en cascade,
        // users) avant chaque scenario ; le clonage ne doit pas en dependre.
        JdbcTemplate owner = proprietaire(pg);
        owner.execute("CREATE TABLE l0_modele_workspace AS SELECT * FROM workspaces WHERE id = '" + WORKSPACE_SEME + "'");
        owner.execute("CREATE TABLE l0_modele_utilisateur AS SELECT * FROM users WHERE id = '" + EMPLOYE_SEME + "'");
        verifierUneLigne(owner, "l0_modele_workspace");
        verifierUneLigne(owner, "l0_modele_utilisateur");
    }

    private static void verifierUneLigne(JdbcTemplate owner, String table) {
        Integer n = owner.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        if (n == null || n != 1) {
            throw new IllegalStateException(table + " : " + n + " ligne(s) au lieu d'une");
        }
    }

    private static void passe(PostgreSQLContainer<?> pg, String emplacement, String historique, String cible) {
        var config = Flyway.configure()
                .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations(emplacement).table(historique)
                .baselineOnMigrate(true).baselineVersion("0");
        if (cible != null) config.target(cible);
        config.load().migrate();
    }

    public static JdbcTemplate proprietaire(PostgreSQLContainer<?> pg) {
        return new JdbcTemplate(new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()));
    }

    /**
     * Cree un cabinet : clone du workspace seme (toutes ses colonnes obligatoires),
     * avec un identifiant, un nom et un code ({@code ^JUR-[A-Z0-9]{5}$}) propres.
     */
    public static void workspace(JdbcTemplate owner, UUID id, String nom, String code) {
        owner.execute((java.sql.Connection c) -> {
            try (java.sql.Statement st = c.createStatement()) {
                st.execute("CREATE TEMP TABLE w AS SELECT * FROM l0_modele_workspace");
                st.execute("UPDATE w SET id = '" + id + "', name = '" + nom.replace("'", "''")
                        + "', code = '" + code + "'");
                int n = st.executeUpdate("INSERT INTO workspaces SELECT * FROM w");
                st.execute("DROP TABLE w");
                if (n != 1) throw new IllegalStateException("Workspace " + id + " non cree (" + n + " ligne)");
                // L'employe seme sert d'auteur aux tickets de test (tickets.cree_par_id) ;
                // s'il a disparu (nettoyage en cascade), il est rattache a ce cabinet.
                st.execute("CREATE TEMP TABLE e AS SELECT * FROM l0_modele_utilisateur");
                st.execute("UPDATE e SET workspace_id = '" + id + "'");
                st.execute("INSERT INTO users SELECT * FROM e WHERE NOT EXISTS "
                        + "(SELECT 1 FROM users WHERE id = '" + EMPLOYE_SEME + "')");
                st.execute("DROP TABLE e");
            }
            return null;
        });
    }

    /** Cree un utilisateur (clone de l'employe seme) dans un cabinet. */
    public static void utilisateur(JdbcTemplate owner, UUID id, UUID workspace) {
        owner.execute((java.sql.Connection c) -> {
            try (java.sql.Statement st = c.createStatement()) {
                st.execute("CREATE TEMP TABLE u AS SELECT * FROM l0_modele_utilisateur");
                st.execute("UPDATE u SET id = '" + id + "', workspace_id = '" + workspace
                        + "', email = '" + id + "@rls.test', login_email = '" + id + "@rls.test'");
                int n = st.executeUpdate("INSERT INTO users SELECT * FROM u");
                st.execute("DROP TABLE u");
                if (n != 1) throw new IllegalStateException("Utilisateur " + id + " non cree (" + n + " ligne)");
            }
            return null;
        });
    }
}
