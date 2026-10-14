package ma.jurika.billing.application.contrat;

import com.zaxxer.hikari.HikariDataSource;
import ma.jurika.billing.application.editeur.EditeurParametresService;
import ma.jurika.billing.infrastructure.config.BillingProperties;
import ma.jurika.billing.infrastructure.persistence.EditeurParametresEntity;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lot L7 (E9) : passage d'une base vivante (Z440) a la version 1 du contrat. La base porte deja
 * la version "2026-10-12" (projet a crochets, origine INITIALE, inalterable), sans aucune
 * acceptation. Apres billing V16 et le demarrage : le projet est retire (jamais presente a un
 * cabinet), la version 1 est chargee, en vigueur des le demarrage. Si un cabinet avait accepte
 * le projet, il ne serait pas retire et le chargement sans preavis serait refuse.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContratVersionInitialeIT {

    private static final String PROJET = "# Présentation des pièces du contrat\n\n# A. Conditions particulières\n\n"
            + "**Maghreb Consulting**, [forme sociale] au capital de [montant] dirhams\n";

    private final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_billing_z440")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("contrat-it-init.sql");

    @BeforeAll
    void demarrer() throws Exception {
        pg.start();
        try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
             Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE avec_acceptation");
        }
    }

    @AfterAll
    void arreter() {
        pg.stop();
    }

    private String url(String base) {
        return pg.getJdbcUrl().replace("/jurika_billing_z440", "/" + base);
    }

    private void migrer(String base, String cible) {
        var conf = Flyway.configure().dataSource(url(base), pg.getUsername(), pg.getPassword())
                .locations("classpath:db/migration").table("flyway_history_billing");
        if (cible != null) conf = conf.target(cible);
        conf.load().migrate();
    }

    private static void insererProjet(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO contrat_versions (code, texte_modele, empreinte_modele, empreinte_cg, empreinte_dpa, "
                + "entree_en_vigueur_le, origine) VALUES ('2026-10-12', ?, ?, ?, ?, DATE '2026-10-13', 'INITIALE')",
                PROJET, "a".repeat(64), "b".repeat(64), "c".repeat(64));
    }

    private static ContratService service(HikariDataSource ds, JdbcTemplate jdbc) {
        ContratHorloge horloge = new ContratHorloge();
        horloge.regler(Clock.fixed(Instant.parse("2026-10-15T08:00:00Z"), ZoneOffset.UTC));
        EditeurParametresService editeur = mock(EditeurParametresService.class);
        when(editeur.identite()).thenReturn(new EditeurParametresEntity());
        ContratCourriel courriel = new ContratCourriel(null, null, new BillingProperties()) {
            @Override
            public Resultat envoyer(String destinataire, String objet, String gabarit, Map<String, Object> variables,
                                    PieceJointe piece) {
                return new Resultat(null);
            }
        };
        IdentiteCabinetSource identites = jeton -> new IdentiteCabinetSource.Identite("Cabinet", null, null, null,
                "c@cabinet.ma", "Nom", "Prénom", "compte@jurika.ma");
        return new ContratService(new ContratDepot(jdbc), editeur, identites, courriel, horloge, e -> { },
                new BillingProperties(), new DataSourceTransactionManager(ds));
    }

    private HikariDataSource source(String base) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url(base));
        ds.setUsername(pg.getUsername());
        ds.setPassword(pg.getPassword());
        return ds;
    }

    @Test
    void base_vivante_le_projet_est_retire_et_la_version_1_devient_la_version_en_vigueur() {
        migrer("jurika_billing_z440", "14");
        try (HikariDataSource ds = source("jurika_billing_z440")) {
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            insererProjet(jdbc);
            migrer("jurika_billing_z440", null);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM contrat_versions_retraits r JOIN contrat_versions v "
                    + "ON v.id = r.version_id WHERE v.code = '2026-10-12'", Integer.class)).isEqualTo(1);

            ContratService service = service(ds, jdbc);
            service.initialiserVersionInitiale(ContratPlanification.VERSION_INITIALE, ContratPlanification.sourceInitiale());
            Map<String, Object> v1 = jdbc.queryForMap("SELECT * FROM contrat_versions WHERE code = '1'");
            assertThat(v1.get("origine")).isEqualTo("INITIALE");
            assertThat(v1.get("entree_en_vigueur_le").toString()).isEqualTo("2026-10-15");
            assertThat(v1.get("texte_modele")).isEqualTo(ContratPlanification.modeleInitial());

            ContratService.Etat etat = service.etat(UUID.randomUUID());
            assertThat(etat.versionEnVigueur().code()).isEqualTo("1");
            assertThat(etat.versionAVenir()).isNull();
            // Le projet n'est jamais presente a un cabinet ; le super-admin le voit, marque retire.
            assertThat(service.versions(false)).extracting(ContratService.VersionVue::code).containsExactly("1");
            assertThat(service.versions(true)).extracting(ContratService.VersionVue::code, ContratService.VersionVue::retiree)
                    .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("1", false),
                            org.assertj.core.groups.Tuple.tuple("2026-10-12", true));
            assertThatThrownBy(() -> service.version("2026-10-12", false)).isInstanceOf(ContratException.class);
            assertThat(service.version("2026-10-12", true).texteModele()).isEqualTo(PROJET);
            // La ligne du projet reste intacte (inalterable) ; son retrait aussi.
            assertThatThrownBy(() -> jdbc.update("DELETE FROM contrat_versions_retraits"))
                    .rootCause().hasMessageContaining("inalterable");
            // Second demarrage : idempotent.
            service.initialiserVersionInitiale(ContratPlanification.VERSION_INITIALE, ContratPlanification.sourceInitiale());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM contrat_versions", Integer.class)).isEqualTo(2);
        }
    }

    @Test
    void projet_deja_accepte_par_un_cabinet_ni_retire_ni_remplace_sans_preavis() {
        migrer("avec_acceptation", "14");
        try (HikariDataSource ds = source("avec_acceptation")) {
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            insererProjet(jdbc);
            UUID ws = UUID.randomUUID();
            UUID cp = jdbc.queryForObject("INSERT INTO conditions_particulieres (workspace_id, reference, forfait, "
                    + "rythme_paiement, prix_ht, date_debut, date_fin, origine, statut, echeance_courante) VALUES "
                    + "(?, 'CP-2026-999999', 'ESSENTIEL', 'ANNUEL', 3750, DATE '2026-10-13', DATE '2027-10-13', "
                    + "'SUPER_ADMIN', 'ACCEPTE', DATE '2027-10-13') RETURNING id", UUID.class, ws);
            jdbc.update("INSERT INTO contrat_acceptations (workspace_id, conditions_id, version_id, version_code, objet, "
                    + "texte_accepte, empreinte_contrat, empreinte_cg, empreinte_dpa, signataire_user_id, signataire_compte, "
                    + "signataire_civilite, signataire_nom, signataire_prenom, signataire_qualite, signataire_role, "
                    + "cabinet_denomination, accepte_le, accepte_le_casablanca, adresse_ip) "
                    + "SELECT ?, ?, id, code, 'CONDITIONS_PARTICULIERES', 'texte', ?, ?, ?, ?, 'compte', 'M.', 'Nom', "
                    + "'Prenom', 'gerant', 'SUPERVISEUR', 'Cabinet', now(), '2026-10-13 10:00:00', '1.2.3.4' "
                    + "FROM contrat_versions WHERE code = '2026-10-12'",
                    ws, cp, "d".repeat(64), "b".repeat(64), "c".repeat(64), UUID.randomUUID());
            migrer("avec_acceptation", null);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM contrat_versions_retraits", Integer.class)).isZero();
            ContratService service = service(ds, jdbc);
            assertThatThrownBy(() -> service.initialiserVersionInitiale(ContratPlanification.VERSION_INITIALE,
                    ContratPlanification.sourceInitiale()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("RG-CTR-07");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM contrat_versions WHERE code = '1'", Integer.class)).isZero();
        }
    }
}
