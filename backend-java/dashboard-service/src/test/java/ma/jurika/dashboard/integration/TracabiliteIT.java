package ma.jurika.dashboard.integration;

import ma.jurika.common.security.TenantContext;
import ma.jurika.dashboard.api.dto.TracabiliteDtos.TracabilitePage;
import ma.jurika.dashboard.application.TracabiliteQueryService;
import ma.jurika.dashboard.application.TracabiliteQueryService.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2 — IT de la traçabilité : prouve le scoping workspace (aucune fuite
 * cross-workspace), le filtre par entite, et la presence des actions ecrites.
 *
 * <p>Test "leger" : on instancie directement {@link TracabiliteQueryService}
 * sur un Postgres testcontainer, sans contexte Spring (le dashboard-service
 * exige Redis pour son contexte complet, inutile ici). Le scoping repose sur
 * le filtre SQL explicite {@code WHERE workspace_id = ?} ; la RLS (defense en
 * profondeur, active en prod via RlsAspect) n'est pas requise pour la preuve
 * d'isolation testee ici.
 */
@Testcontainers
class TracabiliteIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_tracabilite")
            .withUsername("jurika_it")
            .withPassword("jurika_it_pwd")
            .withInitScript("testcontainers-init.sql")
            .withReuse(false);

    private JdbcTemplate jdbc;
    private TracabiliteQueryService service;

    private UUID ws1;
    private UUID ws2;
    private UUID user1;
    private UUID ticket1;
    private UUID dossier1;
    private UUID ticket2;

    @BeforeEach
    void seed() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(ds);
        service = new TracabiliteQueryService(jdbc);

        jdbc.execute("DELETE FROM audit_log");
        jdbc.execute("DELETE FROM users");
        ws1 = UUID.randomUUID();
        ws2 = UUID.randomUUID();
        user1 = UUID.randomUUID();
        ticket1 = UUID.randomUUID();
        dossier1 = UUID.randomUUID();
        ticket2 = UUID.randomUUID();

        insert(ws1, user1, "TICKET_CREATED", "ticket", ticket1);
        insert(ws1, user1, "TICKET_ASSIGNED", "ticket", ticket1);
        insert(ws1, user1, "DOSSIER_TRANSFERE", "dossier", dossier1);
        // Workspace voisin : ne doit JAMAIS apparaitre dans les resultats de ws1.
        insert(ws2, UUID.randomUUID(), "TICKET_CREATED", "ticket", ticket2);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private void insert(UUID ws, UUID user, String action, String entityType, UUID entityId) {
        jdbc.update("""
                INSERT INTO audit_log(workspace_id,user_id,action,entity_type,entity_id,source_service,created_at)
                VALUES (?,?,?,?,?,?, NOW())
                """, ws, user, action, entityType, entityId, "ticket-service");
    }

    private void seedWorkspace(UUID ws) {
        jdbc.update("INSERT INTO workspaces(id,name,code_workspace) VALUES (?,?,?) ON CONFLICT DO NOTHING",
                ws, "WS " + ws, "JUR-" + ws.toString().substring(0, 5));
    }

    private void seedUser(UUID ws, UUID id, String role, String status) {
        jdbc.update("""
                INSERT INTO users(id,workspace_id,email,first_name,last_name,role,status)
                VALUES (?,?,?,?,?,?,?)
                """, id, ws, id + "@jurika.ma", "Prenom", "Nom", role, status);
    }

    @Test
    @DisplayName("Scoping workspace : seules les lignes du workspace courant + libelles FR, pas de fuite")
    void searchScopedToWorkspaceNoLeak() {
        TenantContext.set(ws1);
        TracabilitePage page = service.search(new Filter(null, null, null, null, null, null, null, 50, 0));

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.items()).extracting("action")
                .containsExactlyInAnyOrder("TICKET_CREATED", "TICKET_ASSIGNED", "DOSSIER_TRANSFERE");
        // Aucune entite du workspace voisin (ws2) ne fuite.
        assertThat(page.items()).noneMatch(e -> ticket2.equals(e.entityId()));
        // Libelles FR mappes.
        assertThat(page.items()).anyMatch(e -> "Ticket cree".equals(e.actionLabel()));
        assertThat(page.items()).anyMatch(e -> "Dossier transfere".equals(e.actionLabel()));
    }

    @Test
    @DisplayName("Filtre par entite : seul l'historique du ticket cible (action presente)")
    void filterByEntity() {
        TenantContext.set(ws1);
        TracabilitePage page = service.search(
                new Filter("ticket", ticket1, null, null, null, null, null, 50, 0));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).allMatch(e -> ticket1.equals(e.entityId()));
        assertThat(page.items()).extracting("action")
                .containsExactlyInAnyOrder("TICKET_CREATED", "TICKET_ASSIGNED");
    }

    @Test
    @DisplayName("Un autre workspace ne voit que ses propres lignes (isolation symetrique)")
    void otherWorkspaceSeesOnlyItsOwn() {
        TenantContext.set(ws2);
        TracabilitePage page = service.search(new Filter(null, null, null, null, null, null, null, 50, 0));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).allMatch(e -> ticket2.equals(e.entityId()));
    }

    @Test
    @DisplayName("Filtre acteur ACTIF : n'affiche que les events d'acteurs encore actifs")
    void filterActorActive() {
        seedWorkspace(ws1);
        UUID actif = UUID.randomUUID();
        UUID retire = UUID.randomUUID();   // desactive (INACTIVE)
        UUID purge = UUID.randomUUID();    // aucune ligne users -> compte supprime
        seedUser(ws1, actif, "EMPLOYE", "ACTIVE");
        seedUser(ws1, retire, "CLIENT", "INACTIVE");
        jdbc.execute("DELETE FROM audit_log");
        insert(ws1, actif, "TICKET_CREATED", "ticket", ticket1);
        insert(ws1, retire, "DOCUMENT_UPLOADED", "document", dossier1);
        insert(ws1, purge, "TICKET_CREATED", "ticket", ticket1);
        insert(ws1, null, "TICKET_CREATED", "ticket", ticket1); // event systeme

        TenantContext.set(ws1);
        TracabilitePage page = service.search(
                new Filter(null, null, null, null, null, null, "ACTIVE", 50, 0));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).allMatch(e -> actif.equals(e.userId()));
    }

    @Test
    @DisplayName("Filtre acteur RETIRE : desactives + comptes purges, hors events systeme")
    void filterActorRetired() {
        seedWorkspace(ws1);
        UUID actif = UUID.randomUUID();
        UUID retire = UUID.randomUUID();
        UUID purge = UUID.randomUUID();
        seedUser(ws1, actif, "EMPLOYE", "ACTIVE");
        seedUser(ws1, retire, "CLIENT", "INACTIVE");
        jdbc.execute("DELETE FROM audit_log");
        insert(ws1, actif, "TICKET_CREATED", "ticket", ticket1);
        insert(ws1, retire, "DOCUMENT_UPLOADED", "document", dossier1);
        insert(ws1, purge, "TICKET_CREATED", "ticket", ticket1);
        insert(ws1, null, "TICKET_CREATED", "ticket", ticket1); // event systeme, exclu

        TenantContext.set(ws1);
        TracabilitePage page = service.search(
                new Filter(null, null, null, null, null, null, "RETIRED", 50, 0));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting("userId")
                .containsExactlyInAnyOrder(retire, purge);
    }
}
