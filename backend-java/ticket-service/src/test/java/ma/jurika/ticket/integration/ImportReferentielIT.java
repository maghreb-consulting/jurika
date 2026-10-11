package ma.jurika.ticket.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ticket.application.referentiel.ImportReferentielDemarches;
import ma.jurika.ticket.application.referentiel.ReferentielCorpus;
import ma.jurika.ticket.application.referentiel.ReferentielCorpus.Condition;
import ma.jurika.ticket.application.referentiel.ReferentielCorpus.Decision;
import ma.jurika.ticket.application.referentiel.ReferentielCorpus.Delai;
import ma.jurika.ticket.application.referentiel.ReferentielCorpus.Ligne;
import ma.jurika.ticket.application.referentiel.ReferentielCorpus.Rapport;
import ma.jurika.ticket.application.referentiel.ReferentielCorpus.Renvoi;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L4b (E2, RG-DEM-01) : import versionne du referentiel du corpus, sur une vraie base
 * (migrations jusqu'a V34). Une version refusee ne touche a rien ; une version appliquee
 * desactive la precedente sans la supprimer ; les 51 lignes de la creation en service ne
 * bougent pas.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ImportReferentielIT {

    static final UUID ADMIN = UUID.fromString("44444444-4444-4444-4444-444444444444");

    final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_v34")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("testcontainers-init.sql");

    JdbcTemplate db;
    final AtomicReference<ReferentielCorpus> source = new AtomicReference<>();
    ImportReferentielDemarches imports;

    @BeforeAll
    void demarrer() {
        pg.start();
        db = new JdbcTemplate(new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()));
        migrer("filesystem:../auth-service/src/main/resources/db/migration", "flyway_history_auth", null);
        migrer("classpath:db/migration", "flyway_history_ticket", "20");
        migrer("filesystem:../dataroom-service/src/main/resources/db/migration", "flyway_history_dataroom", null);
        migrer("classpath:db/migration", "flyway_history_ticket", null);
        imports = new ImportReferentielDemarches(source::get, db, new ObjectMapper());
    }

    @AfterAll
    void arreter() {
        pg.stop();
    }

    /** Parcours de creation du corpus pour la comparaison (E3) ; vide sauf mention. */
    static List<Ligne> creation = List.of();

    static ReferentielCorpus version(String v, List<String> erreurs) {
        Ligne depot = new Ligne("MATRICE_COMPTES_RESULTAT_SARL", "03_COMPTES", "SARL", 1, "depot_etats_synthese", null,
                "DEROULEMENT_DEMARCHE", "D\u00e9p\u00f4t des \u00e9tats de synth\u00e8se au greffe",
                "Greffe du tribunal de commerce", new Condition("CAC_EXISTE", "EGAL", "oui", "Si un commissaire"),
                false, new Delai("30 jours \u2014 texte de la matrice : D\u00e9p\u00f4t au greffe dans les trente jours",
                30, "JOURS", null, null), "R\u00e9c\u00e9piss\u00e9", null, 13, null, null);
        Ligne reduction = new Ligne("MATRICE_DECISIONS_MODIFICATIONS_SARL", "02_MODIFICATIONS", "SARL", 1,
                "reduction_capital", new Condition("REDUCTION_MOTIF", "EGAL", "retrait_associe", "cause \u00ab deces \u00bb"),
                "GENERATION_DOCUMENTS", "G\u00e9n\u00e9ration du document PV_AG_SARL", "Cabinet", null, true,
                new Delai("au plus tard le $ANNONCE_LEGALE_DATE_LIMITE", null, null, null, "ANNONCE_LEGALE_DATE_LIMITE"),
                null, "PV_AG_SARL", 21, null, null);
        return new ReferentielCorpus(v, List.of(depot, reduction),
                List.of(new Renvoi("MATRICE_DECISIONS_MODIFICATIONS_SARL", 1, "A", "Approbation", "approbation_comptes",
                        "COMPTES", "MATRICE_COMPTES_RESULTAT_SARL", 1, "La matrice de la famille fait foi")),
                List.of(new Decision("MATRICE_COMPTES_RESULTAT_SARL", 13, "D", "depot_etats_synthese",
                        "D\u00e9p\u00f4t des \u00e9tats de synth\u00e8se", "Formalit\u00e9")),
                creation,
                new Rapport(Map.of("MATRICE_COMPTES_RESULTAT_SARL", 1), 2, List.of(), List.of(), erreurs));
    }

    private int corpusActives() {
        return db.queryForObject("SELECT count(*) FROM demarches_referentiel WHERE corpus_version IS NOT NULL AND actif",
                Integer.class);
    }

    @Test
    @Order(1)
    void une_version_au_rapport_errone_est_refusee_et_ne_touche_a_rien() {
        source.set(version("CORPUS_A", List.of("MATRICE_X : statut inconnu")));
        ImportReferentielDemarches.Resultat r = imports.importer(ADMIN);
        assertThat(r.statut()).isEqualTo("REFUSE");
        assertThat(r.motif()).contains("statut inconnu");
        assertThat(corpusActives()).isZero();
        assertThat(imports.versionActive()).isNull();
        assertThat(imports.journal()).hasSize(1);
    }

    @Test
    @Order(2)
    void une_version_saine_est_appliquee_sans_toucher_a_la_creation() {
        int creation = db.queryForObject("SELECT count(*) FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND actif",
                Integer.class);
        source.set(version("CORPUS_A", List.of()));
        ImportReferentielDemarches.Resultat r = imports.importer(ADMIN);
        assertThat(r.statut()).isEqualTo("APPLIQUE");
        assertThat(r.lignes()).isEqualTo(2);
        assertThat(corpusActives()).isEqualTo(2);
        assertThat(imports.versionActive()).isEqualTo("CORPUS_A");
        Map<String, Object> depot = db.queryForMap("SELECT * FROM demarches_referentiel WHERE decision_code = 'depot_etats_synthese'");
        assertThat(depot).containsEntry("workflow_type", "CORPUS").containsEntry("obligatoire", "C")
                .containsEntry("condition_variable", "CAC_EXISTE").containsEntry("condition_operateur", "EGAL")
                .containsEntry("condition_valeur", "oui").containsEntry("phase_code", "S3")
                .containsEntry("delai_unite", "JOURS").containsEntry("forme", "SARL");
        assertThat(((Number) depot.get("delai_valeur")).intValue()).isEqualTo(30);
        Map<String, Object> reduction = db.queryForMap("SELECT * FROM demarches_referentiel WHERE decision_code = 'reduction_capital'");
        assertThat(reduction).containsEntry("sous_condition_variable", "REDUCTION_MOTIF")
                .containsEntry("sous_condition_texte", "cause \u00ab deces \u00bb")
                .containsEntry("delai_echeance_donnee", "ANNONCE_LEGALE_DATE_LIMITE");
        assertThat(db.queryForObject("SELECT count(*) FROM referentiel_renvois WHERE actif", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM referentiel_decisions WHERE actif", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND actif",
                Integer.class)).as("creation en service intacte").isEqualTo(creation).isEqualTo(51);
    }

    @Test
    @Order(5)
    @SuppressWarnings("unchecked")
    void le_parcours_de_creation_du_corpus_est_compare_aux_lignes_en_service() throws Exception {
        Map<String, Object> un = db.queryForMap("SELECT * FROM demarches_referentiel WHERE workflow_type = 'CREATION' "
                + "AND corpus_version IS NULL AND ordre = 1");
        Map<String, Object> deux = db.queryForMap("SELECT * FROM demarches_referentiel WHERE workflow_type = 'CREATION' "
                + "AND corpus_version IS NULL AND ordre = 2");
        // Presentation du corpus ignoree : case en tete de l'etape, tiret pour une cellule vide.
        creation = List.of(ligneCreation(un, "\u2610 " + un.get("libelle")), ligneCreation(deux, "Libell\u00e9 modifi\u00e9 par le cabinet"));
        source.set(version("CORPUS_C", List.of()));
        ImportReferentielDemarches.Resultat r = imports.importer(ADMIN);
        assertThat(r.statut()).isEqualTo("APPLIQUE");
        String brut = db.queryForObject("SELECT rapport::text FROM referentiel_imports WHERE id = ?", String.class, r.id());
        List<String> ecarts = (List<String>) new ObjectMapper().readValue(brut, Map.class).get("ecartsCreation");
        assertThat(ecarts).noneMatch(e -> e.startsWith("n\u00b0 1 :"));
        assertThat(ecarts).contains("n\u00b0 2 : d\u00e9marche : en service \u00ab " + deux.get("libelle")
                + " \u00bb, corpus \u00ab Libell\u00e9 modifi\u00e9 par le cabinet \u00bb");
        assertThat(ecarts).filteredOn(e -> e.contains("absente du corpus")).hasSize(49);
        creation = List.of();
    }

    private static Ligne ligneCreation(Map<String, Object> l, String libelle) {
        String delai = (String) l.get("delai");
        String cond = (String) l.get("condition_application");
        return new Ligne("PARCOURS_CREATION", "01_CREATION", null, ((Number) l.get("ordre")).intValue(), "creation", null,
                (String) l.get("statut_ticket"), libelle, null,
                cond == null ? null : new Condition(null, "PHRASE", null, cond), true,
                delai == null ? null : new Delai(delai, null, null, null, null),
                (String) l.get("justificatifs_texte"), (String) l.get("modele_jurika"),
                ((Number) l.get("ordre")).intValue(), null, null);
    }

    @Test
    @Order(3)
    void la_meme_version_n_est_pas_reimportee() {
        source.set(version("CORPUS_A", List.of()));
        assertThat(imports.importer(ADMIN).statut()).isEqualTo("REFUSE");
        assertThat(corpusActives()).isEqualTo(2);
    }

    @Test
    @Order(4)
    void une_nouvelle_version_desactive_la_precedente_sans_la_supprimer() {
        source.set(version("CORPUS_B", List.of()));
        assertThat(imports.importer(ADMIN).statut()).isEqualTo("APPLIQUE");
        assertThat(corpusActives()).isEqualTo(2);
        assertThat(db.queryForObject("SELECT count(*) FROM demarches_referentiel WHERE corpus_version = 'CORPUS_A' AND NOT actif",
                Integer.class)).isEqualTo(2);
        assertThat(imports.versionActive()).isEqualTo("CORPUS_B");
        assertThat(imports.journal()).extracting(m -> m.get("statut"))
                .containsExactly("APPLIQUE", "REFUSE", "APPLIQUE", "REFUSE");
        assertThat(db.queryForObject("SELECT rapport->'ecartsCreation'->>0 FROM referentiel_imports "
                + "WHERE corpus_version = 'CORPUS_B'", String.class)).isEqualTo("Parcours de cr\u00e9ation absent du corpus.");
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
