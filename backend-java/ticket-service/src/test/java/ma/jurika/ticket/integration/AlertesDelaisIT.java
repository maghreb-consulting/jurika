package ma.jurika.ticket.integration;

import ma.jurika.ticket.domain.port.AlertesDelaisRegistre.TicketASurveiller;
import ma.jurika.ticket.infrastructure.persistence.AlertesDelaisJdbc;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L5 (E9, ticket V43) : registre des echeances notifiees et liste des tickets
 * a surveiller, executes par le role applicatif (RLS appliquee).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AlertesDelaisIT {

    static final UUID WS = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID WS_B = UUID.fromString("22222222-0000-0000-0000-00000000000b");
    static final UUID E1 = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final UUID SUP = UUID.fromString("33333333-0000-0000-0000-00000000005a");
    static final UUID SUP_INACTIF = UUID.fromString("33333333-0000-0000-0000-00000000005b");
    static final UUID SUP_B = UUID.fromString("33333333-0000-0000-0000-0000000000b5");
    static final LocalDate ECHEANCE = LocalDate.of(2026, 10, 20);

    final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_v43")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("testcontainers-init.sql");

    JdbcTemplate db;
    UUID enCours;
    UUID annule;
    UUID cloture;
    UUID ticketB;

    @BeforeAll
    void demarrer() {
        pg.start();
        db = new JdbcTemplate(new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()));
        migrer("filesystem:../auth-service/src/main/resources/db/migration", "flyway_history_auth", null);
        migrer("classpath:db/migration", "flyway_history_ticket", "20");
        migrer("filesystem:../dataroom-service/src/main/resources/db/migration", "flyway_history_dataroom", null);
        migrer("classpath:db/migration", "flyway_history_ticket", null);

        // Une seule instruction multiple : la table temporaire vit sur UNE connexion.
        db.execute("CREATE TEMP TABLE w AS SELECT * FROM workspaces WHERE id = '" + WS + "'; "
                + "UPDATE w SET id = '" + WS_B + "', code = 'JUR-V43BB'; "
                + "INSERT INTO workspaces SELECT * FROM w; DROP TABLE w");
        membre(SUP, WS, "SUPERVISEUR", "ACTIVE", "sup.v43@rls.test");
        membre(SUP_INACTIF, WS, "SUPERVISEUR", "INACTIVE", "sup.inactif.v43@rls.test");
        membre(SUP_B, WS_B, "SUPERVISEUR", "ACTIVE", "sup.b.v43@rls.test");

        enCours = ticket(WS, "V43-EC", "DEROULEMENT_DEMARCHE", E1);
        annule = ticket(WS, "V43-AN", "ANNULE", E1);
        cloture = ticket(WS, "V43-CL", "CLOTURE_DOSSIER", E1);
        ticketB = ticket(WS_B, "V43-B", "GENERATION_DOCUMENTS", E1);
    }

    @AfterAll
    void arreter() {
        pg.stop();
    }

    private void membre(UUID id, UUID ws, String role, String status, String email) {
        db.execute("CREATE TEMP TABLE u AS SELECT * FROM users WHERE id = '" + E1 + "'; "
                + "UPDATE u SET id = '" + id + "', workspace_id = '" + ws + "', role = '" + role
                + "', status = '" + status + "', email = '" + email + "', login_email = '" + email
                + "', contact_email = '" + email + "'; "
                + "INSERT INTO users SELECT * FROM u; DROP TABLE u");
    }

    private UUID ticket(UUID ws, String reference, String statut, UUID createur) {
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO tickets (id, workspace_id, reference, titre, type, statut, priorite, assigne_id, cree_par_id) "
                + "VALUES (?, ?, ?, 'T', 'CREATION', ?, 'NORMALE', ?, ?)", id, ws, reference, statut, createur, createur);
        return id;
    }

    /** Execute en tant que jurika_app, workspace pose pour la RLS. */
    private <T> T commeApp(UUID ws, java.util.function.Function<JdbcTemplate, T> action) {
        SingleConnectionDataSource app = new SingleConnectionDataSource(pg.getJdbcUrl(), "jurika_app", "jurika_app_it", true);
        try {
            JdbcTemplate parApp = new JdbcTemplate(app);
            if (ws != null) parApp.execute("SELECT set_config('app.current_workspace_id', '" + ws + "', false)");
            return action.apply(parApp);
        } finally {
            app.destroy();
        }
    }

    @Test
    void seuls_les_tickets_en_cours_de_tous_les_cabinets_sont_surveilles() {
        List<TicketASurveiller> cibles = commeApp(null, j -> new AlertesDelaisJdbc(j).ticketsASurveiller());
        assertThat(cibles).contains(new TicketASurveiller(WS, enCours), new TicketASurveiller(WS_B, ticketB));
        assertThat(cibles).extracting(TicketASurveiller::ticketId).doesNotContain(annule, cloture);
    }

    @Test
    void un_palier_n_est_retenu_qu_une_fois() {
        boolean premier = commeApp(WS, j -> new AlertesDelaisJdbc(j).retenirSiNouveau(WS, enCours, 23, ECHEANCE, "APPROCHE"));
        boolean second = commeApp(WS, j -> new AlertesDelaisJdbc(j).retenirSiNouveau(WS, enCours, 23, ECHEANCE, "APPROCHE"));
        boolean autrePalier = commeApp(WS, j -> new AlertesDelaisJdbc(j).retenirSiNouveau(WS, enCours, 23, ECHEANCE, "CRITIQUE"));
        boolean autreEcheance = commeApp(WS, j -> new AlertesDelaisJdbc(j).retenirSiNouveau(WS, enCours, 23,
                ECHEANCE.plusDays(5), "APPROCHE"));
        assertThat(premier).isTrue();
        assertThat(second).isFalse();
        assertThat(autrePalier).isTrue();
        assertThat(autreEcheance).isTrue();
    }

    @Test
    void la_rls_isole_le_registre_par_cabinet() {
        db.update("INSERT INTO alertes_delais_notifiees (workspace_id, ticket_id, ordre, echeance, severite) "
                + "VALUES (?, ?, 99, ?, 'DEPASSE')", WS_B, ticketB, Date.valueOf(ECHEANCE));
        Integer vus = commeApp(WS, j -> j.queryForObject(
                "SELECT COUNT(*) FROM alertes_delais_notifiees WHERE ticket_id = ?", Integer.class, ticketB));
        assertThat(vus).isZero();
    }

    @Test
    void superviseurs_actifs_du_seul_cabinet_demande() {
        List<UUID> sup = commeApp(WS, j -> new AlertesDelaisJdbc(j).actifs(WS));
        assertThat(sup).contains(SUP).doesNotContain(SUP_INACTIF, SUP_B, E1);
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
