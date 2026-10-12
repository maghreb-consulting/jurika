package ma.jurika.ticket.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L5 (E6, ticket V40) : les sept types de debours du cahier des charges (RG-DEB-01),
 * reprise sans perte des categories de V3 (ancienne valeur conservee, libelle intact).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeboursTypesMigrationIT {

    static final UUID WS = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID E1 = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final UUID TICKET = UUID.fromString("dddddddd-0000-0000-0000-00000000e601");

    final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_v40")
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
        migrer("classpath:db/migration", "flyway_history_ticket", "39");
        db.update("INSERT INTO tickets (id, workspace_id, reference, titre, type, statut, priorite, "
                + "assigne_id, cree_par_id) VALUES (?, ?, 'T-E6', 'T', 'MODIFICATION', 'CREATION_TICKET', 'NORMALE', ?, ?)",
                TICKET, WS, E1, E1);
        for (String c : new String[] {"FRAIS_TRIBUNAL", "FRAIS_NOTARIE", "FRAIS_ENREGISTREMENT", "PUBLICATION_JAL",
                "PUBLICATION_BO", "HONORAIRES", "TRANSPORT", "AUTRE"}) {
            db.update("INSERT INTO ticket_debours (workspace_id, ticket_id, libelle, categorie, montant_mad, created_by_id) "
                    + "VALUES (?, ?, ?, ?, 100.00, ?)", WS, TICKET, "Ligne " + c, c, E1);
        }
        migrer("classpath:db/migration", "flyway_history_ticket", null);
    }

    @AfterAll
    void arreter() {
        pg.stop();
    }

    private Map<String, Object> ligne(String ancienne) {
        return db.queryForMap("SELECT libelle, categorie, categorie_reprise FROM ticket_debours WHERE libelle = ?",
                "Ligne " + ancienne);
    }

    @Test
    void equivalences_exactes_reprises_ancienne_valeur_conservee() {
        assertThat(ligne("FRAIS_ENREGISTREMENT")).containsEntry("categorie", "DROITS_ENREGISTREMENT")
                .containsEntry("categorie_reprise", "FRAIS_ENREGISTREMENT");
        assertThat(ligne("PUBLICATION_JAL")).containsEntry("categorie", "ANNONCE_LEGALE")
                .containsEntry("categorie_reprise", "PUBLICATION_JAL");
        assertThat(ligne("PUBLICATION_BO")).containsEntry("categorie", "ANNONCE_LEGALE")
                .containsEntry("categorie_reprise", "PUBLICATION_BO");
    }

    @Test
    void autres_valeurs_en_autre_libelle_et_ancienne_valeur_conserves() {
        for (String c : new String[] {"FRAIS_TRIBUNAL", "FRAIS_NOTARIE", "HONORAIRES", "TRANSPORT"}) {
            assertThat(ligne(c)).containsEntry("categorie", "AUTRE")
                    .containsEntry("categorie_reprise", c)
                    .containsEntry("libelle", "Ligne " + c);
        }
        assertThat(ligne("AUTRE")).containsEntry("categorie", "AUTRE");
        assertThat(ligne("AUTRE").get("categorie_reprise")).isNull();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM ticket_debours WHERE libelle LIKE 'Ligne %'", Integer.class)).isEqualTo(8);
    }

    @Test
    void seuls_les_sept_types_sont_admis() {
        for (String c : new String[] {"DROITS_ENREGISTREMENT", "FRAIS_GREFFE", "ANNONCE_LEGALE", "TIMBRES",
                "LEGALISATION", "FRAIS_ORGANISME", "AUTRE"}) {
            db.update("INSERT INTO ticket_debours (workspace_id, ticket_id, libelle, categorie, montant_mad, created_by_id) "
                    + "VALUES (?, ?, 'ok', ?, 1, ?)", WS, TICKET, c, E1);
        }
        assertThatThrownBy(() -> db.update("INSERT INTO ticket_debours (workspace_id, ticket_id, libelle, categorie, "
                + "montant_mad, created_by_id) VALUES (?, ?, 'ko', 'HONORAIRES', 1, ?)", WS, TICKET, E1))
                .isInstanceOf(DataIntegrityViolationException.class);
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
