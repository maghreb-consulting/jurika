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

        FicheCloture.Resultat r = new FicheClotureJdbc(db).mettreAJour(WS, ticket, E1);

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
        FicheCloture.Resultat r = new FicheClotureJdbc(db).mettreAJour(WS, ticket, E1);
        assertThat(r.repris()).isEmpty();
        assertThat(r.ecarts()).isEmpty();
    }

    // ------------------------------------------------------------------------------
    // Lot L5 (E7, RG-FIC-03, RG-FIC-04) : trace et provenance des identifiants (V41).
    // ------------------------------------------------------------------------------

    private java.util.List<java.util.Map<String, Object>> trace(UUID dossier, String champ) {
        return db.queryForList("SELECT ancienne_valeur, nouvelle_valeur, origine, ticket_id, auteur_id "
                + "FROM dossier_identifiants_historique WHERE dossier_id = ? AND champ = ? ORDER BY modifie_le, id",
                dossier, champ);
    }

    private UUID dossier(String rc) {
        UUID d = UUID.randomUUID();
        db.update("INSERT INTO entreprise_dossiers (id, workspace_id, raison_sociale, forme_juridique, responsable_id, rc_numero) "
                + "VALUES (?, ?, ?, 'SARL', ?, ?)", d, WS, "TRACEE " + d, E1, rc);
        return d;
    }

    private UUID ticket(UUID dossier, String ref, String type, String statut) {
        UUID t = UUID.randomUUID();
        db.update("INSERT INTO tickets (id, workspace_id, reference, titre, type, statut, priorite, dossier_id, assigne_id, cree_par_id) "
                + "VALUES (?, ?, ?, 'T', ?, ?, 'NORMALE', ?, ?, ?)", t, WS, ref, type, statut, dossier, E1, E1);
        return t;
    }

    @Test
    void la_cloture_trace_l_identifiant_reporte_en_fin_de_parcours_avec_ticket_et_auteur() {
        UUID dossier = dossier(null);
        UUID t = ticket(dossier, "T-TR1", "CREATION", "DEROULEMENT_DEMARCHE");
        db.update("INSERT INTO dossier_variables (workspace_id, ticket_id, variable, valeur, origine, saisie_par_id) "
                + "VALUES (?, ?, 'RC_NUMERO', '777', 'SAISIE', ?)", WS, t, E1);
        db.update("INSERT INTO dossier_variables (workspace_id, ticket_id, variable, valeur, origine, saisie_par_id) "
                + "VALUES (?, ?, 'IDENTIFIANT_FISCAL', 'IF-88', 'EXTRAITE', ?)", WS, t, E1);

        new FicheClotureJdbc(db).mettreAJour(WS, t, E1);

        assertThat(trace(dossier, "rc_numero")).singleElement().satisfies(l -> {
            assertThat(l.get("ancienne_valeur")).isNull();
            assertThat(l.get("nouvelle_valeur")).isEqualTo("777");
            assertThat(l.get("origine")).isEqualTo("FIN_PARCOURS");
            assertThat(l.get("ticket_id")).isEqualTo(t);
            assertThat(l.get("auteur_id")).isEqualTo(E1);
        });
        assertThat(trace(dossier, "identifiant_fiscal")).singleElement()
                .satisfies(l -> assertThat(l.get("origine")).as("lu sur une piece et confirme").isEqualTo("EXTRAITE"));
    }

    @Test
    void l_ouverture_du_dossier_trace_les_valeurs_initiales() {
        UUID dossier = dossier("RC-1");
        assertThat(trace(dossier, "raison_sociale")).singleElement().satisfies(l -> {
            assertThat(l.get("nouvelle_valeur")).isEqualTo("TRACEE " + dossier);
            assertThat(l.get("origine")).isEqualTo("OUVERTURE");
        });
        assertThat(trace(dossier, "rc_numero")).singleElement()
                .satisfies(l -> assertThat(l.get("nouvelle_valeur")).isEqualTo("RC-1"));
        assertThat(trace(dossier, "ice")).as("un champ vide n'a pas de trace").isEmpty();
    }

    @Test
    void une_ecriture_de_parcours_sans_declaration_est_rattachee_au_seul_ticket_ouvert() {
        UUID dossier = dossier("RC-2");
        UUID ouvert = ticket(dossier, "T-TR3", "MODIFICATION", "GENERATION_DOCUMENTS");
        ticket(dossier, "T-TR3B", "CREATION", "CLOTURE_DOSSIER");

        db.update("UPDATE entreprise_dossiers SET capital_social_mad = 200000 WHERE id = ?", dossier);

        assertThat(trace(dossier, "capital_social_mad")).singleElement().satisfies(l -> {
            assertThat(l.get("origine")).isEqualTo("PARCOURS");
            assertThat(l.get("ticket_id")).isEqualTo(ouvert);
            assertThat(l.get("auteur_id")).isNull();
        });

        ticket(dossier, "T-TR3C", "PV_AGO", "CREATION_TICKET");
        db.update("UPDATE entreprise_dossiers SET adresse_siege = '1 rue Y' WHERE id = ?", dossier);
        assertThat(trace(dossier, "adresse_siege")).singleElement()
                .satisfies(l -> assertThat(l.get("ticket_id")).as("deux tickets ouverts : jamais devine").isNull());
    }

    @Test
    void la_saisie_declaree_trace_ancienne_et_nouvelle_valeur_et_son_auteur() {
        UUID dossier = dossier("RC-3");
        db.execute((java.sql.Connection c) -> {
            c.setAutoCommit(false);
            try (java.sql.Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.identifiants_origine', 'SAISIE', true), "
                        + "set_config('app.identifiants_auteur', '" + E1 + "', true)");
                st.executeUpdate("UPDATE entreprise_dossiers SET rc_numero = 'RC-3B', taxe_professionnelle = 'TP-9' "
                        + "WHERE id = '" + dossier + "'");
                st.executeUpdate("UPDATE entreprise_dossiers SET raison_sociale = 'TRACEE " + dossier + "' WHERE id = '" + dossier + "'");
            }
            c.commit();
            c.setAutoCommit(true);
            return null;
        });

        assertThat(trace(dossier, "rc_numero")).hasSize(2).last().satisfies(l -> {
            assertThat(l.get("ancienne_valeur")).isEqualTo("RC-3");
            assertThat(l.get("nouvelle_valeur")).isEqualTo("RC-3B");
            assertThat(l.get("origine")).isEqualTo("SAISIE");
            assertThat(l.get("auteur_id")).isEqualTo(E1);
        });
        assertThat(trace(dossier, "taxe_professionnelle")).singleElement()
                .satisfies(l -> assertThat(l.get("nouvelle_valeur")).isEqualTo("TP-9"));
        assertThat(trace(dossier, "raison_sociale")).as("valeur inchangee : aucune trace").hasSize(1);
    }

    @Test
    void le_role_applicatif_soumis_a_la_rls_ecrit_la_fiche_et_sa_trace() {
        UUID dossier = dossier("RC-4");
        DriverManagerDataSource app = new DriverManagerDataSource(pg.getJdbcUrl(), "jurika_app", "jurika_app_it");
        JdbcTemplate jdbcApp = new JdbcTemplate(app);
        jdbcApp.execute((java.sql.Connection c) -> {
            c.setAutoCommit(false);
            try (java.sql.Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_workspace_id', '" + WS + "', true)");
                assertThat(st.executeUpdate("UPDATE entreprise_dossiers SET cnss = 'CNSS-1' WHERE id = '" + dossier + "'"))
                        .isEqualTo(1);
            }
            c.commit();
            c.setAutoCommit(true);
            return null;
        });
        assertThat(trace(dossier, "cnss")).singleElement()
                .satisfies(l -> assertThat(l.get("nouvelle_valeur")).isEqualTo("CNSS-1"));

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
