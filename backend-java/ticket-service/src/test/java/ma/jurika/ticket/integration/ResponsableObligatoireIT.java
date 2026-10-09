package ma.jurika.ticket.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L1, etape E3 : migration V28 (ticket-service), responsable obligatoire.
 *
 * <p>La chaine de migrations est jouee jusqu'a V27 (etat du Z440 au 2026-10-09),
 * les cas releves sur le Z440 sont poses, puis V28 est appliquee :
 * rattrapage TRACE (nature RATTRAPAGE) selon l'ordre de la migration, alignement
 * des tickets sur le responsable du dossier, NOT NULL et cles en RESTRICT ; un
 * workspace sans aucun employe fait echouer la migration en nommant le dossier.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResponsableObligatoireIT {

    static final UUID WS = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID E1 = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final UUID E2 = UUID.fromString("33333333-0000-0000-0000-0000000000e2");
    static final UUID SUP = UUID.fromString("33333333-0000-0000-0000-00000000005a");
    static final UUID D_UN_ASSIGNE = UUID.fromString("dddddddd-0000-0000-0000-000000000001");
    static final UUID D_DEUX_ASSIGNES = UUID.fromString("dddddddd-0000-0000-0000-000000000002");
    static final UUID D_SANS_TICKET = UUID.fromString("dddddddd-0000-0000-0000-000000000003");
    static final UUID D_SUPERVISEUR = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    static final UUID D_CORRECT = UUID.fromString("dddddddd-0000-0000-0000-000000000005");

    final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_v28")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("testcontainers-init.sql");

    JdbcTemplate db;

    @BeforeAll
    void demarrer() {
        pg.start();
        db = new JdbcTemplate(new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()));
        migrerJusquA(pg.getJdbcUrl(), "27");
        utilisateur(E2, "EMPLOYE", "e2@v28.test", "2026-01-02");
        utilisateur(SUP, "SUPERVISEUR", "sup@v28.test", "2025-01-01");
        db.update("UPDATE users SET created_at = '2026-01-01' WHERE id = ?", E1);
        dossier(D_UN_ASSIGNE, null);
        dossier(D_DEUX_ASSIGNES, null);
        dossier(D_SANS_TICKET, null);
        dossier(D_SUPERVISEUR, SUP);
        dossier(D_CORRECT, E2);
        ticket(1, D_UN_ASSIGNE, E1, SUP, "2026-02-01");
        ticket(2, D_UN_ASSIGNE, E1, E1, "2026-02-02");
        ticket(3, D_DEUX_ASSIGNES, E1, E2, "2026-02-01");
        ticket(4, D_DEUX_ASSIGNES, E2, E1, "2026-02-03");
        ticket(5, D_SUPERVISEUR, E2, E2, "2026-02-01");
        ticket(6, D_CORRECT, E1, E2, "2026-02-01");
        migrer(pg.getJdbcUrl(), "classpath:db/migration", "flyway_history_ticket", "28");
    }

    @AfterAll
    void arreter() {
        pg.stop();
    }

    @Test
    void chaque_dossier_a_un_employe_responsable_selon_l_ordre_de_rattrapage() {
        assertThat(responsable(D_UN_ASSIGNE)).as("a. unique assigne").isEqualTo(E1);
        assertThat(responsable(D_DEUX_ASSIGNES)).as("b. createur EMPLOYE du premier ticket").isEqualTo(E2);
        assertThat(responsable(D_SANS_TICKET)).as("c. employe actif le plus ancien").isEqualTo(E1);
        assertThat(responsable(D_SUPERVISEUR)).as("superviseur remplace (a.)").isEqualTo(E2);
        assertThat(responsable(D_CORRECT)).as("inchange").isEqualTo(E2);
        assertThat(db.queryForObject("SELECT count(*) FROM entreprise_dossiers WHERE responsable_id IS NULL",
                Integer.class)).isZero();
    }

    @Test
    void chaque_rattrapage_est_trace() {
        List<Map<String, Object>> traces = db.queryForList(
                "SELECT dossier_id, ancien_responsable_id, nouveau_responsable_id, nature, auteur_id, motif "
                        + "FROM dossier_reaffectations ORDER BY dossier_id");
        assertThat(traces).extracting(t -> t.get("dossier_id"))
                .containsExactly(D_UN_ASSIGNE, D_DEUX_ASSIGNES, D_SANS_TICKET, D_SUPERVISEUR);
        assertThat(traces).allSatisfy(t -> {
            assertThat(t.get("nature")).isEqualTo("RATTRAPAGE");
            assertThat(t.get("auteur_id")).isNull();
        });
        assertThat(traces.get(3).get("ancien_responsable_id")).isEqualTo(SUP);
        assertThat(traces.get(3).get("nouveau_responsable_id")).isEqualTo(E2);
        assertThat(traces.get(0).get("ancien_responsable_id")).isNull();
    }

    @Test
    void les_tickets_suivent_leur_dossier() {
        assertThat(db.queryForObject("SELECT count(*) FROM tickets t JOIN entreprise_dossiers d "
                + "ON d.id = t.dossier_id WHERE t.assigne_id IS DISTINCT FROM d.responsable_id", Integer.class))
                .isZero();
        assertThat(db.queryForObject("SELECT assigne_id FROM tickets WHERE reference = 'V28-6'", UUID.class))
                .isEqualTo(E2);
    }

    @Test
    void contraintes_not_null_et_restrict() {
        assertThat(db.queryForObject("SELECT is_nullable FROM information_schema.columns "
                + "WHERE table_name = 'entreprise_dossiers' AND column_name = 'responsable_id'", String.class))
                .isEqualTo("NO");
        assertThat(db.queryForObject("SELECT confdeltype FROM pg_constraint "
                + "WHERE conname = 'entreprise_dossiers_responsable_id_fkey'", String.class)).isEqualTo("r");
        assertThat(db.queryForObject("SELECT confdeltype FROM pg_constraint "
                + "WHERE conname = 'tickets_dossier_id_fkey'", String.class)).isEqualTo("r");
        assertThatThrownBy(() -> db.update("UPDATE entreprise_dossiers SET responsable_id = NULL WHERE id = ?",
                D_CORRECT)).hasMessageContaining("responsable_id");
    }

    @Test
    void workspace_sans_employe_echec_explicite_qui_nomme_le_dossier() {
        db.execute("CREATE DATABASE jurika_it_v28_echec");
        String url = pg.getJdbcUrl().replace("/jurika_it_v28", "/jurika_it_v28_echec");
        JdbcTemplate echec = new JdbcTemplate(new DriverManagerDataSource(url, pg.getUsername(), pg.getPassword()));
        // Memes extensions que testcontainers-init.sql (le script ne s'applique qu'a la premiere base).
        echec.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"; CREATE EXTENSION IF NOT EXISTS pgcrypto; "
                + "CREATE EXTENSION IF NOT EXISTS pg_trgm");
        migrerJusquA(url, "27");
        echec.update("UPDATE users SET role = 'SUPERVISEUR' WHERE id = ?", E1);
        UUID orphelin = UUID.fromString("dddddddd-0000-0000-0000-0000000000ff");
        echec.update("INSERT INTO entreprise_dossiers (id, workspace_id, raison_sociale, forme_juridique) "
                + "VALUES (?, ?, 'Orphelin', 'SARL')", orphelin, WS);
        assertThatThrownBy(() -> migrer(url, "classpath:db/migration", "flyway_history_ticket", "28"))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining(orphelin.toString());
        assertThat(echec.queryForObject("SELECT count(*) FROM entreprise_dossiers WHERE responsable_id IS NULL",
                Integer.class)).as("migration annulee, rien de modifie").isEqualTo(1);
    }

    private void migrerJusquA(String url, String cibleTicket) {
        migrer(url, "filesystem:../auth-service/src/main/resources/db/migration", "flyway_history_auth", null);
        migrer(url, "classpath:db/migration", "flyway_history_ticket", "20");
        migrer(url, "filesystem:../dataroom-service/src/main/resources/db/migration", "flyway_history_dataroom", null);
        migrer(url, "classpath:db/migration", "flyway_history_ticket", cibleTicket);
    }

    private void migrer(String url, String emplacement, String historique, String cible) {
        var config = Flyway.configure()
                .dataSource(url, pg.getUsername(), pg.getPassword())
                .locations(emplacement)
                .table(historique)
                .baselineOnMigrate(true)
                .baselineVersion("0");
        if (cible != null) config.target(cible);
        config.load().migrate();
    }

    private void utilisateur(UUID id, String role, String email, String creeLe) {
        // Table de travail ordinaire : DriverManagerDataSource ouvre une connexion par
        // instruction, une table temporaire n'y survivrait pas.
        db.execute("CREATE TABLE u_v28 AS SELECT * FROM users WHERE id = '" + E1 + "'");
        db.update("UPDATE u_v28 SET id = ?, role = ?, email = ?, login_email = ?, created_at = CAST(? AS timestamptz)",
                id, role, email, email, creeLe);
        db.update("INSERT INTO users SELECT * FROM u_v28");
        db.execute("DROP TABLE u_v28");
    }

    private void dossier(UUID id, UUID responsable) {
        db.update("INSERT INTO entreprise_dossiers (id, workspace_id, raison_sociale, forme_juridique, responsable_id) "
                + "VALUES (?, ?, ?, 'SARL', ?)", id, WS, "Societe " + id.toString().substring(32), responsable);
    }

    private void ticket(int n, UUID dossier, UUID assigne, UUID createur, String creeLe) {
        db.update("INSERT INTO tickets (id, workspace_id, reference, titre, type, statut, priorite, dossier_id, "
                        + "assigne_id, cree_par_id, created_at) VALUES (gen_random_uuid(), ?, ?, 'T', 'CREATION', "
                        + "'CREATION_TICKET', 'NORMALE', ?, ?, ?, CAST(? AS timestamptz))",
                WS, "V28-" + n, dossier, assigne, createur, creeLe);
    }

    private UUID responsable(UUID dossier) {
        return db.queryForObject("SELECT responsable_id FROM entreprise_dossiers WHERE id = ?", UUID.class, dossier);
    }
}
