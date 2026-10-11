package ma.jurika.ticket.integration;

import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.infrastructure.persistence.RepriseTicketJdbc;
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
 * Lot L4a (E4) : migration V33 et adaptateur de l'annulation / reprise, sur une vraie base
 * (chaine de migrations de la base partagee).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnnulationRepriseIT {

    static final UUID WS = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID E1 = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final UUID DOSSIER = UUID.fromString("dddddddd-0000-0000-0000-00000000a401");

    final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_v33")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("testcontainers-init.sql");

    JdbcTemplate db;
    RepriseTicketJdbc reprise;

    @BeforeAll
    void demarrer() {
        pg.start();
        db = new JdbcTemplate(new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()));
        migrer("filesystem:../auth-service/src/main/resources/db/migration", "flyway_history_auth", null);
        migrer("classpath:db/migration", "flyway_history_ticket", "20");
        migrer("filesystem:../dataroom-service/src/main/resources/db/migration", "flyway_history_dataroom", null);
        migrer("classpath:db/migration", "flyway_history_ticket", null);
        db.update("INSERT INTO entreprise_dossiers (id, workspace_id, raison_sociale, forme_juridique, responsable_id) "
                + "VALUES (?, ?, 'PARACOSME', 'SARL', ?)", DOSSIER, WS, E1);
        reprise = new RepriseTicketJdbc(db);
    }

    @AfterAll
    void arreter() {
        pg.stop();
    }

    private UUID ticket(String reference) {
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO tickets (id, workspace_id, reference, titre, type, statut, priorite, dossier_id, "
                + "assigne_id, cree_par_id) VALUES (?, ?, ?, 'T', 'CREATION', 'ANNULE', 'NORMALE', ?, ?, ?)",
                id, WS, reference, DOSSIER, E1, E1);
        return id;
    }

    @Test
    void l_annulation_conserve_le_statut_atteint_et_l_auteur() {
        UUID t = ticket("A-1");
        reprise.enregistrerAnnulation(WS, t, TicketStatut.DEROULEMENT_DEMARCHE, E1);
        assertThat(reprise.statutAnterieur(WS, t)).contains(TicketStatut.DEROULEMENT_DEMARCHE);
        assertThat(db.queryForObject("SELECT annule_par_id FROM tickets WHERE id = ?", UUID.class, t)).isEqualTo(E1);
        reprise.effacer(WS, t);
        assertThat(db.queryForObject("SELECT statut_anterieur FROM tickets WHERE id = ?", String.class, t)).isNull();
    }

    @Test
    void un_ticket_annule_avant_v33_se_relit_dans_la_trace_de_l_annulation() {
        UUID t = ticket("A-2");
        db.update("INSERT INTO ticket_comments (workspace_id, ticket_id, auteur_id, type, contenu, metadata) "
                + "VALUES (?, ?, ?, 'ANNULATION', 'Abandon', CAST(? AS jsonb))",
                WS, t, E1, "{\"from\":\"GENERATION_DOCUMENTS\",\"to\":\"ANNULE\"}");
        assertThat(reprise.statutAnterieur(WS, t)).contains(TicketStatut.GENERATION_DOCUMENTS);
        assertThat(reprise.statutAnterieur(WS, ticket("A-3"))).as("aucune trace : rien de suppose").isEmpty();
    }

    @Test
    void un_retrait_coche_est_vu() {
        UUID t = ticket("A-4");
        assertThat(reprise.retraitEffectue(WS, t)).isFalse();
        UUID retrait = db.queryForObject(
                "SELECT id FROM demarches_referentiel WHERE formalite_volet = 'RETRAIT' LIMIT 1", UUID.class);
        db.update("INSERT INTO ticket_demarches (workspace_id, ticket_id, demarche_id, etat, acteur_id, coche_at) "
                + "VALUES (?, ?, ?, 'COCHEE', ?, now())", WS, t, retrait, E1);
        assertThat(reprise.retraitEffectue(WS, t)).isTrue();
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
