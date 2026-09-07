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

    private UUID ticketCreation;
    private UUID demarcheReference;
    /** Les demarches de test se suivent : 1 = reference, puis 2, 3, ... */
    private int prochainOrdre = 1;

    @BeforeEach
    void seed() {
        jdbc.execute("DELETE FROM dataroom_client_access_log");
        jdbc.execute("DELETE FROM dataroom_demandes_client");
        jdbc.execute("DELETE FROM deadlines");
        jdbc.execute("DELETE FROM ticket_demarches");
        jdbc.execute("DELETE FROM demarches_justificatifs");
        jdbc.execute("DELETE FROM demarches_referentiel");
        jdbc.execute("DELETE FROM tickets");
        jdbc.execute("DELETE FROM entreprise_dossiers");
        jdbc.execute("DELETE FROM users");
        jdbc.execute("DELETE FROM workspaces");

        prochainOrdre = 1;

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

        // Echeances : deadline depassee (dossierA) + delai legal de demarche a J+5.
        jdbc.update("""
                INSERT INTO deadlines(id,workspace_id,dossier_id,title,due_at,severity,statut)
                VALUES (?,?,?,?, NOW() - INTERVAL '1 day', 'CRITICAL', 'OUVERTE')
                """, UUID.randomUUID(), ws, dossierA, "Depot RC en retard");

        // Referentiel minimal : une etape de reference (1) et une etape porteuse
        // d'un delai (2). Le delai court a partir de la date de COCHAGE de
        // l'etape 1 : en la cochant il y a (ANCRAGE) jours et en donnant un
        // delai de (ANCRAGE + k) jours, l'echeance tombe a J+k.
        ticketCreation = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tickets(id,workspace_id,reference,titre,type,statut,priorite,dossier_id,assigne_id,created_at)
                VALUES (?,?,?,?, 'CREATION', 'DEROULEMENT_DEMARCHE', 'NORMALE', ?, ?, NOW())
                """, ticketCreation, ws, "T-DEL", "Creation Alpha", dossierA, emp);
        demarcheReference = insertDemarcheReferentiel(1, "Signature des statuts", null, null, null);
        cocher(ticketCreation, demarcheReference, ANCRAGE_JOURS);
        insertDelaiLegal("Immatriculation au RC", 5);
        // Bruit : deadline sur dossierB (otherEmp) -> ne doit PAS remonter pour emp
        jdbc.update("""
                INSERT INTO deadlines(id,workspace_id,dossier_id,title,due_at,severity,statut)
                VALUES (?,?,?,?, NOW() - INTERVAL '1 day', 'CRITICAL', 'OUVERTE')
                """, UUID.randomUUID(), ws, dossierB, "Bruit B");

        // Reste-a-faire : 1 ticket NOUVEAU (dossierA) + 1 demande NON_TRAITEE (dossierA)
        jdbc.update("""
                INSERT INTO tickets(id,workspace_id,reference,titre,type,statut,priorite,dossier_id,assigne_id,created_at)
                VALUES (?,?,?,?,?,?,?,?,?, NOW() - INTERVAL '4 day')
                """, UUID.randomUUID(), ws, "T-001", "Modif statuts", "MODIFICATION", "CREATION_TICKET", "NORMALE", dossierA, emp);
        jdbc.update("""
                INSERT INTO tickets(id,workspace_id,reference,titre,type,statut,priorite,dossier_id,assigne_id,created_at)
                VALUES (?,?,?,?,?,?,?,?,?, NOW())
                """, UUID.randomUUID(), ws, "T-002", "Bruit B", "CREATION", "CREATION_TICKET", "NORMALE", dossierB, otherEmp);
        jdbc.update("""
                INSERT INTO dataroom_demandes_client(id,workspace_id,dossier_id,sujet,statut,created_at)
                VALUES (?,?,?,?, 'NON_TRAITEE', NOW() - INTERVAL '6 day')
                """, UUID.randomUUID(), ws, dossierA, "Question TVA");
    }

    @Test
    @DisplayName("Agrege et scope les 3 signaux sur les dossiers de l'employe")
    void aggregatesAndScopes() {
        AgentSignals s = aggregator.aggregate(ws, emp);

        // 2 echeances (deadline depassee + delai legal a J+5), bruit de dossierB exclu
        assertThat(s.echeances()).hasSize(2);
        assertThat(s.counts().echeancesDepassees()).isEqualTo(1);
        assertThat(s.echeances()).allMatch(e -> "SARL Alpha".equals(e.dossierNom()));

        // Reste-a-faire : 2 tickets ouverts d'emp (T-001 + le ticket CREATION qui
        // porte les delais legaux) + 1 demande. Le bruit de dossierB reste exclu.
        assertThat(s.counts().tickets()).isEqualTo(2);
        assertThat(s.counts().demandes()).isEqualTo(1);
        assertThat(s.resteAFaire()).hasSize(3);
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
    @DisplayName("Copilote borne le retard a 45 j (delais legaux + deadlines), demarche cochee jamais")
    void borneLeRetardA45Jours() {
        // Delais legaux de demarches sur dossierA (responsable = emp).
        insertDelaiLegal("DEL_RETARD_10J", -10);   // retard recent -> visible
        insertDelaiLegal("DEL_RETARD_170J", -170); // retard fossile -> exclu
        insertDelaiLegal("DEL_AVENIR_3J", 3);      // a venir <= J+7 -> visible
        // Demarche deja cochee : son delai ne doit plus jamais remonter.
        int ordreTraitee = ++prochainOrdre;
        UUID traitee = insertDemarcheReferentiel(ordreTraitee, "DEL_TRAITEE_10J",
                ANCRAGE_JOURS - 10, "JOURS", 1);
        cocher(ticketCreation, traitee, 0);

        // Deadlines sur dossierA : marqueurs par title.
        insertDeadline("DL_RETARD_10J", "NOW() - INTERVAL '10 day'", "OUVERTE");   // retard recent -> visible
        insertDeadline("DL_RETARD_170J", "NOW() - INTERVAL '170 day'", "OUVERTE"); // retard fossile -> exclu
        insertDeadline("DL_AVENIR_3J", "NOW() + INTERVAL '3 day'", "OUVERTE");     // a venir <= J+7 -> visible
        insertDeadline("DL_FERMEE_10J", "NOW() - INTERVAL '10 day'", "FERMEE");    // traitee/fermee -> jamais

        AgentSignals s = aggregator.aggregate(ws, emp);
        List<String> titres = s.echeances().stream().map(EcheanceSignal::intitule).toList();

        assertThat(titres).contains(
                "DEL_RETARD_10J", "DEL_AVENIR_3J", "DL_RETARD_10J", "DL_AVENIR_3J");
        assertThat(titres).doesNotContain(
                "DEL_RETARD_170J", "DEL_TRAITEE_10J", "DL_RETARD_170J", "DL_FERMEE_10J");
    }

    // ------------------------------------------------------------------

    /**
     * Ancrage du point de depart : l'etape de reference est cochee il y a ce
     * nombre de jours. Un delai de (ANCRAGE_JOURS + k) jours place donc
     * l'echeance a J+k, et un delai de (ANCRAGE_JOURS - k) a J-k.
     */
    private static final int ANCRAGE_JOURS = 200;

    private UUID insertDemarcheReferentiel(int ordre, String libelle,
                                            Integer delaiValeur, String delaiUnite,
                                            Integer referenceOrdre) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO demarches_referentiel(id, workflow_type, ordre, phase_code, phase_libelle,
                        libelle, statut_ticket, obligatoire, delai_valeur, delai_unite, delai_reference_ordre)
                VALUES (?, 'CREATION', ?, 'P5', 'P5 Fiscal / RC', ?, 'DEROULEMENT_DEMARCHE', 'O', ?, ?, ?)
                """, id, (short) ordre, libelle,
                delaiValeur == null ? null : delaiValeur.shortValue(), delaiUnite,
                referenceOrdre == null ? null : referenceOrdre.shortValue());
        return id;
    }

    private void cocher(UUID ticketId, UUID demarcheId, int ilYAJours) {
        jdbc.update(("""
                INSERT INTO ticket_demarches(id, workspace_id, ticket_id, demarche_id, etat, coche_at)
                VALUES (?,?,?,?, 'COCHEE', NOW() - INTERVAL '%d day')
                """).formatted(ilYAJours), UUID.randomUUID(), ws, ticketId, demarcheId);
    }

    /** Cree une demarche a faire dont l'echeance tombe a J+{@code joursAvantEcheance}. */
    private void insertDelaiLegal(String libelle, int joursAvantEcheance) {
        int ordre = ++prochainOrdre;
        insertDemarcheReferentiel(ordre, libelle, ANCRAGE_JOURS + joursAvantEcheance, "JOURS", 1);
    }

    private void insertDeadline(String title, String dueExpr, String statut) {
        jdbc.update(("""
                INSERT INTO deadlines(id,workspace_id,dossier_id,title,due_at,severity,statut)
                VALUES (?,?,?,?, %s, 'CRITICAL', ?)
                """).formatted(dueExpr), UUID.randomUUID(), ws, dossierA, title, statut);
    }
}
