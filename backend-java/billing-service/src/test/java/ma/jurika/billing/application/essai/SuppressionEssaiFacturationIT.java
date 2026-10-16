package ma.jurika.billing.application.essai;

import com.zaxxer.hikari.HikariDataSource;
import ma.jurika.billing.application.essai.SuppressionFacturationEssaiService.Bilan;
import ma.jurika.billing.application.essai.SuppressionFacturationEssaiService.BilanPurge;
import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.trial.TrialAccessChecker;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L7 (E14), RG-ESS-10 et decision du 2026-10-14 : donnees de facturation d'un essai supprime
 * definitivement (billing V18), sur une vraie base.
 *
 * <ul>
 *   <li>garde de classement : toute table de jurika_billing portant un cabinet (ou liee a une
 *       telle table) est classee ({@link ClassementFacturationEssai}) ;</li>
 *   <li>paiements non aboutis et leurs retours du CMI supprimes ;</li>
 *   <li>paiements aboutis et factures conserves, echeance a +10 ans au registre ;</li>
 *   <li>preuves du contrat conservees, duree A_DECIDER ;</li>
 *   <li>refus (rien n'est supprime) : cabinet hors essai, abonnement actif, paiement a traiter ;</li>
 *   <li>purge refusee avant l'echeance (garde-fou de la fonction), faite apres, tracee ;
 *       registre inalterable ; droits de jurika_app.</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SuppressionEssaiFacturationIT {

    private static final ZoneId CASA = ZoneId.of("Africa/Casablanca");
    private static final AtomicInteger RANG = new AtomicInteger();

    private final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_billing")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("contrat-it-init.sql");

    private HikariDataSource ds;
    private JdbcTemplate jdbc;
    private SuppressionFacturationEssaiService service;
    /** Etat d'essai rendu par auth-service (simule) : par defaut, tout cabinet est un essai en lecture seule. */
    private final Map<UUID, String> etats = new HashMap<>();

    @BeforeAll
    void demarrer() {
        pg.start();
        Flyway.configure().dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations("classpath:db/migration").table("flyway_history_billing").load().migrate();
        ds = new HikariDataSource();
        ds.setJdbcUrl(pg.getJdbcUrl());
        ds.setUsername(pg.getUsername());
        ds.setPassword(pg.getPassword());
        jdbc = new JdbcTemplate(ds);
        TrialAccessChecker checker = ws -> Optional.of(new TrialAccessChecker.TrialState(ws,
                etats.getOrDefault(ws, "TRIAL_EXPIRED"), 0, null, null));
        StaticListableBeanFactory beans = new StaticListableBeanFactory(Map.of("checker", checker));
        service = new SuppressionFacturationEssaiService(jdbc, new DataSourceTransactionManager(ds),
                beans.getBeanProvider(TrialAccessChecker.class));
    }

    @AfterAll
    void arreter() {
        if (ds != null) ds.close();
        pg.stop();
    }

    @BeforeEach
    void vider() {
        etats.clear();
    }

    // ---------------------------------------------------------------- donnees

    private long paiement(UUID ws, String statut, String oid, boolean propagee) {
        return jdbc.queryForObject("""
                INSERT INTO payments (workspace_id, plan_code, amount_mad_cents, method, status, cmi_oid,
                                      activation_propagee, objet)
                VALUES (?, 'essentiel', 120000, ?, ?, ?, ?, 'SOUSCRIPTION') RETURNING id
                """, Long.class, ws, oid == null ? "BANK_TRANSFER" : "CARD", statut, oid, propagee);
    }

    private void retourCmi(String oid, Long paiement) {
        jdbc.update("INSERT INTO cmi_retours (oid, signature_valide, code_retour, reponse_rendue, payment_id) "
                + "VALUES (?, true, '99', 'APPROVED', ?)", oid, paiement);
    }

    private void facture(UUID ws, long paiement) {
        int rang = RANG.incrementAndGet();
        jdbc.update("""
                INSERT INTO factures (numero, annee, rang, workspace_id, payment_id, date_emission, designation,
                                      mode_paiement, montant_ht_centimes, taux_tva, montant_tva_centimes,
                                      montant_ttc_centimes, editeur, cabinet, pdf)
                VALUES (?, 2026, ?, ?, ?, DATE '2026-10-01', 'Abonnement Essentiel', 'CARD', 100000, 20.00, 20000,
                        120000, '{}'::jsonb, '{}'::jsonb, '\\x25504446'::bytea)
                """, "F2026-" + String.format("%06d", rang), rang, ws, paiement);
    }

    private void abonnement(UUID ws, String statut) {
        jdbc.update("INSERT INTO subscriptions (workspace_id, plan_code, status, current_period_start, current_period_end) "
                + "VALUES (?, 'essentiel', ?, now(), now() + interval '1 month')", ws, statut);
    }

    private void conditions(UUID ws) {
        jdbc.update("""
                INSERT INTO conditions_particulieres (workspace_id, reference, forfait, rythme_paiement, prix_ht,
                                                      date_debut, date_fin, origine, echeance_courante)
                VALUES (?, ?, 'ESSENTIEL', 'ANNUEL', 1000, DATE '2026-10-01', DATE '2027-10-01', 'ABONNEMENT',
                        DATE '2027-10-01')
                """, ws, "CP-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private long compter(String table, UUID ws) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE workspace_id = ?", Long.class, ws);
    }

    private long retoursCmi(String oid) {
        return jdbc.queryForObject("SELECT count(*) FROM cmi_retours WHERE oid = ?", Long.class, oid);
    }

    private static String oid() {
        return "ESSAI-" + UUID.randomUUID().toString().substring(0, 12);
    }

    private static String etatSql(Throwable t) {
        Throwable c = t;
        while (c != null && !(c instanceof SQLException)) c = c.getCause();
        return c == null ? null : ((SQLException) c).getSQLState();
    }

    // ----------------------------------------------------------------- tests

    @Test
    void garde_toute_table_portant_un_cabinet_est_classee() {
        Set<String> tables = new TreeSet<>(jdbc.queryForList("""
                WITH cabinet AS (
                    SELECT c.table_name::text AS t FROM information_schema.columns c
                     WHERE c.table_schema = 'public' AND c.column_name = 'workspace_id'
                ), liees AS (
                    SELECT DISTINCT src.relname::text AS t
                      FROM pg_constraint k
                      JOIN pg_class src ON src.oid = k.conrelid
                      JOIN pg_class cible ON cible.oid = k.confrelid
                      JOIN pg_namespace n ON n.oid = src.relnamespace
                     WHERE k.contype = 'f' AND n.nspname = 'public'
                       AND cible.relname::text IN (SELECT t FROM cabinet)
                )
                SELECT t FROM cabinet UNION SELECT t FROM liees
                """, String.class));
        assertThat(tables)
                .as("Chaque table de jurika_billing portant un cabinet a un sort a la suppression d'un essai "
                        + "(ClassementFacturationEssai et fonction essai_facturation_supprimer, billing V18)")
                .containsExactlyInAnyOrderElementsOf(ClassementFacturationEssai.TABLES.keySet());
        // Chaque regle a une raison.
        ClassementFacturationEssai.TABLES.forEach((t, r) -> assertThat(r.raison()).as(t).isNotBlank());
    }

    @Test
    void essai_avec_paiement_echoue_et_retour_cmi_tout_est_supprime() {
        UUID ws = UUID.randomUUID();
        UUID autre = UUID.randomUUID();
        String o1 = oid();
        String o2 = oid();
        long echoue = paiement(ws, "FAILED", o1, false);
        retourCmi(o1, echoue);
        long enAttente = paiement(ws, "PENDING", o2, false);
        retourCmi(o2, null); // retour journalise sans lien (commande inconnue a l'epoque) : par oid
        paiement(ws, "CANCELLED", null, false);
        abonnement(ws, "cancelled");
        long autrePaiement = paiement(autre, "FAILED", null, false);

        Bilan b = service.supprimer(ws);

        assertThat(b.supprimees()).containsEntry("payments", 3L).containsEntry("cmi_retours", 2L)
                .containsEntry("subscriptions", 1L).containsEntry("payment_methods", 0L);
        assertThat(b.conservees10Ans()).containsEntry("payments", 0L).containsEntry("factures", 0L);
        assertThat(b.echeanceConservation()).as("rien de conserve : aucun registre").isNull();
        assertThat(compter("payments", ws)).isZero();
        assertThat(compter("subscriptions", ws)).isZero();
        assertThat(retoursCmi(o1)).isZero();
        assertThat(retoursCmi(o2)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE id IN (?, ?)", Long.class, echoue, enAttente))
                .isZero();
        assertThat(compter("conservation_comptable", ws)).isZero();
        // Un autre cabinet n'est pas touche.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE id = ?", Long.class, autrePaiement)).isOne();
    }

    @Test
    void essai_avec_paiement_abouti_et_facture_conserves_avec_echeance_a_dix_ans() {
        UUID ws = UUID.randomUUID();
        String o = oid();
        long abouti = paiement(ws, "COMPLETED", o, true);
        retourCmi(o, abouti);
        facture(ws, abouti);
        paiement(ws, "FAILED", null, false);
        conditions(ws);

        Bilan b = service.supprimer(ws);

        // Regle : date (Casablanca) de la suppression + 10 ans, calculee avec l'horloge et les regles de fuseau de la
        // base (celles de la JVM peuvent differer autour de minuit : echec vu le 2026-10-15 a 23 h 40 UTC).
        LocalDate attendue = jdbc.queryForObject("SELECT ((essai_supprime_le AT TIME ZONE 'Africa/Casablanca')::date"
                + " + INTERVAL '10 years')::date FROM conservation_comptable WHERE workspace_id = ?", LocalDate.class, ws);
        assertThat(b.echeanceConservation()).isEqualTo(attendue);
        assertThat(attendue).isBetween(LocalDate.now(CASA).plusYears(10).minusDays(1), LocalDate.now(CASA).plusYears(10).plusDays(1));
        assertThat(b.supprimees()).containsEntry("payments", 1L).containsEntry("cmi_retours", 0L);
        assertThat(b.conservees10Ans()).containsEntry("payments", 1L).containsEntry("factures", 1L)
                .containsEntry("cmi_retours", 1L);
        assertThat(b.conserveesDureeADecider()).containsEntry("conditions_particulieres", 1L);
        assertThat(b.dureePreuvesContrat()).isEqualTo("A_DECIDER");
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE workspace_id = ?", String.class, ws))
                .isEqualTo("COMPLETED");
        assertThat(compter("factures", ws)).isOne();
        assertThat(compter("conditions_particulieres", ws)).isOne();
        assertThat(retoursCmi(o)).isOne();
        Map<String, Object> reg = jdbc.queryForMap("SELECT * FROM conservation_comptable WHERE workspace_id = ?", ws);
        assertThat(reg.get("echeance").toString()).isEqualTo(attendue.toString());
        assertThat(reg).containsEntry("paiements_conserves", 1).containsEntry("factures_conservees", 1)
                .containsEntry("retours_cmi_conserves", 1).containsEntry("autres_conservees", 1)
                .containsEntry("autres_duree", "A_DECIDER");
        assertThat(reg.get("purge_le")).isNull();

        // Un second appel (auth rejoue apres un echec de jurika_db) ne change pas l'echeance.
        Bilan b2 = service.supprimer(ws);
        assertThat(b2.echeanceConservation()).isEqualTo(attendue);
        assertThat(compter("factures", ws)).isOne();
    }

    @Test
    void refus_sans_rien_supprimer_hors_essai_abonnement_actif_ou_paiement_a_traiter() {
        UUID converti = UUID.randomUUID();
        paiement(converti, "FAILED", null, false);
        etats.put(converti, "CONVERTED");
        assertThatThrownBy(() -> service.supprimer(converti)).isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).code()).isEqualTo("ESSAI_SUPPRESSION_REFUSEE"));
        assertThat(compter("payments", converti)).isOne();

        UUID actif = UUID.randomUUID();
        paiement(actif, "FAILED", null, false);
        abonnement(actif, "active");
        assertThatThrownBy(() -> service.supprimer(actif)).isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).code()).isEqualTo("ESSAI_ABONNEMENT_ACTIF"));
        assertThat(compter("payments", actif)).isOne();
        assertThat(compter("subscriptions", actif)).isOne();

        UUID sansFacture = UUID.randomUUID();
        paiement(sansFacture, "FAILED", null, false);
        paiement(sansFacture, "COMPLETED", null, true);
        assertThatThrownBy(() -> service.supprimer(sansFacture)).isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).code()).isEqualTo("ESSAI_PAIEMENT_A_TRAITER"));
        assertThat(compter("payments", sansFacture)).isEqualTo(2);

        UUID nonPropage = UUID.randomUUID();
        long p = paiement(nonPropage, "COMPLETED", null, false);
        facture(nonPropage, p);
        assertThatThrownBy(() -> service.supprimer(nonPropage)).isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).code()).isEqualTo("ESSAI_PAIEMENT_A_TRAITER"));
        assertThat(compter("conservation_comptable", nonPropage)).isZero();
    }

    @Test
    void purge_refusee_avant_l_echeance_effectuee_apres_et_tracee() {
        // Essai supprime aujourd'hui : purge refusee par la fonction.
        UUID recent = UUID.randomUUID();
        long p1 = paiement(recent, "COMPLETED", null, true);
        facture(recent, p1);
        service.supprimer(recent);
        assertThatThrownBy(() -> service.purger(recent)).isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).code()).isEqualTo("CONSERVATION_NON_ECHUE"));
        assertThatThrownBy(() -> jdbc.queryForList("SELECT * FROM conservation_comptable_purger(?)", recent))
                .satisfies(e -> assertThat(etatSql(e)).isEqualTo("JE012"));
        assertThat(compter("factures", recent)).isOne();

        // Le jour de l'echeance : encore refusee (echeance non passee).
        UUID jourJ = UUID.randomUUID();
        registreAncien(jourJ, "10 years");
        assertThatThrownBy(() -> service.purger(jourJ))
                .satisfies(e -> assertThat(((BusinessException) e).code()).isEqualTo("CONSERVATION_NON_ECHUE"));

        // Essai supprime il y a plus de 10 ans : purge des seuls paiements aboutis et factures.
        UUID ancien = UUID.randomUUID();
        String o = oid();
        long p2 = paiement(ancien, "COMPLETED", o, true);
        retourCmi(o, p2);
        facture(ancien, p2);
        conditions(ancien);
        registreAncien(ancien, "10 years 1 day");

        BilanPurge bilan = service.purgerEcheancesPassees();
        assertThat(bilan.reussis()).isGreaterThanOrEqualTo(1);
        assertThat(bilan.echecs()).isZero();
        assertThat(compter("payments", ancien)).isZero();
        assertThat(compter("factures", ancien)).isZero();
        assertThat(retoursCmi(o)).isZero();
        assertThat(compter("conditions_particulieres", ancien)).as("rien d'autre n'est purge").isOne();
        Map<String, Object> reg = jdbc.queryForMap("SELECT * FROM conservation_comptable WHERE workspace_id = ?", ancien);
        assertThat(reg.get("purge_le")).isNotNull();
        assertThat(reg).containsEntry("purge_paiements", 1).containsEntry("purge_factures", 1)
                .containsEntry("purge_retours_cmi", 1).containsEntry("purge_factures_stripe", 0);
        // Les cabinets non echus n'ont pas ete touches par le passage.
        assertThat(compter("factures", recent)).isOne();
        assertThatThrownBy(() -> service.purger(ancien))
                .satisfies(e -> assertThat(((BusinessException) e).code()).isEqualTo("CONSERVATION_DEJA_PURGEE"));
    }

    /** Registre d'un essai supprime il y a {@code anciennete} (date de Casablanca), comme l'ecrit la fonction. */
    private void registreAncien(UUID ws, String anciennete) {
        jdbc.update("""
                INSERT INTO conservation_comptable (workspace_id, essai_supprime_le, echeance, paiements_conserves,
                        retours_cmi_conserves, factures_conservees, factures_stripe_conservees, autres_conservees,
                        autres_duree)
                SELECT ?, t, ((t AT TIME ZONE 'Africa/Casablanca')::date + INTERVAL '10 years')::date, 1, 1, 1, 0, 1,
                       'A_DECIDER'
                  FROM (SELECT now() - CAST(? AS interval) AS t) s
                """, ws, anciennete);
    }

    @Test
    void registre_inalterable_et_droits_de_jurika_app() throws Exception {
        UUID ws = UUID.randomUUID();
        long p = paiement(ws, "COMPLETED", null, true);
        facture(ws, p);
        service.supprimer(ws);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM conservation_comptable WHERE workspace_id = ?", ws))
                .rootCause().hasMessageContaining("inalterable");
        assertThatThrownBy(() -> jdbc.update("UPDATE conservation_comptable SET echeance = echeance - 3650 "
                + "WHERE workspace_id = ?", ws)).rootCause().hasMessageContaining("inalterable");
        assertThatThrownBy(() -> jdbc.update("UPDATE conservation_comptable SET purge_le = now(), purge_paiements = 0, "
                + "purge_retours_cmi = 0, purge_factures = 0, purge_factures_stripe = 0 WHERE workspace_id = ?", ws))
                .as("pose de la purge avant l'echeance").rootCause().hasMessageContaining("inalterable");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE conservation_comptable"))
                .rootCause().hasMessageContaining("inalterable");
        // Echeance incoherente (pas 10 ans) : contrainte.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO conservation_comptable (workspace_id, essai_supprime_le, echeance, paiements_conserves,
                        retours_cmi_conserves, factures_conservees, factures_stripe_conservees, autres_conservees,
                        autres_duree)
                VALUES (?, now(), DATE '2027-01-01', 0, 0, 0, 0, 0, 'A_DECIDER')
                """, UUID.randomUUID())).rootCause().hasMessageContaining("ck_conservation_dix_ans");

        try (Connection app = DriverManager.getConnection(pg.getJdbcUrl(), "jurika_app", "jurika_app_it")) {
            assertThat(etat(app, "DELETE FROM conservation_comptable")).isEqualTo("42501");
            assertThat(etat(app, "UPDATE conservation_comptable SET autres_duree = 'A_DECIDER'")).isEqualTo("42501");
            assertThat(etat(app, "INSERT INTO conservation_comptable (workspace_id, essai_supprime_le, echeance, "
                    + "paiements_conserves, retours_cmi_conserves, factures_conservees, factures_stripe_conservees, "
                    + "autres_conservees, autres_duree) VALUES (gen_random_uuid(), now(), "
                    + "((now() AT TIME ZONE 'Africa/Casablanca')::date + INTERVAL '10 years')::date, 0, 0, 0, 0, 0, "
                    + "'A_DECIDER')")).isEqualTo("42501");
            assertThat(etat(app, "DELETE FROM factures")).isEqualTo("42501");
            // La purge passe par la fonction, dont le garde-fou s'applique aussi a jurika_app.
            assertThat(etat(app, "SELECT * FROM conservation_comptable_purger('" + ws + "')")).isEqualTo("JE012");
        }
        assertThat(compter("factures", ws)).isOne();
    }

    private static String etat(Connection c, String sql) {
        try (Statement st = c.createStatement()) {
            st.execute(sql);
            return "OK";
        } catch (SQLException e) {
            return e.getSQLState();
        }
    }
}
