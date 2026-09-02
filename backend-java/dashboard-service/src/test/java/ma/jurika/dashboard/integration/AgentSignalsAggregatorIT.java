package ma.jurika.dashboard.integration;

import ma.jurika.dashboard.api.dto.AgentDtos.AgentSignals;
import ma.jurika.dashboard.api.dto.AgentDtos.EcheanceSignal;
import ma.jurika.dashboard.application.aggregator.AgentSignalsAggregator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot IA-1 — IT du {@link AgentSignalsAggregator} : verifie que les 3 signaux
 * sont correctement agreges ET scopes sur les dossiers de l'employe
 * ({@code entreprise_dossiers.responsable_id}). Les items d'un dossier
 * appartenant a un AUTRE employe ne doivent jamais apparaitre.
 *
 * <p>Test « slice » volontairement SANS {@code @SpringBootTest} : on construit
 * directement un {@link JdbcTemplate} sur le container Postgres et on instancie
 * l'aggregator. Cela evite de charger le contexte Spring complet (cache Redis
 * du service), qui n'est pas monte en IT local, et teste exactement le SQL.
 */
@Testcontainers
class AgentSignalsAggregatorIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_agent")
            .withUsername("jurika_it")
            .withPassword("jurika_it_pwd")
            .withInitScript("testcontainers-init.sql")
            .withReuse(false);

    private static JdbcTemplate jdbc;
    private static AgentSignalsAggregator aggregator;

    private UUID ws;
    private UUID emp;
    private UUID otherEmp;
    private UUID clientUser;
    private UUID dossierA; // responsable = emp
    private UUID dossierB; // responsable = otherEmp

    @BeforeAll
    static void initDataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(ds);
        aggregator = new AgentSignalsAggregator(jdbc);
    }

    @BeforeEach
    void seed() {
        jdbc.execute("DELETE FROM dataroom_client_access_log");
        jdbc.execute("DELETE FROM dataroom_demandes_client");
        jdbc.execute("DELETE FROM deadlines");
        jdbc.execute("DELETE FROM dataroom_alertes_echeances");
        jdbc.execute("DELETE FROM tickets");
        jdbc.execute("DELETE FROM entreprise_dossiers");
        jdbc.execute("DELETE FROM users");
        jdbc.execute("DELETE FROM workspaces");

        ws = UUID.randomUUID();
        emp = UUID.randomUUID();
        otherEmp = UUID.randomUUID();
        clientUser = UUID.randomUUID();
        dossierA = UUID.randomUUID();
        dossierB = UUID.randomUUID();

        jdbc.update("INSERT INTO workspaces(id,name,code_workspace) VALUES (?,?,?)",
                ws, "Cabinet IA", "JUR-IA000");
        jdbc.update("INSERT INTO users(id,workspace_id,email,role) VALUES (?,?,?,?)",
                emp, ws, "emp@ia.ma", "EMPLOYE");
        jdbc.update("INSERT INTO users(id,workspace_id,email,role) VALUES (?,?,?,?)",
                otherEmp, ws, "other@ia.ma", "EMPLOYE");
        jdbc.update("INSERT INTO users(id,workspace_id,email,role) VALUES (?,?,?,?)",
                clientUser, ws, "client@ia.ma", "CLIENT");

        // dossierA -> emp (avec client) ; dossierB -> otherEmp
        jdbc.update("INSERT INTO entreprise_dossiers(id,workspace_id,raison_sociale,client_id,responsable_id) VALUES (?,?,?,?,?)",
                dossierA, ws, "SARL Alpha", clientUser, emp);
        jdbc.update("INSERT INTO entreprise_dossiers(id,workspace_id,raison_sociale,client_id,responsable_id) VALUES (?,?,?,?,?)",
                dossierB, ws, "SARL Beta", null, otherEmp);

        // Echeances : deadline depassee (dossierA) + alerte fiscale J+5 (dossierA)
        jdbc.update("""
                INSERT INTO deadlines(id,workspace_id,dossier_id,title,due_at,severity,statut)
                VALUES (?,?,?,?, NOW() - INTERVAL '1 day', 'CRITICAL', 'OUVERTE')
                """, UUID.randomUUID(), ws, dossierA, "Depot RC en retard");
        jdbc.update("""
                INSERT INTO dataroom_alertes_echeances(id,workspace_id,dossier_id,type_echeance,date_echeance,statut)
                VALUES (?,?,?,?, CURRENT_DATE + INTERVAL '5 day', 'PLANIFIEE')
                """, UUID.randomUUID(), ws, dossierA, "TVA_MENSUELLE");
        // Bruit : deadline sur dossierB (otherEmp) -> ne doit PAS remonter pour emp
        jdbc.update("""
                INSERT INTO deadlines(id,workspace_id,dossier_id,title,due_at,severity,statut)
                VALUES (?,?,?,?, NOW() - INTERVAL '1 day', 'CRITICAL', 'OUVERTE')
                """, UUID.randomUUID(), ws, dossierB, "Bruit B");

        // Reste-a-faire : 1 ticket NOUVEAU (dossierA) + 1 demande NON_TRAITEE (dossierA)
        jdbc.update("""
                INSERT INTO tickets(id,workspace_id,reference,titre,type,statut,priorite,dossier_id,assigne_id,created_at)
                VALUES (?,?,?,?,?,?,?,?,?, NOW() - INTERVAL '4 day')
                """, UUID.randomUUID(), ws, "T-001", "Modif statuts", "MODIFICATION", "NOUVEAU", "NORMALE", dossierA, emp);
        jdbc.update("""
                INSERT INTO tickets(id,workspace_id,reference,titre,type,statut,priorite,dossier_id,assigne_id,created_at)
                VALUES (?,?,?,?,?,?,?,?,?, NOW())
                """, UUID.randomUUID(), ws, "T-002", "Bruit B", "CREATION", "NOUVEAU", "NORMALE", dossierB, otherEmp);
        jdbc.update("""
                INSERT INTO dataroom_demandes_client(id,workspace_id,dossier_id,sujet,statut,created_at)
                VALUES (?,?,?,?, 'NON_TRAITEE', NOW() - INTERVAL '6 day')
                """, UUID.randomUUID(), ws, dossierA, "Question TVA");
    }

    @Test
    @DisplayName("Agrege et scope les 3 signaux sur les dossiers de l'employe")
    void aggregatesAndScopes() {
        AgentSignals s = aggregator.aggregate(ws, emp);

        // 2 echeances (deadline depassee + alerte J+5), le bruit de dossierB exclu
        assertThat(s.echeances()).hasSize(2);
        assertThat(s.counts().echeancesDepassees()).isEqualTo(1);
        assertThat(s.echeances()).allMatch(e -> "SARL Alpha".equals(e.dossierNom()));

        // Reste-a-faire : 1 ticket + 1 demande (dossierB exclu)
        assertThat(s.counts().tickets()).isEqualTo(1);
        assertThat(s.counts().demandes()).isEqualTo(1);
        assertThat(s.resteAFaire()).hasSize(2);
    }

    @Test
    @DisplayName("Isolation : un autre employe voit SES dossiers, pas ceux du premier")
    void isolationBetweenEmployees() {
        AgentSignals other = aggregator.aggregate(ws, otherEmp);

        // otherEmp ne gere que dossierB (sans client) : 1 echeance bruit + 1 ticket
        assertThat(other.echeances()).hasSize(1);
        assertThat(other.counts().tickets()).isEqualTo(1);
        assertThat(other.counts().demandes()).isZero();
        assertThat(other.echeances()).allMatch(e -> "SARL Beta".equals(e.dossierNom()));
    }

    @Test
    @DisplayName("Copilote borne le retard a 45 j (alertes fiscales + deadlines), TRAITEE jamais")
    void borneLeRetardA45Jours() {
        // Alertes fiscales sur dossierA (responsable = emp) : marqueurs par type_echeance.
        insertAlerte("FISC_RETARD_10J", "CURRENT_DATE - INTERVAL '10 day'", "PLANIFIEE");   // retard recent -> visible
        insertAlerte("FISC_RETARD_170J", "CURRENT_DATE - INTERVAL '170 day'", "PLANIFIEE"); // retard fossile -> exclu
        insertAlerte("FISC_AVENIR_3J", "CURRENT_DATE + INTERVAL '3 day'", "PLANIFIEE");     // a venir <= J+7 -> visible
        insertAlerte("FISC_TRAITEE_10J", "CURRENT_DATE - INTERVAL '10 day'", "TRAITEE");    // traitee -> jamais

        // Deadlines sur dossierA : marqueurs par title.
        insertDeadline("DL_RETARD_10J", "NOW() - INTERVAL '10 day'", "OUVERTE");   // retard recent -> visible
        insertDeadline("DL_RETARD_170J", "NOW() - INTERVAL '170 day'", "OUVERTE"); // retard fossile -> exclu
        insertDeadline("DL_AVENIR_3J", "NOW() + INTERVAL '3 day'", "OUVERTE");     // a venir <= J+7 -> visible
        insertDeadline("DL_FERMEE_10J", "NOW() - INTERVAL '10 day'", "FERMEE");    // traitee/fermee -> jamais

        AgentSignals s = aggregator.aggregate(ws, emp);
        List<String> titres = s.echeances().stream().map(EcheanceSignal::intitule).toList();

        assertThat(titres).contains(
                "FISC_RETARD_10J", "FISC_AVENIR_3J", "DL_RETARD_10J", "DL_AVENIR_3J");
        assertThat(titres).doesNotContain(
                "FISC_RETARD_170J", "FISC_TRAITEE_10J", "DL_RETARD_170J", "DL_FERMEE_10J");
    }

    // ------------------------------------------------------------------
    private void insertAlerte(String type, String dateExpr, String statut) {
        jdbc.update(("""
                INSERT INTO dataroom_alertes_echeances(id,workspace_id,dossier_id,type_echeance,date_echeance,statut)
                VALUES (?,?,?,?, %s, ?)
                """).formatted(dateExpr), UUID.randomUUID(), ws, dossierA, type, statut);
    }

    private void insertDeadline(String title, String dueExpr, String statut) {
        jdbc.update(("""
                INSERT INTO deadlines(id,workspace_id,dossier_id,title,due_at,severity,statut)
                VALUES (?,?,?,?, %s, 'CRITICAL', ?)
                """).formatted(dueExpr), UUID.randomUUID(), ws, dossierA, title, statut);
    }
}
