package ma.jurika.dataroom.integration;

import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.DocumentSummary;
import ma.jurika.dataroom.api.dto.DataroomDtos.DossierJuridiqueView;
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

/**
 * Lot B, énoncé 11 — <b>LA VISIBILITÉ CLIENT SE RÉPERCUTE DES DEUX CÔTÉS.</b>
 *
 * <p>« La visibilité modifiée depuis la Data Room se répercute, et
 * réciproquement. » Ce test l'éprouve sur une base réelle, migrations comprises :
 * ce sont {@code V31} et {@code V32} du lot B qui créent la colonne et son
 * journal, et c'est leur exécution par Flyway que ce test suppose.
 *
 * <h2>Pourquoi il faut une base, et pourquoi un test unitaire ne suffit pas</h2>
 *
 * <p>La répercussion n'est pas une synchronisation : <b>il n'y a qu'une seule
 * colonne</b>, {@code dataroom_documents.visible_client}, et les deux points
 * d'entrée — le panneau de cochage du workflow, la Data Room — y écrivent. C'est
 * précisément ce qui doit être vérifié en base : qu'aucun des deux ne passe par
 * une copie, une projection, ou un cache qui divergerait.
 *
 * <p>Et le <b>ticket-service lit cette même colonne</b>, par une vue en lecture
 * seule ({@code DataroomDocumentViewEntity.visible_client}), pour le récapitulatif
 * de clôture. Un renommage de colonne casserait ce service-là sans qu'aucun test
 * du dataroom ne bronche : le dernier cas ci-dessous interroge donc le nom
 * exactement comme l'autre service le fait.
 *
 * <p>Défaut que ce test empêcherait de revenir : un filtre posé à l'affichage
 * plutôt qu'à la source. Un indicateur que seule l'interface respecterait ne
 * serait pas une visibilité, ce serait une convention — et le client verrait la
 * pièce en appelant l'API directement.
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
class VisibiliteClientIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_visibilite")
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

        SchemaJurikaDb.workspace(jdbc, workspaceId, "Cabinet Visibilite", "JUR-V0001");
        jdbc.update("INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale, forme_juridique, responsable_id) VALUES (?, ?, ?, 'SARL', '33333333-3333-3333-3333-333333333333')",
                dossierId, workspaceId, "PARACOSME");
        jdbc.update("""
                INSERT INTO tickets(id, workspace_id, reference, titre, type, statut,
                                    dossier_id, created_at, cree_par_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, '33333333-3333-3333-3333-333333333333')
                """, ticketId, workspaceId, "T-2026-00841", "Creation SARL PARACOSME",
                "CREATION", "DEROULEMENT_DEMARCHE", dossierId,
                OffsetDateTime.parse("2026-09-01T09:00:00Z"));

        TenantContext.set(workspaceId);
        jdbc.execute("SELECT set_config('app.current_workspace_id', '" + workspaceId + "', false)");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // =================================================================
    //  Utilitaires
    // =================================================================

    private MockMultipartFile pdf(String nom) {
        return new MockMultipartFile("file", nom, "application/pdf", ("contenu " + nom).getBytes());
    }

    /** Dépose comme le fait le cochage : type issu du référentiel, rattaché au ticket. */
    private DocumentSummary deposer(String documentType, String titre) {
        return juridique.uploadVersion(dossierId, documentType, titre, ticketId,
                pdf(documentType.toLowerCase() + ".pdf"), employeId);
    }

    /** La valeur EN BASE, lue par le nom de colonne, sans passer par le service. */
    private boolean visibleEnBase(UUID documentId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT visible_client FROM dataroom_documents WHERE id = ?",
                Boolean.class, documentId));
    }

    private List<Map<String, Object>> journal(UUID documentId) {
        return jdbc.queryForList(
                "SELECT visible, origine, acteur_id FROM dataroom_visibilite_evenements "
                        + "WHERE document_id = ? ORDER BY survenu_le", documentId);
    }

    /** Les documents que le CLIENT voit dans le dossier du ticket. */
    private List<String> titresVusParLeClient() {
        return titresDansLeTicket(juridique.view(dossierId, null, null, null, true));
    }

    /** Les documents que l'EMPLOYÉ voit — il doit tout voir, masqué compris. */
    private List<String> titresVusParLEmploye() {
        return titresDansLeTicket(juridique.view(dossierId, null, null, null, false));
    }

    private List<String> titresDansLeTicket(DossierJuridiqueView vue) {
        return vue.dossiersParTicket().stream()
                .filter(t -> ticketId.equals(t.ticketId()))
                .findFirst()
                .map(DossierTicket::groupes).orElse(List.of())
                .stream()
                .flatMap(g -> g.documents().stream())
                .map(DocumentSummary::title)
                .sorted()
                .toList();
    }

    // =================================================================
    //  La valeur par défaut
    // =================================================================

    @Test
    @DisplayName("Un justificatif du parcours est VISIBLE par défaut ; un document AUTRE est MASQUÉ")
    void valeurParDefautSelonLeType() {
        // « Ce que le référentiel sait nommer appartient au dossier du client ;
        //   ce qu'il ne sait pas nommer attend une décision. »
        DocumentSummary recepisse = deposer("RECEPISSE_DEPOT", "19. Récépissé de dépôt");
        DocumentSummary note = deposer("AUTRE", "Note interne — relance du greffe");

        assertThat(visibleEnBase(recepisse.id()))
                .as("un récépissé de dépôt appartient au dossier du client")
                .isTrue();
        assertThat(visibleEnBase(note.id()))
                .as("AUTRE est le seul endroit où une note interne peut atterrir")
                .isFalse();

        // Et le résumé rendu par l'API porte la même valeur que la base.
        assertThat(recepisse.visibleClient()).isTrue();
        assertThat(note.visibleClient()).isFalse();
    }

    @Test
    @DisplayName("Le dépôt sans indication ne journalise rien ; c'est la valeur par défaut")
    void leDefautNEstPasUnGeste() {
        DocumentSummary recepisse = deposer("RECEPISSE_DEPOT", "19. Récépissé de dépôt");

        assertThat(journal(recepisse.id()))
                .as("le journal enregistre les DÉCISIONS, pas l'application d'une règle")
                .isEmpty();
    }

    // =================================================================
    //  La répercussion, dans les deux sens
    // =================================================================

    @Test
    @DisplayName("Masqué depuis la Data Room : le client cesse de le voir, l'employé continue")
    void masqueDepuisLaDataRoom() {
        DocumentSummary recepisse = deposer("RECEPISSE_DEPOT", "19. Récépissé de dépôt");
        DocumentSummary modeleJ = deposer("RC", "28. Modèle J");

        assertThat(titresVusParLeClient())
                .containsExactly("19. Récépissé de dépôt", "28. Modèle J");

        juridique.changerVisibilite(recepisse.id(), false, "DATAROOM", employeId);

        assertThat(visibleEnBase(recepisse.id()))
                .as("le filtre est posé à la SOURCE, pas à l'affichage")
                .isFalse();
        assertThat(titresVusParLeClient())
                .as("le client ne voit plus le récépissé")
                .containsExactly("28. Modèle J");
        assertThat(titresVusParLEmploye())
                .as("l'employé doit pouvoir constater qu'une pièce existe et décider de la montrer")
                .containsExactly("19. Récépissé de dépôt", "28. Modèle J");
        assertThat(modeleJ.visibleClient()).isTrue();
    }

    @Test
    @DisplayName("Montré depuis le WORKFLOW : le client le voit, sur la même colonne")
    void montreDepuisLeWorkflow() {
        // Une note interne, masquée par défaut. L'employé décide de la montrer
        // depuis le panneau de cochage — l'autre point d'entrée.
        DocumentSummary note = deposer("AUTRE", "Courrier du greffe");
        assertThat(titresVusParLeClient()).isEmpty();

        juridique.changerVisibilite(note.id(), true, "WORKFLOW", employeId);

        assertThat(visibleEnBase(note.id())).isTrue();
        assertThat(titresVusParLeClient()).containsExactly("Courrier du greffe");
    }

    @Test
    @DisplayName("Les deux points d'entrée écrivent LA MÊME LIGNE — aucune copie à synchroniser")
    void unSeulEtatPourLesDeuxCotes() {
        DocumentSummary recepisse = deposer("RECEPISSE_DEPOT", "19. Récépissé de dépôt");

        // Data Room → masqué, puis workflow → visible, puis Data Room → masqué.
        juridique.changerVisibilite(recepisse.id(), false, "DATAROOM", employeId);
        assertThat(visibleEnBase(recepisse.id())).isFalse();

        juridique.changerVisibilite(recepisse.id(), true, "WORKFLOW", employeId);
        assertThat(visibleEnBase(recepisse.id())).isTrue();
        assertThat(titresVusParLeClient()).containsExactly("19. Récépissé de dépôt");

        juridique.changerVisibilite(recepisse.id(), false, "DATAROOM", employeId);
        assertThat(visibleEnBase(recepisse.id())).isFalse();
        assertThat(titresVusParLeClient()).isEmpty();

        // Une seule ligne en base, donc un seul état : rien à réconcilier.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM dataroom_documents WHERE id = ?", Long.class,
                recepisse.id()))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("Chaque changement est journalisé, avec son sens, son origine et son auteur")
    void chaqueChangementEstJournalise() {
        DocumentSummary recepisse = deposer("RECEPISSE_DEPOT", "19. Récépissé de dépôt");

        juridique.changerVisibilite(recepisse.id(), false, "DATAROOM", employeId);
        juridique.changerVisibilite(recepisse.id(), true, "WORKFLOW", employeId);

        List<Map<String, Object>> evenements = journal(recepisse.id());

        assertThat(evenements)
                .as("« qui a masqué ce récépissé, et quand ? » doit avoir une réponse")
                .hasSize(2);
        assertThat(evenements.get(0)).containsEntry("visible", false)
                .containsEntry("origine", "DATAROOM").containsEntry("acteur_id", employeId);
        assertThat(evenements.get(1)).containsEntry("visible", true)
                .containsEntry("origine", "WORKFLOW").containsEntry("acteur_id", employeId);
    }

    // =================================================================
    //  Les accès unitaires — aucune liste ne filtre là
    // =================================================================

    @Test
    @DisplayName("Un document masqué n'est pas consultable à l'unité par le client")
    void accesUnitaireRefuseAuClient() {
        DocumentSummary recepisse = deposer("RECEPISSE_DEPOT", "19. Récépissé de dépôt");
        juridique.changerVisibilite(recepisse.id(), false, "DATAROOM", employeId);

        // C'est le chemin de l'aperçu et du téléchargement : aucune liste n'y
        // filtre, la garde doit donc être posée document par document. Sans elle,
        // un client qui connaît l'identifiant lit la pièce.
        assertThat(juridique.estVisiblePour(recepisse.id(), true)).isFalse();
        assertThat(juridique.estVisiblePour(recepisse.id(), false))
                .as("l'employé, lui, y accède")
                .isTrue();
    }

    // =================================================================
    //  Le versionnement
    // =================================================================

    @Test
    @DisplayName("Une nouvelle version HÉRITE de la visibilité : remplacer n'est pas montrer")
    void laNouvelleVersionHerite() {
        DocumentSummary recepisse = deposer("RECEPISSE_DEPOT", "19. Récépissé de dépôt");
        juridique.changerVisibilite(recepisse.id(), false, "DATAROOM", employeId);

        DocumentSummary v2 = juridique.replaceAsNewVersion(recepisse.id(),
                pdf("recepisse-v2.pdf"), "Scan illisible, redépôt", employeId);

        assertThat(v2.version()).isEqualTo((short) 2);
        assertThat(visibleEnBase(v2.id()))
                .as("remplacer un fichier n'est pas décider de le montrer")
                .isFalse();
        assertThat(titresVusParLeClient()).isEmpty();
    }

    // =================================================================
    //  Le contrat inter-services
    // =================================================================

    @Test
    @DisplayName("La colonne porte le nom que le ticket-service lit pour le récapitulatif")
    void contratAvecLeTicketService() {
        DocumentSummary note = deposer("AUTRE", "Note interne");
        DocumentSummary recepisse = deposer("RECEPISSE_DEPOT", "19. Récépissé de dépôt");

        // Le ticket-service lit `dataroom_documents` par une vue en LECTURE SEULE
        // (`DataroomDocumentViewEntity`) pour bâtir le récapitulatif de clôture :
        // « remettre un dossier dont des pièces restent masquées est une décision ».
        // On interroge ici exactement comme lui — par le nom de colonne, sur les
        // documents EN VIGUEUR du ticket. Un renommage casserait ce service-là
        // sans qu'aucun test du dataroom ne bronche.
        List<Map<String, Object>> vueTicket = jdbc.queryForList("""
                SELECT id, document_type, visible_client
                  FROM dataroom_documents
                 WHERE workspace_id = ? AND ticket_id = ? AND is_current
                 ORDER BY document_type
                """, workspaceId, ticketId);

        assertThat(vueTicket).hasSize(2);
        assertThat(vueTicket.get(0)).containsEntry("document_type", "AUTRE")
                .containsEntry("visible_client", false);
        assertThat(vueTicket.get(1)).containsEntry("document_type", "RECEPISSE_DEPOT")
                .containsEntry("visible_client", true);
        assertThat(vueTicket.get(0).get("id")).isEqualTo(note.id());
        assertThat(vueTicket.get(1).get("id")).isEqualTo(recepisse.id());
    }

    @Test
    @DisplayName("Les documents déjà déposés avant la migration restent visibles")
    void aucuneRegressionSilencieuseSurLExistant() {
        // La colonne est posée avec DEFAULT TRUE, et V32 ne passe à FALSE que les
        // documents de type AUTRE. Poser FALSE partout aurait fait disparaître de
        // l'espace client, à la minute de la migration, tout ce qui y était.
        UUID ancien = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO dataroom_documents(id, workspace_id, dossier_id, ticket_id,
                        document_type, title, version, is_current, object_key, filename,
                        size_bytes, created_at)
                VALUES (?, ?, ?, ?, 'STATUTS', 'Statuts déposés avant le lot B', 1, TRUE,
                        'ws/legacy/statuts.pdf', 'statuts.pdf', 1024, NOW())
                """, ancien, workspaceId, dossierId, ticketId);

        assertThat(visibleEnBase(ancien))
                .as("le DEFAULT de la colonne s'applique à une ligne qui ne la mentionne pas")
                .isTrue();
        assertThat(titresVusParLeClient()).contains("Statuts déposés avant le lot B");
    }
}
