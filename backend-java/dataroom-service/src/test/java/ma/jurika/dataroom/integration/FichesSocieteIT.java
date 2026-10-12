package ma.jurika.dataroom.integration;

import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.FicheClientDtos.FicheDossierView;
import ma.jurika.dataroom.api.dto.FicheClientDtos.Identifiant;
import ma.jurika.dataroom.api.dto.FicheClientDtos.Operation;
import ma.jurika.dataroom.application.FicheClientService;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L5 (E7) : fiche client et fiche dossier sur le vrai schema (migrations de auth,
 * ticket, dataroom et workflow), en role d'execution jurika_app (RLS) : lectures des
 * tickets, de la trace des identifiants (ticket V41) et des versions de TP (ticket V30) ;
 * conservation des fiches Word en versions successives dans la Data Room (dataroom V36).
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"})
@ActiveProfiles("it")
class FichesSocieteIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_fiches")
            .withUsername("jurika_it")
            .withPassword("jurika_it_pwd")
            .withInitScript("testcontainers-init.sql")
            .withReuse(false);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        SchemaJurikaDb.migrer(POSTGRES);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "jurika_app");
        registry.add("spring.datasource.password", () -> "jurika_app_it");
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("jurika.minio.endpoint", () -> "http://localhost:9099");
        registry.add("jurika.minio.access-key", () -> "test");
        registry.add("jurika.minio.secret-key", () -> "test-secret");
        registry.add("jurika.minio.bucket", () -> "jurika-it");
    }

    private static final UUID EMPLOYE = SchemaJurikaDb.EMPLOYE_SEME;
    private static int compteur = 0;

    /** Preparation en PROPRIETAIRE, hors RLS. */
    private final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    @Autowired private FicheClientService fiches;
    @MockBean private ObjectStorage storage;

    private UUID ws;
    private UUID dossier;

    @BeforeEach
    void seed() {
        ws = UUID.randomUUID();
        dossier = UUID.randomUUID();
        SchemaJurikaDb.workspace(jdbc, ws, "Cabinet Fiches " + ws, String.format("JUR-F%04d", ++compteur));
        jdbc.update("""
                INSERT INTO entreprise_dossiers (id, workspace_id, raison_sociale, forme_juridique, responsable_id,
                       capital_social_mad, adresse_siege, ville, ice, statut)
                VALUES (?, ?, 'PARACOSME', 'SARL', ?, 100000, '5 bd Zerktouni', 'Casablanca', '001234567000089', 'ACTIVE')
                """, dossier, ws, EMPLOYE);
        UUID creation = ticket("T-F1", "CREATION", "CLOTURE_DOSSIER", "2026-01-10T09:00:00Z");
        jdbc.update("UPDATE tickets SET cloture_at = ? WHERE id = ?",
                OffsetDateTime.parse("2026-03-05T09:00:00Z"), creation);
        jdbc.update("UPDATE entreprise_dossiers SET created_by_ticket_id = ? WHERE id = ?", creation, dossier);
        UUID annule = ticket("T-F2", "PV_AGO", "ANNULE", "2026-04-01T09:00:00Z");
        jdbc.update("UPDATE tickets SET annule_at = ? WHERE id = ?",
                OffsetDateTime.parse("2026-04-02T09:00:00Z"), annule);
        // Taxe professionnelle saisie par l'employe : trace SAISIE et premiere version, date inconnue.
        jdbc.execute((java.sql.Connection c) -> {
            c.setAutoCommit(false);
            try (java.sql.Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.identifiants_origine', 'SAISIE', true), "
                        + "set_config('app.identifiants_auteur', '" + EMPLOYE + "', true)");
                st.executeUpdate("UPDATE entreprise_dossiers SET taxe_professionnelle = 'TP-501' WHERE id = '" + dossier + "'");
                st.executeUpdate("INSERT INTO dossier_tp_versions (workspace_id, dossier_id, numero, date_effet, saisi_par) "
                        + "VALUES ('" + ws + "', '" + dossier + "', 'TP-501', NULL, '" + EMPLOYE + "')");
            }
            c.commit();
            c.setAutoCommit(true);
            return null;
        });
        TenantContext.set(ws);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(EMPLOYE, ws, "employe@cabinet.test", Role.EMPLOYE), null,
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYE"))));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private UUID ticket(String ref, String type, String statut, String creeLe) {
        UUID t = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tickets (id, workspace_id, reference, titre, type, statut, dossier_id, assigne_id,
                                     cree_par_id, created_at)
                VALUES (?, ?, ?, 'T', ?, ?, ?, ?, ?, ?)
                """, t, ws, ref + "-" + compteur, type, statut, dossier, EMPLOYE, EMPLOYE, OffsetDateTime.parse(creeLe));
        return t;
    }

    @Test
    void fiche_dossier_lue_sous_rls_provenance_versions_et_operations() {
        FicheDossierView v = fiches.ficheDossier(dossier, Role.EMPLOYE, EMPLOYE);
        Map<String, Identifiant> ids = v.fiche().identifiants().stream()
                .collect(Collectors.toMap(Identifiant::code, Function.identity()));

        assertThat(ids.get("DENOMINATION").provenance()).isEqualTo("Parcours de création");
        assertThat(ids.get("CAPITAL").valeur()).isEqualTo("100 000,00 dirhams");
        assertThat(ids.get("CAPITAL").origine()).as("trace d'ouverture du numerique 100000").isEqualTo("OUVERTURE");
        assertThat(ids.get("TP").origine()).isEqualTo("SAISIE");
        assertThat(ids.get("TP").provenance()).startsWith("Saisie manuelle par ");
        assertThat(ids.get("RC").valeur()).isNull();
        assertThat(v.fiche().versionsTp()).singleElement().satisfies(t -> {
            assertThat(t.numero()).isEqualTo("TP-501");
            assertThat(t.dateEffet()).isNull();
            assertThat(t.enVigueur()).isTrue();
        });
        assertThat(v.fiche().modifications()).isNotEmpty();
        assertThat(v.operations()).extracting(Operation::natureFin).containsExactly("CLOTURE", "ANNULATION");
        assertThat(v.derniereCloture()).isNotNull();
    }

    @Test
    void chaque_generation_du_responsable_est_conservee_en_nouvelle_version() throws Exception {
        FicheClientService.FicheWord w1 = fiches.genererFicheClient(dossier, Role.EMPLOYE, EMPLOYE);
        FicheClientService.FicheWord w2 = fiches.genererFicheClient(dossier, Role.EMPLOYE, EMPLOYE);
        fiches.genererFicheDossier(dossier, Role.EMPLOYE, EMPLOYE);

        assertThat(w1.conservee()).isTrue();
        assertThat(w1.version()).isEqualTo((short) 1);
        assertThat(w2.version()).isEqualTo((short) 2);
        List<Map<String, Object>> docs = jdbc.queryForList("""
                SELECT document_type, version, is_current, visible_client, groupe FROM dataroom_documents
                 WHERE dossier_id = ? ORDER BY document_type, version
                """, dossier);
        assertThat(docs).hasSize(3);
        assertThat(docs.get(0)).containsEntry("document_type", "FICHE_CLIENT").containsEntry("is_current", false);
        assertThat(docs.get(1)).containsEntry("document_type", "FICHE_CLIENT").containsEntry("is_current", true)
                .containsEntry("visible_client", false).containsEntry("groupe", "ACTES_GENERES");
        assertThat(docs.get(2)).containsEntry("document_type", "FICHE_DOSSIER");

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(w1.contenu()))) {
            String texte = doc.getParagraphs().stream().map(p -> p.getText()).collect(Collectors.joining("\n"))
                    + doc.getTables().stream().map(t -> t.getText()).collect(Collectors.joining("\n"));
            assertThat(texte).contains("PARACOSME", "TP-501", "5 bd Zerktouni, Casablanca").doesNotContain("$");
        }
    }

    @Test
    void employe_non_responsable_et_client_refuses() {
        UUID autre = UUID.randomUUID();
        SchemaJurikaDb.utilisateur(jdbc, autre, ws);
        assertThatThrownBy(() -> fiches.ficheClient(dossier, Role.EMPLOYE, autre))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> fiches.genererFicheClient(dossier, Role.CLIENT, EMPLOYE))
                .isInstanceOf(AccessDeniedException.class);
    }
}
