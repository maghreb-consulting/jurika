package ma.jurika.dataroom.integration;

import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.DocumentSummary;
import ma.jurika.dataroom.api.dto.DataroomDtos.DossierJuridiqueView;
import ma.jurika.dataroom.api.dto.DataroomDtos.DossierTicket;
import ma.jurika.dataroom.api.dto.DataroomDtos.GroupeDocuments;
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
 * Enonce, test 5 — un justificatif televerse au cochage d'une demarche doit se
 * retrouver DANS LE BON GROUPE DU BON TICKET, AVEC LE BON TYPE.
 *
 * <p>Les assertions portent sur des VALEURS lues en base et dans la vue rendue :
 * le type effectivement enregistre, le groupe de rangement, le ticket de
 * rattachement, le libelle calcule du dossier. Aucune ne se contente de
 * constater l'absence d'erreur.
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
class JustificatifCochageIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_cochage")
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
    private UUID ticketCreation;
    private UUID ticketModification;
    private UUID uploaderId;

    @BeforeEach
    void seed() {
        jdbc.execute("SELECT set_config('app.current_workspace_id', '', false)");
        jdbc.execute("ALTER TABLE dataroom_documents DISABLE ROW LEVEL SECURITY");
        jdbc.execute("DELETE FROM dataroom_documents");
        jdbc.execute("DELETE FROM tickets");
        jdbc.execute("DELETE FROM entreprise_dossiers");
        jdbc.execute("DELETE FROM workspaces");
        jdbc.execute("ALTER TABLE dataroom_documents ENABLE ROW LEVEL SECURITY");
        jdbc.execute("ALTER TABLE dataroom_documents "
                + "DROP CONSTRAINT IF EXISTS dataroom_documents_uploaded_by_fkey");

        workspaceId = UUID.randomUUID();
        dossierId = UUID.randomUUID();
        ticketCreation = UUID.randomUUID();
        ticketModification = UUID.randomUUID();
        uploaderId = UUID.randomUUID();

        SchemaJurikaDb.workspace(jdbc, workspaceId, "Cabinet Cochage", "JUR-C0001");
        jdbc.update("INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale, forme_juridique, responsable_id) VALUES (?, ?, ?, 'SARL', '33333333-3333-3333-3333-333333333333')",
                dossierId, workspaceId, "ATLAS TRADING");

        // Deux tickets sur le meme dossier : le justificatif doit atterrir dans
        // le bon, pas simplement « dans le dossier ».
        insertTicket(ticketCreation, "T-2026-00841", "CREATION", "DEROULEMENT_DEMARCHE",
                OffsetDateTime.parse("2026-06-15T09:00:00Z"));
        insertTicket(ticketModification, "T-2026-00902", "MODIFICATION", "GENERATION_DOCUMENTS",
                OffsetDateTime.parse("2026-09-04T09:00:00Z"));

        TenantContext.set(workspaceId);
        jdbc.execute("SELECT set_config('app.current_workspace_id', '" + workspaceId + "', false)");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void insertTicket(UUID id, String reference, String type, String statut,
                               OffsetDateTime creeLe) {
        jdbc.update("""
                INSERT INTO tickets(id, workspace_id, reference, titre, type, statut,
                                    dossier_id, created_at, cree_par_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, '33333333-3333-3333-3333-333333333333')
                """, id, workspaceId, reference, "Dossier " + reference, type, statut,
                dossierId, creeLe);
    }

    private MockMultipartFile pdf(String filename) {
        return new MockMultipartFile("file", filename, "application/pdf",
                ("contenu de " + filename).getBytes());
    }

    /** Depose un justificatif comme le fait le cochage : type du referentiel + ticket. */
    private DocumentSummary deposer(String documentType, String titre, UUID ticketId) {
        return juridique.uploadVersion(dossierId, documentType, titre, ticketId,
                pdf(documentType.toLowerCase() + ".pdf"), uploaderId, false);
    }

    private Map<String, Object> ligneEnBase(UUID documentId) {
        return jdbc.queryForMap(
                "SELECT document_type, groupe, ticket_id, dossier_id, is_current, title "
                        + "FROM dataroom_documents WHERE id = ?", documentId);
    }

    // =================================================================

    @Test
    @DisplayName("Le justificatif porte le type du referentiel et le groupe qui en decoule")
    void typeEtGroupeEnregistres() {
        DocumentSummary modeleJ = deposer("RC", "21. Immatriculation au RC", ticketCreation);

        Map<String, Object> ligne = ligneEnBase(modeleJ.id());

        assertThat(ligne.get("document_type"))
                .as("le type vient du referentiel, jamais de la saisie")
                .isEqualTo("RC");
        assertThat(ligne.get("groupe"))
                .as("un document delivre par une administration est un justificatif administratif")
                .isEqualTo("JUSTIFICATIFS_ADMINISTRATIFS");
        assertThat(ligne.get("ticket_id")).isEqualTo(ticketCreation);
        assertThat(ligne.get("dossier_id")).isEqualTo(dossierId);
        assertThat(ligne.get("title")).isEqualTo("21. Immatriculation au RC");
    }

    @Test
    @DisplayName("Chaque nature de document tombe dans son groupe")
    void chaqueNatureDansSonGroupe() {
        UUID statuts = deposer("STATUTS", "8. Statuts", ticketCreation).id();
        UUID cin = deposer("PIECE_IDENTITE", "2. KYC", ticketCreation).id();
        UUID attestation =
                deposer("ATTESTATION_ENREGISTREMENT", "17. Enregistrement", ticketCreation).id();

        assertThat(ligneEnBase(statuts).get("groupe")).isEqualTo("ACTES_GENERES");
        assertThat(ligneEnBase(cin).get("groupe")).isEqualTo("PIECES_CLIENT");
        assertThat(ligneEnBase(attestation).get("groupe")).isEqualTo("JUSTIFICATIFS_ADMINISTRATIFS");
    }

    @Test
    @DisplayName("Le justificatif se retrouve dans le BON ticket, sous le BON groupe")
    void dansLeBonGroupeDuBonTicket() {
        DocumentSummary modeleJ = deposer("RC", "21. Immatriculation au RC", ticketCreation);
        // Bruit : un acte sur l'AUTRE ticket du meme dossier.
        deposer("PV_MODIFICATION", "PV de modification", ticketModification);

        DossierJuridiqueView vue = juridique.view(dossierId);

        DossierTicket dossierCreation = vue.dossiersParTicket().stream()
                .filter(t -> ticketCreation.equals(t.ticketId()))
                .findFirst().orElseThrow();

        assertThat(dossierCreation.libelle())
                .as("le libelle est calcule, jamais saisi")
                .isEqualTo("Création — T-2026-00841 — 15/06/2026");
        assertThat(dossierCreation.totalDocuments()).isEqualTo(1);

        GroupeDocuments justificatifs = dossierCreation.groupes().stream()
                .filter(g -> "JUSTIFICATIFS_ADMINISTRATIFS".equals(g.code()))
                .findFirst().orElseThrow();
        assertThat(justificatifs.libelle()).isEqualTo("Justificatifs administratifs");
        assertThat(justificatifs.documents())
                .extracting(DocumentSummary::id, DocumentSummary::documentType)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(modeleJ.id(), "RC"));

        // Le document de l'autre ticket ne doit PAS s'y trouver.
        assertThat(dossierCreation.groupes())
                .flatExtracting(GroupeDocuments::documents)
                .extracting(DocumentSummary::documentType)
                .doesNotContain("PV_MODIFICATION");

        DossierTicket dossierModif = vue.dossiersParTicket().stream()
                .filter(t -> ticketModification.equals(t.ticketId()))
                .findFirst().orElseThrow();
        assertThat(dossierModif.libelle())
                .isEqualTo("Modification — T-2026-00902 — 04/09/2026");
        assertThat(dossierModif.groupes())
                .flatExtracting(GroupeDocuments::documents)
                .extracting(DocumentSummary::documentType)
                .containsExactly("PV_MODIFICATION");
    }

    @Test
    @DisplayName("Un document sans ticket reste accessible sous « Hors ticket »")
    void documentHorsTicketResteVisible() {
        DocumentSummary orphelin = deposer("AUTRE", "Piece deposee hors workflow", null);

        DossierJuridiqueView vue = juridique.view(dossierId);

        DossierTicket horsTicket = vue.dossiersParTicket().stream()
                .filter(t -> t.ticketId() == null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("le regroupement « Hors ticket » a disparu"));

        assertThat(horsTicket.libelle()).isEqualTo("Hors ticket");
        assertThat(horsTicket.groupes())
                .flatExtracting(GroupeDocuments::documents)
                .extracting(DocumentSummary::id)
                .contains(orphelin.id());
    }

    @Test
    @DisplayName("Un type non deductible s'affiche sous « Pieces client » SANS etre ecrit en base")
    void typeNonDeductibleAfficheSansEcriture() {
        DocumentSummary autre = deposer("AUTRE", "Piece diverse", ticketCreation);

        // En base : le groupe reste NULL — on ne fabrique pas une donnee absente.
        assertThat(ligneEnBase(autre.id()).get("groupe"))
                .as("la nature n'etant pas deductible, rien n'est ecrit")
                .isNull();

        // A l'ecran : le document apparait quand meme, sous « Pieces client ».
        DossierTicket dossier = juridique.view(dossierId).dossiersParTicket().stream()
                .filter(t -> ticketCreation.equals(t.ticketId()))
                .findFirst().orElseThrow();
        GroupeDocuments pieces = dossier.groupes().stream()
                .filter(g -> "PIECES_CLIENT".equals(g.code()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("un document invisible est un document perdu"));
        assertThat(pieces.documents())
                .extracting(DocumentSummary::id)
                .contains(autre.id());
    }

    @Test
    @DisplayName("Une version remplacee reste visible dans le ticket qui l'a produite")
    void versionHistoriqueResteVisible() {
        DocumentSummary v1 = juridique.uploadVersion(dossierId, "STATUTS", "Statuts",
                ticketCreation, pdf("statuts-v1.pdf"), uploaderId, true);
        DocumentSummary v2 = juridique.uploadVersion(dossierId, "STATUTS", "Statuts",
                ticketCreation, pdf("statuts-v2.pdf"), uploaderId, true);

        assertThat(ligneEnBase(v1.id()).get("is_current")).isEqualTo(false);
        assertThat(ligneEnBase(v2.id()).get("is_current")).isEqualTo(true);

        DossierTicket dossier = juridique.view(dossierId).dossiersParTicket().stream()
                .filter(t -> ticketCreation.equals(t.ticketId()))
                .findFirst().orElseThrow();

        List<UUID> ids = dossier.groupes().stream()
                .flatMap(g -> g.documents().stream())
                .map(DocumentSummary::id)
                .toList();

        // Lot 2 (2026-09-07) — LA VERSION REMPLACEE N'A PLUS SA PROPRE LIGNE
        // QUAND C'EST LE MEME TICKET QUI L'A REMPLACEE.
        //
        // Elle en avait une avant, et le dossier annoncait « 2 documents » pour
        // un seul acte : la meme piece figurait en ligne de premier niveau ET
        // sous « Anciennes versions » de la ligne courante, avec les memes
        // actions et rien qui les distingue.
        //
        // Le principe « un ticket clos EST l'archive » n'est pas abandonne : il
        // est servi par le lignage, verifie ci-dessous. Un acte remplace lors
        // d'une operation ULTERIEURE, lui, garde bien sa ligne dans le ticket
        // qui l'a produit — c'est le cas que couvre `versionRemplaceeAilleurs`.
        assertThat(ids)
                .as("le ticket montre l'acte une fois, dans sa version en vigueur")
                .containsExactly(v2.id());
        assertThat(dossier.totalDocuments()).isEqualTo(1);

        assertThat(juridique.listVersions(v2.id()))
                .as("rien n'est perdu : la version remplacee reste dans le lignage")
                .extracting(DocumentSummary::id)
                .contains(v1.id(), v2.id());
    }

    @Test
    @DisplayName("Un acte remplace par une operation ULTERIEURE reste dans le ticket qui l'a produit")
    void versionRemplaceeAilleurs() {
        // C'est le cas qui porte reellement le principe « un ticket clos EST
        // l'archive ». Les statuts d'origine ont ete produits a la creation ;
        // une modification, des mois plus tard, en publie une version refondue.
        // Le juriste qui rouvre le dossier de la CREATION doit y retrouver les
        // statuts tels qu'ils etaient alors — c'est ce qu'il vient y chercher.
        DocumentSummary v1 = juridique.uploadVersion(dossierId, "STATUTS", "Statuts",
                ticketCreation, pdf("statuts-origine.pdf"), uploaderId, true);
        DocumentSummary v2 = juridique.uploadVersion(dossierId, "STATUTS", "Statuts",
                ticketModification, pdf("statuts-refondus.pdf"), uploaderId, true);

        List<DossierTicket> dossiers = juridique.view(dossierId).dossiersParTicket();

        List<UUID> creation = dossiers.stream()
                .filter(t -> ticketCreation.equals(t.ticketId()))
                .findFirst().orElseThrow()
                .groupes().stream().flatMap(g -> g.documents().stream())
                .map(DocumentSummary::id).toList();
        assertThat(creation)
                .as("la version d'origine reste dans le ticket qui l'a produite")
                .containsExactly(v1.id());

        List<UUID> modification = dossiers.stream()
                .filter(t -> ticketModification.equals(t.ticketId()))
                .findFirst().orElseThrow()
                .groupes().stream().flatMap(g -> g.documents().stream())
                .map(DocumentSummary::id).toList();
        assertThat(modification)
                .as("la version refondue appartient au ticket qui l'a produite")
                .containsExactly(v2.id());
    }
}
