package ma.jurika.ticket.integration;

import ma.jurika.ticket.domain.port.FicheCloture;
import ma.jurika.ticket.infrastructure.persistence.FicheClotureJdbc;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L4c (E2, RG-CLO-04, RG-VAR-08) : a la cloture, la fiche societe recoit les
 * identifiants obtenus pendant le parcours, sans jamais ecraser une valeur existante.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FicheClotureIT {

    static final UUID WS = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID E1 = UUID.fromString("33333333-3333-3333-3333-333333333333");

    final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_fiche")
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
        migrer("classpath:db/migration", "flyway_history_ticket", null);
        migrer("filesystem:../workflow-service/src/main/resources/db/migration", "flyway_history_workflow", null);
    }

    @AfterAll
    void arreter() {
        pg.stop();
    }

    @Test
    void la_cloture_reporte_les_identifiants_obtenus_sans_ecraser_la_fiche() {
        UUID dossier = UUID.randomUUID();
        db.update("INSERT INTO entreprise_dossiers (id, workspace_id, raison_sociale, forme_juridique, responsable_id, ice) "
                + "VALUES (?, ?, 'PARACOSME', 'SARL', ?, '001234567000089')", dossier, WS, E1);
        UUID ticket = UUID.randomUUID();
        db.update("INSERT INTO tickets (id, workspace_id, reference, titre, type, statut, priorite, dossier_id, assigne_id, cree_par_id) "
                + "VALUES (?, ?, 'T-FICHE', 'T', 'CREATION', 'DEROULEMENT_DEMARCHE', 'NORMALE', ?, ?, ?)", ticket, WS, dossier, E1, E1);
        for (String[] v : new String[][]{{"RC_NUMERO", "456789"}, {"ICE", "009999999000011"}}) {
            db.update("INSERT INTO dossier_variables (workspace_id, ticket_id, variable, valeur, origine, saisie_par_id) "
                    + "VALUES (?, ?, ?, ?, 'SAISIE', ?)", WS, ticket, v[0], v[1], E1);
        }

        FicheCloture.Resultat r = new FicheClotureJdbc(db).mettreAJour(WS, ticket);

        assertThat(r.repris()).containsExactly("RC_NUMERO");
        assertThat(r.ecarts()).singleElement().asString().contains("ICE").contains("001234567000089").contains("009999999000011");
        assertThat(db.queryForObject("SELECT rc_numero FROM entreprise_dossiers WHERE id = ?", String.class, dossier))
                .isEqualTo("456789");
        assertThat(db.queryForObject("SELECT ice FROM entreprise_dossiers WHERE id = ?", String.class, dossier))
                .as("jamais ecrase").isEqualTo("001234567000089");
    }

    @Test
    void un_ticket_sans_dossier_ne_touche_a_rien() {
        UUID ticket = UUID.randomUUID();
        db.update("INSERT INTO tickets (id, workspace_id, reference, titre, type, statut, priorite, assigne_id, cree_par_id) "
                + "VALUES (?, ?, 'T-SANS', 'T', 'MODIFICATION', 'DEROULEMENT_DEMARCHE', 'NORMALE', ?, ?)", ticket, WS, E1, E1);
        FicheCloture.Resultat r = new FicheClotureJdbc(db).mettreAJour(WS, ticket);
        assertThat(r.repris()).isEmpty();
        assertThat(r.ecarts()).isEmpty();
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
