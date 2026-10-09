package ma.jurika.dataroom.integration;

import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.DocumentSummary;
import ma.jurika.dataroom.api.dto.DataroomDtos.DossierTicket;
import ma.jurika.dataroom.application.DataroomJuridiqueService;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot B, énoncé 13 — <b>L'ANNONCE LÉGALE SE FAIT EN DEUX TEMPS, ET NE DONNE
 * QU'UN SEUL DOCUMENT.</b>
 *
 * <p>Le parcours du 9 septembre produit l'annonce légale <b>deux fois</b> : une
 * première au statut 2, avec ce qu'on sait alors de la société ; une seconde
 * après l'immatriculation, complétée du <b>numéro de registre du commerce</b>,
 * qui est la version publiée. Ce sont la ligne 11 et la ligne 30 du parcours, et
 * elles portent le même modèle.
 *
 * <p>Le risque est là : deux générations, même emplacement. Si la seconde crée
 * une ligne à côté de la première au lieu de la remplacer, le dossier montre
 * <b>deux annonces légales en vigueur</b> — l'une avec le numéro RC, l'autre
 * sans — sans rien qui dise laquelle est la bonne. Personne ne verrait l'erreur :
 * les deux documents sont valides, seul leur nombre est faux. C'est le défaut que
 * ce test empêche de revenir.
 *
 * <p>Il l'éprouve sur le chemin réel — brouillon, puis validation — et sur une
 * base réelle, parce que la règle est <b>tenue par la base</b> : l'index unique
 * partiel {@code ux_dataroom_documents_courant_par_slot} (migration V23) refuse
 * une seconde ligne en vigueur sur le même emplacement. Un test à mocks
 * n'éprouverait que le code qui est censé l'éviter, pas le garde-fou qui le
 * rattrape.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
        }
)
@ActiveProfiles("it")
class AnnonceLegaleDeuxTempsIT {

