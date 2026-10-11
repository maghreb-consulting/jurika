package ma.jurika.ticket.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L4d (E6, ticket V38) : numero lisible du dossier, par cabinet et par annee.
 * Les dossiers existants sont numerotes dans l'ordre de leur creation ; un nouveau dossier
 * prend le numero suivant de son annee, y compris cree par le role d'execution (RLS).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NumeroDossierIT {

    static final UUID WS = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID E1 = UUID.fromString("33333333-3333-3333-3333-333333333333");

    final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_v38")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("testcontainers-init.sql");

    JdbcTemplate db;

    @BeforeAll
    void demarrer() {
        pg.start();
        db = new JdbcTemplate(new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()));
        migrer("filesystem:../auth-service/src/main/resources/db/migration", "flyway_history_auth", null);
        migrer("classpath:db/migration", "flyway_history_ticket", "20");
        migrer("filesystem:../dataroom-service/src/main/resources/db/migration", "flyway_history_dataroom", null);
        migrer("classpath:db/migration", "flyway_history_ticket", "37");
        // Dossiers anterieurs a V38, crees hors ordre d'insertion.
        dossier("B-2026", "2026-03-01");
        dossier("A-2025", "2025-11-20");
        dossier("C-2026", "2026-01-15");
        migrer("classpath:db/migration", "flyway_history_ticket", null);
    }

    @AfterAll
    void arreter() {
        pg.stop();
    }

    private void dossier(String nom, String creeLe) {
        db.update("INSERT INTO entreprise_dossiers (id, workspace_id, raison_sociale, forme_juridique, responsable_id, created_at) "
                + "VALUES (?, ?, ?, 'SARL', ?, CAST(? AS timestamptz))", UUID.randomUUID(), WS, nom, E1, creeLe + " 10:00:00+01");
    }

    private Map<String, Object> numero(String nom) {
        return db.queryForMap("SELECT numero_annee, numero_ordre FROM entreprise_dossiers WHERE raison_sociale = ?", nom);
    }

    @Test
    void les_dossiers_existants_sont_numerotes_par_annee_dans_l_ordre_de_creation() {
        assertThat(numero("A-2025")).containsEntry("numero_annee", 2025).containsEntry("numero_ordre", 1);
        assertThat(numero("C-2026")).containsEntry("numero_annee", 2026).containsEntry("numero_ordre", 1);
        assertThat(numero("B-2026")).containsEntry("numero_annee", 2026).containsEntry("numero_ordre", 2);
    }

    @Test
    void un_nouveau_dossier_prend_le_numero_suivant_meme_par_le_role_applicatif() {
        SingleConnectionDataSource app = new SingleConnectionDataSource(pg.getJdbcUrl(), "jurika_app", "jurika_app_it", true);
        try {
            JdbcTemplate parApp = new JdbcTemplate(app);
            parApp.execute("SELECT set_config('app.current_workspace_id', '" + WS + "', false)");
            parApp.update("INSERT INTO entreprise_dossiers (id, workspace_id, raison_sociale, forme_juridique, responsable_id, created_at) "
                    + "VALUES (?, ?, 'D-2026', 'SARL', ?, CAST('2026-10-11 10:00:00+01' AS timestamptz))", UUID.randomUUID(), WS, E1);
        } finally {
            app.destroy();
        }
        assertThat(numero("D-2026")).containsEntry("numero_annee", 2026).containsEntry("numero_ordre", 3);
        List<Integer> ordres = db.queryForList("SELECT numero_ordre FROM entreprise_dossiers WHERE workspace_id = ? "
                + "AND numero_annee = 2026 ORDER BY numero_ordre", Integer.class, WS);
        assertThat(ordres).containsExactly(1, 2, 3);
    }

    private void migrer(String emplacement, String historique, String cible) {
        var config = Flyway.configure()
                .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations(emplacement)
                .table(historique)
                .baselineOnMigrate(true)
                .baselineVersion("0");
        if (cible != null) config.target(cible);
        config.load().migrate();
    }
}
