package ma.jurika.billing.application.contrat;

import com.zaxxer.hikari.HikariDataSource;
import ma.jurika.billing.application.editeur.EditeurParametresService;
import ma.jurika.billing.infrastructure.persistence.EditeurParametresEntity;
import ma.jurika.billing.infrastructure.config.BillingProperties;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot L7 (E6) : contrat d'abonnement sur une vraie base (migrations billing V1 a V14) :
 * parcours complet (ouverture en attente, acceptation, exemplaire, changement, resiliation,
 * renouvellement, nouvelle version), preuve inalterable (declencheurs et droits de jurika_app),
 * RLS par cabinet, trace des parametres de l'editeur.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContratAbonnementIT {

    private static final String MODELE = ContratPlanification.modeleInitial();

    private final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_billing_it")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("contrat-it-init.sql");

    private HikariDataSource proprietaire;
    private JdbcTemplate jdbc;
    private ContratService service;
    private ContratHorloge horloge;
    private final List<Object> evenements = new ArrayList<>();
    private final List<String[]> courriels = new ArrayList<>();
    private EditeurParametresEntity editeur;

    @BeforeAll
    void demarrer() {
        pg.start();
        Flyway.configure()
                .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations("classpath:db/migration")
                .table("flyway_history_billing")
                .load()
                .migrate();
        proprietaire = new HikariDataSource();
        proprietaire.setJdbcUrl(pg.getJdbcUrl());
        proprietaire.setUsername(pg.getUsername());
        proprietaire.setPassword(pg.getPassword());
        jdbc = new JdbcTemplate(proprietaire);

        horloge = new ContratHorloge();
        editeur = new EditeurParametresEntity();
        EditeurParametresService editeurService = mock(EditeurParametresService.class);
        when(editeurService.identite()).thenAnswer(i -> editeur);
        ContratCourriel courriel = new ContratCourriel(null, null, new BillingProperties()) {
            @Override
            public Resultat envoyer(String destinataire, String objet, String gabarit, Map<String, Object> variables,
                                    PieceJointe piece) {
                courriels.add(new String[]{destinataire, gabarit, piece == null ? "" : piece.nom()});
                return new Resultat(null);
            }
        };
        IdentiteCabinetSource identites = jeton -> new IdentiteCabinetSource.Identite("Cabinet Atlas Conseil",
                "12 boulevard d\u2019Anfa, Casablanca", "123456", "001234567000089", "contact@atlas.ma",
                "Bennani", "Salma", "salma.bennani@jurika.ma");
        service = new ContratService(new ContratDepot(jdbc), editeurService, identites, courriel, horloge,
                evenements::add, proprietes(), new DataSourceTransactionManager(proprietaire));
        service.initialiserSiVide(ContratPlanification.VERSION_INITIALE, MODELE);
    }

    @BeforeEach
    void raz() {
        horloge.regler(Clock.fixed(Instant.parse("2026-10-14T09:30:00Z"), ZoneOffset.UTC));
        evenements.clear();
        courriels.clear();
    }

    @AfterAll
    void arreter() {
        if (proprietaire != null) proprietaire.close();
        pg.stop();
    }

    private static ContratService.Requerant superviseur(UUID userId) {
        return new ContratService.Requerant(userId, "SUPERVISEUR", "Bearer x", "196.200.1.2", "196.200.1.2 ; 10.0.0.5");
    }

    private static ContratService.CommandeAcceptation commande(String empreinte, boolean caseCochee, boolean confirmation) {
        return new ContratService.CommandeAcceptation("Mme", "g\u00e9rante", "SARL", "Casablanca", empreinte,
                caseCochee, confirmation);
    }

    private UUID ouvrir(UUID ws, String forfait, String rythme, String prix) {
        return service.ouvrir(new ContratAbonnementPort.ConditionsParticulieres(ws, forfait, rythme, new BigDecimal(prix),
                LocalDate.of(2026, 11, 1), null, UUID.randomUUID()), "VIREMENT", "SUPER_ADMIN");
    }

    private ContratService.AcceptationVue accepter(UUID ws, UUID user) {
        ContratService.Apercu a = service.apercu(ws, superviseur(user), commande(null, false, false));
        return service.accepter(ws, superviseur(user), commande(a.empreinte(), true, true));
    }

    @Test
    void parcours_complet_du_contrat() {
        UUID ws = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        assertThat(service.acceptationEnAttente(ws)).isFalse();
        assertThat(service.etat(ws).statut()).isEqualTo("AUCUN");

        // RG-CTR-03 : conditions particulieres ouvertes EN ATTENTE.
        UUID cp = ouvrir(ws, "PROFESSIONNEL", "ANNUEL", "7500");
        assertThat(service.acceptationEnAttente(ws)).isTrue();
        ContratService.Etat etat = service.etat(ws);
        assertThat(etat.statut()).isEqualTo("EN_ATTENTE");
        assertThat(etat.objetAAccepter()).isEqualTo(ContratService.OBJET_CP);
        assertThat(etat.enAttente().reference()).matches("CP-2026-\\d{6}");
        assertThat(etat.enAttente().dateFin()).isEqualTo(LocalDate.of(2027, 11, 1));

        // Apercu : texte, empreinte, mentions de l'editeur nommees (parametres vides).
        ContratService.Apercu apercu = service.apercu(ws, superviseur(user), commande(null, false, false));
        assertThat(apercu.empreinte()).isEqualTo(ContratTexte.sha256(apercu.texte()));
        assertThat(apercu.manquants()).contains("ICE de l\u2019\u00c9diteur", "forme juridique de l\u2019\u00c9diteur");
        assertThat(apercu.texte()).contains("| Forfait | Professionnel |", "| Mode de paiement choisi | virement bancaire |");

        // RG-CTR-01 : case non cochee, confirmation absente, texte modifie -> refus, rien n'est enregistre.
        assertThatThrownBy(() -> service.accepter(ws, superviseur(user), commande(apercu.empreinte(), false, true)))
                .isInstanceOf(ContratException.class).hasMessageContaining("Cochez la case");
        assertThatThrownBy(() -> service.accepter(ws, superviseur(user), commande(apercu.empreinte(), true, false)))
                .isInstanceOf(ContratException.class).hasMessageContaining("Confirmez");
        assertThatThrownBy(() -> service.accepter(ws, superviseur(user), commande("0".repeat(64), true, true)))
                .isInstanceOf(ContratException.class)
                .satisfies(e -> assertThat(((ContratException) e).statut()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.accepter(ws, new ContratService.Requerant(user, "EMPLOYE", "Bearer x",
                "1.2.3.4", null), commande(apercu.empreinte(), true, true)))
                .isInstanceOf(ContratException.class)
                .satisfies(e -> assertThat(((ContratException) e).statut()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contrat_acceptations WHERE workspace_id = ?", Integer.class, ws))
                .isZero();

        // Acceptation : preuve complete (RG-CTR-02).
        ContratService.AcceptationVue acc = service.accepter(ws, superviseur(user), commande(apercu.empreinte(), true, true));
        Map<String, Object> preuve = jdbc.queryForMap("SELECT * FROM contrat_acceptations WHERE id = ?", acc.id());
        assertThat(preuve.get("empreinte_contrat").toString().trim()).isEqualTo(apercu.empreinte());
        assertThat(preuve.get("texte_accepte")).isEqualTo(apercu.texte());
        assertThat(preuve.get("version_code")).isEqualTo("2026-10-12");
        assertThat(preuve.get("signataire_compte")).isEqualTo("salma.bennani@jurika.ma");
        assertThat(preuve.get("signataire_qualite")).isEqualTo("g\u00e9rante");
        assertThat(preuve.get("signataire_role")).isEqualTo("SUPERVISEUR");
        assertThat(preuve.get("accepte_le_casablanca")).isEqualTo("2026-10-14 10:30:00");
        assertThat(preuve.get("adresse_ip")).isEqualTo("196.200.1.2");
        assertThat(preuve.get("cabinet_email")).isEqualTo("contact@atlas.ma");
        assertThat(service.acceptationEnAttente(ws)).isFalse();
        assertThat(service.etat(ws).statut()).isEqualTo("ACCEPTE");
        assertThat(evenements).hasSize(1).first().isInstanceOf(ContratEvenements.ContratAccepte.class);

        // RG-CTR-04 : exemplaire PDF et copie par courriel (journalisee).
        ContratService.Exemplaire ex = service.exemplaire(ws, acc.id(), false);
        assertThat(new String(ex.pdf(), 0, 5)).isEqualTo("%PDF-");
        assertThat(service.exemplaire(ws, acc.id(), false).pdf()).isEqualTo(ex.pdf());
        assertThat(courriels).hasSize(1);
        assertThat(courriels.get(0)[0]).isEqualTo("contact@atlas.ma");
        assertThat(courriels.get(0)[2]).endsWith(".pdf");
        assertThat(jdbc.queryForObject("SELECT statut FROM contrat_avis WHERE workspace_id = ? AND type = 'COPIE_CONTRAT'",
                String.class, ws)).isEqualTo("ENVOYE");
        // Un autre cabinet ne lit pas cet exemplaire (filtrage applicatif).
        assertThatThrownBy(() -> service.exemplaire(UUID.randomUUID(), acc.id(), false))
                .isInstanceOf(ContratException.class);

        // RG-CTR-05 : changement de forfait -> nouvelles conditions en attente ; l'ancien contrat reste
        // accepte jusqu'a l'acceptation, puis il est remplace.
        UUID cp2 = ouvrir(ws, "MAX", "MENSUEL", "1500");
        assertThat(service.acceptationEnAttente(ws)).isTrue();
        assertThat(jdbc.queryForObject("SELECT statut FROM conditions_particulieres WHERE id = ?", String.class, cp))
                .isEqualTo("ACCEPTE");
        accepter(ws, user);
        assertThat(jdbc.queryForObject("SELECT statut FROM conditions_particulieres WHERE id = ?", String.class, cp))
                .isEqualTo("REMPLACE");
        assertThat(jdbc.queryForObject("SELECT statut FROM conditions_particulieres WHERE id = ?", String.class, cp2))
                .isEqualTo("ACCEPTE");

        // RG-CTR-09 : historique complet.
        ContratService.Historique h = service.historique(ws, false);
        assertThat(h.conditions()).hasSize(2);
        assertThat(h.acceptations()).hasSize(2);

        // RG-CTR-08 : resiliation par le superviseur, effet a l'echeance, arret du forfait signale.
        assertThatThrownBy(() -> service.resilierParCabinet(ws, user, " "))
                .isInstanceOf(ContratException.class).hasMessageContaining("motif");
        ContratService.ResiliationVue res = service.resilierParCabinet(ws, user, "Cessation d\u2019activit\u00e9");
        assertThat(res.dateEffet()).isEqualTo(LocalDate.of(2027, 11, 1));
        // Arret du forfait : signale a l'abonnement par l'evenement (ResiliationAbonnementListener).
        assertThat(evenements).last().isInstanceOfSatisfying(ContratEvenements.ResiliationEnregistree.class,
                ev -> assertThat(ev.dateEffet()).isEqualTo(LocalDate.of(2027, 11, 1)));
        assertThat(evenements).last().isInstanceOf(ContratEvenements.ResiliationEnregistree.class);
        assertThat(service.etat(ws).statut()).isEqualTo("RESILIE");
        // Rien n'est supprime.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contrat_acceptations WHERE workspace_id = ?", Integer.class, ws))
                .isEqualTo(2);
    }

    @Test
    void renouvellement_annuel_et_avis_trente_jours_avant() {
        UUID ws = UUID.randomUUID();
        ouvrir(ws, "ESSENTIEL", "ANNUEL", "3750");
        accepter(ws, UUID.randomUUID());
        courriels.clear();

        // 2027-10-05 : echeance du 2027-11-01 a 27 jours -> avis au cabinet et au super-admin, une seule fois.
        horloge.regler(Clock.fixed(Instant.parse("2027-10-05T06:00:00Z"), ZoneOffset.UTC));
        service.executerEcheances();
        assertThat(courriels).extracting(c -> c[0]).contains("contact@atlas.ma", "ventes@editeur.test");
        assertThat(courriels).allMatch(c -> c[1].equals("email/contrat-echeance"));
        int avis = courriels.size();
        service.executerEcheances();
        assertThat(courriels).hasSize(avis);

        // Echeance passee : renouvellement pour un an, trace.
        horloge.regler(Clock.fixed(Instant.parse("2027-11-02T06:00:00Z"), ZoneOffset.UTC));
        ContratService.BilanEcheances b = service.executerEcheances();
        assertThat(b.renouvellements()).isGreaterThanOrEqualTo(1);
        assertThat(service.etat(ws).courant().echeanceCourante()).isEqualTo(LocalDate.of(2028, 11, 1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contrat_renouvellements WHERE workspace_id = ?",
                Integer.class, ws)).isEqualTo(1);
    }

    @Test
    void nouvelle_version_publiee_trente_jours_avant_puis_acceptee() {
        UUID ws = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        ouvrir(ws, "ESSENTIEL", "MENSUEL", "375");
        accepter(ws, user);
        courriels.clear();
        String texte = MODELE.replace("## Article 17 \u2014 Notifications", "## Article 17 \u2014 Notifications et avis");
        assertThatThrownBy(() -> service.publierVersion("2026-12-01", texte, LocalDate.of(2026, 11, 1), UUID.randomUUID()))
                .isInstanceOf(ContratException.class).hasMessageContaining("au moins 30 jours");
        String code = "v-" + UUID.randomUUID().toString().substring(0, 8);
        service.publierVersion(code, texte, LocalDate.of(2026, 11, 20), UUID.randomUUID());
        assertThat(courriels).extracting(c -> c[1]).contains("email/contrat-nouvelle-version");
        // Avant l'entree en vigueur : rien a accepter, la version a venir est annoncee.
        ContratService.Etat avant = service.etat(ws);
        assertThat(avant.acceptationRequise()).isFalse();
        assertThat(avant.versionAVenir().code()).isEqualTo(code);
        // Apres : acceptation demandee a la connexion suivante (sans bloquer le cabinet).
        horloge.regler(Clock.fixed(Instant.parse("2026-11-21T08:00:00Z"), ZoneOffset.UTC));
        ContratService.Etat apres = service.etat(ws);
        assertThat(apres.objetAAccepter()).isEqualTo(ContratService.OBJET_VERSION);
        assertThat(service.acceptationEnAttente(ws)).isFalse();
        ContratService.AcceptationVue acc = accepter(ws, user);
        assertThat(acc.versionCode()).isEqualTo(code);
        assertThat(service.etat(ws).acceptationRequise()).isFalse();
        // Toutes les versions sont conservees.
        assertThat(service.versions()).extracting(ContratService.VersionVue::code).contains("2026-10-12", code);
    }

    @Test
    void la_preuve_est_inalterable_meme_pour_le_proprietaire() {
        UUID ws = UUID.randomUUID();
        ouvrir(ws, "ESSENTIEL", "ANNUEL", "3750");
        ContratService.AcceptationVue acc = accepter(ws, UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update("UPDATE contrat_acceptations SET adresse_ip = '1.1.1.1' WHERE id = ?", acc.id()))
                .rootCause().hasMessageContaining("inalterable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM contrat_acceptations WHERE id = ?", acc.id()))
                .rootCause().hasMessageContaining("inalterable");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE contrat_acceptations CASCADE"))
                .rootCause().hasMessageContaining("inalterable");
        assertThatThrownBy(() -> jdbc.update("UPDATE conditions_particulieres SET prix_ht = 1 WHERE workspace_id = ?", ws))
                .rootCause().hasMessageContaining("figes");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM conditions_particulieres WHERE workspace_id = ?", ws))
                .rootCause().hasMessageContaining("suppression refusee");
        assertThatThrownBy(() -> jdbc.update("UPDATE contrat_versions SET code = 'x'"))
                .rootCause().hasMessageContaining("inalterable");
    }

    @Test
    void jurika_app_ni_update_ni_delete_sur_la_preuve_et_rls_par_cabinet() throws Exception {
        UUID wsA = UUID.randomUUID();
        UUID wsB = UUID.randomUUID();
        ouvrir(wsA, "ESSENTIEL", "ANNUEL", "3750");
        accepter(wsA, UUID.randomUUID());
        try (Connection app = DriverManager.getConnection(pg.getJdbcUrl(), "jurika_app", "jurika_app_it")) {
            app.setAutoCommit(false);
            try (Statement st = app.createStatement()) {
                st.execute("SELECT set_config('app.current_workspace_id', '" + wsA + "', true)");
                var rs = st.executeQuery("SELECT count(*) FROM contrat_acceptations");
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
            assertThat(sqlState(app, "UPDATE contrat_acceptations SET signataire_qualite = 'x'"))
                    .as("UPDATE refuse a jurika_app (REVOKE)").isEqualTo("42501");
            assertThat(sqlState(app, "DELETE FROM contrat_acceptations")).isEqualTo("42501");
            assertThat(sqlState(app, "DELETE FROM conditions_particulieres")).isEqualTo("42501");
            assertThat(sqlState(app, "DELETE FROM contrat_resiliations")).isEqualTo("42501");
            assertThat(sqlState(app, "UPDATE contrat_versions SET code = code")).isEqualTo("42501");
            app.rollback();
            // RLS : le cabinet B ne voit rien du cabinet A.
            try (Statement st = app.createStatement()) {
                st.execute("SELECT set_config('app.current_workspace_id', '" + wsB + "', true)");
                var rs = st.executeQuery("SELECT count(*) FROM conditions_particulieres WHERE workspace_id = '" + wsA + "'");
                rs.next();
                assertThat(rs.getInt(1)).isZero();
                rs = st.executeQuery("SELECT count(*) FROM contrat_acceptations");
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
            app.rollback();
            // Portee globale (super-admin, taches planifiees, passerelle) : lecture transverse.
            try (Statement st = app.createStatement()) {
                st.execute("SELECT set_config('app.contrat_portee', 'globale', true)");
                var rs = st.executeQuery("SELECT count(*) FROM conditions_particulieres WHERE workspace_id = '" + wsA + "'");
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
            app.rollback();
        }
    }

    @Test
    void chaque_modification_des_parametres_de_l_editeur_est_tracee() {
        UUID auteur = UUID.randomUUID();
        jdbc.update("UPDATE editeur_parametres SET ice = '001122334455667', forme_juridique = 'SARL', modifie_par = ? "
                + "WHERE id = 1", auteur);
        List<Map<String, Object>> traces = jdbc.queryForList(
                "SELECT champ, ancienne_valeur, nouvelle_valeur, modifie_par FROM editeur_parametres_historique "
                        + "WHERE modifie_par = ? ORDER BY champ", auteur);
        assertThat(traces).extracting(t -> t.get("champ")).containsExactly("forme_juridique", "ice");
        assertThat(traces.get(1).get("ancienne_valeur")).isNull();
        assertThat(traces.get(1).get("nouvelle_valeur")).isEqualTo("001122334455667");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM editeur_parametres_historique"))
                .rootCause().hasMessageContaining("inalterable");
        jdbc.update("UPDATE editeur_parametres SET ice = NULL, forme_juridique = NULL, modifie_par = ? WHERE id = 1", auteur);
    }

    private static String sqlState(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("SAVEPOINT s");
            try {
                st.executeUpdate(sql);
                return "OK";
            } catch (SQLException ex) {
                st.execute("ROLLBACK TO SAVEPOINT s");
                return ex.getSQLState();
            }
        }
    }

    private static BillingProperties proprietes() {
        BillingProperties p = new BillingProperties();
        p.setSalesEmail("ventes@editeur.test");
        return p;
    }
}