    /** Le type et le titre que l'étape 7 pose pour ce modèle — l'emplacement. */
    private static final String TYPE = "ANNONCE_JAL";
    private static final String TITRE = "Annonce légale";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_annonce")
            .withUsername("jurika_it")
            .withPassword("jurika_it_pwd")
            .withInitScript("testcontainers-init.sql")
            .withReuse(false);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        SchemaJurikaDb.migrer(POSTGRES);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        // Lot L0 (E15) : l'application tourne en role d'execution jurika_app (la
        // RLS s'applique) ; Flyway migre avec le proprietaire.
        registry.add("spring.datasource.username", () -> "jurika_app");
        registry.add("spring.datasource.password", () -> "jurika_app_it");
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("jurika.minio.endpoint", () -> "http://localhost:9099");
        registry.add("jurika.minio.access-key", () -> "test");
        registry.add("jurika.minio.secret-key", () -> "test-secret");
        registry.add("jurika.minio.bucket", () -> "jurika-it");
    }

    /** Preparation et assertions en PROPRIETAIRE, hors RLS (lot L0). */
    private final JdbcTemplate jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    @Autowired private DataroomJuridiqueService juridique;
    @MockBean private ObjectStorage storage;

    private UUID workspaceId;
    private UUID dossierId;
    private UUID ticketId;
    private UUID employeId;

    @BeforeEach
    void seed() {
        jdbc.execute("SELECT set_config('app.current_workspace_id', '', false)");
        jdbc.execute("ALTER TABLE dataroom_documents DISABLE ROW LEVEL SECURITY");
        jdbc.execute("DELETE FROM dataroom_visibilite_evenements");
        jdbc.execute("DELETE FROM ticket_document_snapshots");
        jdbc.execute("DELETE FROM dataroom_documents");
        jdbc.execute("DELETE FROM tickets");
        jdbc.execute("DELETE FROM entreprise_dossiers");
        jdbc.execute("DELETE FROM workspaces");
        jdbc.execute("ALTER TABLE dataroom_documents ENABLE ROW LEVEL SECURITY");
        jdbc.execute("ALTER TABLE dataroom_documents "
                + "DROP CONSTRAINT IF EXISTS dataroom_documents_uploaded_by_fkey");
        jdbc.execute("ALTER TABLE dataroom_visibilite_evenements "
                + "DROP CONSTRAINT IF EXISTS dataroom_visibilite_evenements_acteur_id_fkey");

        workspaceId = UUID.randomUUID();
        dossierId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
        employeId = UUID.randomUUID();

        SchemaJurikaDb.workspace(jdbc, workspaceId, "Cabinet Annonce", "JUR-A0001");
        jdbc.update("INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale, forme_juridique) VALUES (?, ?, ?, 'SARL')",
                dossierId, workspaceId, "PARACOSME");
        jdbc.update("""
                INSERT INTO tickets(id, workspace_id, reference, titre, type, statut,
                                    dossier_id, created_at, cree_par_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, '33333333-3333-3333-3333-333333333333')
                """, ticketId, workspaceId, "T-2026-00841", "Creation SARL PARACOSME",
                "CREATION", "GENERATION_DOCUMENTS", dossierId,
                OffsetDateTime.parse("2026-09-01T09:00:00Z"));

        TenantContext.set(workspaceId);
        jdbc.execute("SELECT set_config('app.current_workspace_id', '" + workspaceId + "', false)");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // =================================================================
    //  Le chemin réel : générer produit un brouillon, valider le publie
    // =================================================================

    private MockMultipartFile docx(String nom) {
        return new MockMultipartFile("file", nom,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                ("contenu " + nom).getBytes());
    }

    /** Ce que fait l'étape 7 quand le moteur a rendu le document. */
    private DocumentSummary genererBrouillon(String fichier) {
        return juridique.enregistrerBrouillon(dossierId, ticketId, TYPE, TITRE,
                docx(fichier), employeId);
    }

    /** Ce que fait l'employé en validant l'acte. */
    private DocumentSummary valider(UUID brouillonId, String motif) {
        return juridique.validerBrouillon(brouillonId, motif, employeId);
    }

    /** Les lignes EN VIGUEUR de cet emplacement — il ne doit jamais y en avoir deux. */
    private List<Map<String, Object>> enVigueurSurLeSlot() {
        // `version` est un SMALLINT : projeté en `int` pour que l'assertion ne
        // dépende pas du type que le pilote JDBC choisit de rendre.
        return jdbc.queryForList("""
                SELECT id, version::int AS version, is_current, replaced_at, motif, visible_client
                  FROM dataroom_documents
                 WHERE workspace_id = ? AND dossier_id = ?
                   AND document_type = ? AND title = ?
                   AND is_current AND NOT brouillon
                """, workspaceId, dossierId, TYPE, TITRE);
    }

    private Map<String, Object> ligne(UUID id) {
        return jdbc.queryForMap("""
                SELECT version::int AS version, is_current, brouillon, replaced_at,
                       motif, visible_client
                  FROM dataroom_documents WHERE id = ?
                """, id);
    }

    // =================================================================

    @Test
    @DisplayName("Les deux générations donnent UNE annonce en vigueur, en version 2")
    void deuxGenerationsUnSeulDocumentEnVigueur() {
        // Statut 2 — l'annonce est produite avec ce qu'on sait alors.
        UUID avantRc = valider(genererBrouillon("annonce-sans-rc.docx").id(), null).id();

        // Statut 4 — la société est immatriculée ; l'annonce est REGÉNÉRÉE,
        // complétée du numéro RC, et c'est celle-là qui part au journal.
        DocumentSummary apresRc = valider(genererBrouillon("annonce-avec-rc.docx").id(),
                "Complétée du numéro de registre du commerce");

        List<Map<String, Object>> courants = enVigueurSurLeSlot();
        assertThat(courants)
                .as("deux annonces en vigueur, l'une avec le RC et l'autre sans, "
                        + "sans rien qui dise laquelle est la bonne")
                .hasSize(1);
        assertThat(courants.get(0).get("id")).isEqualTo(apresRc.id());
        assertThat(courants.get(0).get("version")).isEqualTo(2);

        assertThat(apresRc.id())
                .as("la seconde génération crée bien une NOUVELLE ligne : "
                        + "l'annonce d'origine doit rester relisible")
                .isNotEqualTo(avantRc);
    }

    @Test
    @DisplayName("La première annonce bascule en historique, datée et motivée")
    void lAnnonceDOrigineBasculeEnHistorique() {
        UUID avantRc = valider(genererBrouillon("annonce-sans-rc.docx").id(), null).id();
        valider(genererBrouillon("annonce-avec-rc.docx").id(),
                "Complétée du numéro de registre du commerce");

        Map<String, Object> ancienne = ligne(avantRc);
        assertThat(ancienne.get("is_current")).isEqualTo(false);
        assertThat(ancienne.get("replaced_at"))
                .as("quand l'annonce a-t-elle été remplacée ? doit avoir une réponse")
                .isNotNull();
        assertThat(ancienne.get("motif"))
                .as("le motif est porté par la version REMPLACÉE : il dit pourquoi "
                        + "elle ne l'est plus")
                .isEqualTo("Complétée du numéro de registre du commerce");
    }

    @Test
    @DisplayName("Les deux versions restent dans le lignage — rien n'est perdu")
    void lesDeuxVersionsRestentRelisibles() {
        UUID avantRc = valider(genererBrouillon("annonce-sans-rc.docx").id(), null).id();
        DocumentSummary apresRc = valider(genererBrouillon("annonce-avec-rc.docx").id(),
                "Complétée du numéro de registre du commerce");

        assertThat(juridique.listVersions(apresRc.id()))
                .extracting(DocumentSummary::id, DocumentSummary::version)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(apresRc.id(), (short) 2),
                        org.assertj.core.groups.Tuple.tuple(avantRc, (short) 1));
    }

    @Test
    @DisplayName("Le dossier affiche l'annonce UNE fois — pas deux lignes côte à côte")
    void leDossierNAfficheQuUneAnnonce() {
        valider(genererBrouillon("annonce-sans-rc.docx").id(), null);
        DocumentSummary apresRc = valider(genererBrouillon("annonce-avec-rc.docx").id(),
                "Complétée du numéro de registre du commerce");

        DossierTicket dossier = juridique.view(dossierId).dossiersParTicket().stream()
                .filter(t -> ticketId.equals(t.ticketId()))
                .findFirst().orElseThrow();

        List<UUID> affiches = dossier.groupes().stream()
                .flatMap(g -> g.documents().stream())
                .map(DocumentSummary::id)
                .toList();

        assertThat(affiches)
                .as("c'est ce que l'employé et le client voient : une annonce, la bonne")
                .containsExactly(apresRc.id());
        assertThat(dossier.totalDocuments()).isEqualTo(1);
    }

    // =================================================================
    //  Le garde-fou : c'est la BASE qui refuse la version parallèle
    // =================================================================

    @Test
    @DisplayName("La base REFUSE une seconde annonce en vigueur sur le même emplacement")
    void laBaseRefuseLaVersionParallele() {
        DocumentSummary annonce = valider(genererBrouillon("annonce-sans-rc.docx").id(), null);

        // Une régression du code — un chemin de dépôt qui oublierait de replier
        // l'occupant — produirait exactement cet INSERT. L'index unique partiel
        // de V23 doit le refuser, et non le laisser passer en silence.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO dataroom_documents(id, workspace_id, dossier_id, ticket_id,
                        document_type, title, version, is_current, object_key, filename,
                        size_bytes, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 1, TRUE, 'ws/doublon.docx', 'doublon.docx', 10, NOW())
                """, UUID.randomUUID(), workspaceId, dossierId, ticketId, TYPE, TITRE))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("ux_dataroom_documents_courant_par_slot");

        assertThat(enVigueurSurLeSlot()).hasSize(1);
        assertThat(enVigueurSurLeSlot().get(0).get("id")).isEqualTo(annonce.id());
    }

    @Test
    @DisplayName("Un brouillon n'occupe jamais l'emplacement de l'acte en vigueur")
    void leBrouillonNOccupePasLeSlot() {
        DocumentSummary annonce = valider(genererBrouillon("annonce-sans-rc.docx").id(), null);

        // La seconde génération existe en base AVANT d'être validée. Si elle
        // était « en vigueur », l'INSERT se heurterait à l'index unique et la
        // régénération échouerait — l'employé ne pourrait plus produire la
        // version au RC.
        DocumentSummary brouillon = genererBrouillon("annonce-avec-rc.docx");

        assertThat(ligne(brouillon.id()).get("brouillon")).isEqualTo(true);
        assertThat(ligne(brouillon.id()).get("is_current")).isEqualTo(false);
        assertThat(enVigueurSurLeSlot())
                .as("tant que rien n'est validé, l'annonce du statut 2 reste la bonne")
                .hasSize(1);
        assertThat(enVigueurSurLeSlot().get(0).get("id")).isEqualTo(annonce.id());
    }

    @Test
    @DisplayName("Régénérer deux fois avant de valider REMPLACE le brouillon, ne l'empile pas")
    void regenererNEmpilePasLesBrouillons() {
        genererBrouillon("annonce-essai-1.docx");
        DocumentSummary dernier = genererBrouillon("annonce-essai-2.docx");

        assertThat(juridique.listBrouillons(ticketId))
                .as("un employé qui regénère trois fois ne doit pas avoir trois "
                        + "annonces à valider")
                .extracting(DocumentSummary::id)
                .containsExactly(dernier.id());

        valider(dernier.id(), null);
        assertThat(enVigueurSurLeSlot()).hasSize(1);
    }

    // =================================================================
    //  Ce que la seconde version emporte avec elle
    // =================================================================

    @Test
    @DisplayName("La visibilité décidée sur la première annonce vaut pour la seconde")
    void laVisibiliteSeTransmetALaVersionSuivante() {
        DocumentSummary avantRc = valider(genererBrouillon("annonce-sans-rc.docx").id(), null);

        // L'annonce n'est pas encore publiée au journal : l'employé la retire de
        // la vue du client en attendant.
        juridique.changerVisibilite(avantRc.id(), false, "DATAROOM", employeId);

        DocumentSummary apresRc = valider(genererBrouillon("annonce-avec-rc.docx").id(),
                "Complétée du numéro de registre du commerce");

        assertThat(ligne(apresRc.id()).get("visible_client"))
                .as("régénérer un acte n'est pas décider de le montrer : sans cela, "
                        + "la décision de l'employé serait annulée sans un mot")
                .isEqualTo(false);
        List<UUID> vusParLeClient = juridique.view(dossierId, null, null, null, true)
                .dossiersParTicket().stream()
                .flatMap(t -> t.groupes().stream())
                .flatMap(g -> g.documents().stream())
                .map(DocumentSummary::id)
                .toList();
        assertThat(vusParLeClient)
                .as("le client ne voit toujours pas l'annonce")
                .doesNotContain(apresRc.id(), avantRc.id());
    }

    @Test
    @DisplayName("L'annonce reste rattachée au ticket et au groupe qui la portent")
    void rattachementConserve() {
        valider(genererBrouillon("annonce-sans-rc.docx").id(), null);
        DocumentSummary apresRc = valider(genererBrouillon("annonce-avec-rc.docx").id(),
                "Complétée du numéro de registre du commerce");

        Map<String, Object> ligne = jdbc.queryForMap(
                "SELECT ticket_id, groupe, document_type, title FROM dataroom_documents WHERE id = ?",
                apresRc.id());

        assertThat(ligne.get("ticket_id")).isEqualTo(ticketId);
        assertThat(ligne.get("groupe"))
                .as("une annonce légale est un acte produit par le cabinet")
                .isEqualTo("ACTES_GENERES");
        assertThat(ligne.get("document_type")).isEqualTo(TYPE);
        assertThat(ligne.get("title"))
                .as("le titre EST l'emplacement : le changer scinderait le document "
                        + "logique en deux")
                .isEqualTo(TITRE);
    }
}
