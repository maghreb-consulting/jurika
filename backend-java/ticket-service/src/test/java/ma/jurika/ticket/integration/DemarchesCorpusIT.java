package ma.jurika.ticket.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import ma.jurika.common.security.JwtClaims;
import ma.jurika.common.security.JwtKeyConfig;
import ma.jurika.ticket.application.referentiel.ReferentielCorpus;
import ma.jurika.ticket.application.referentiel.ReferentielCorpus.Condition;
import ma.jurika.ticket.application.referentiel.ReferentielCorpus.Decision;
import ma.jurika.ticket.application.referentiel.ReferentielCorpus.Ligne;
import ma.jurika.ticket.application.referentiel.ReferentielCorpus.Rapport;
import ma.jurika.ticket.application.referentiel.ReferentielCorpus.Renvoi;
import ma.jurika.ticket.application.referentiel.SourceReferentiel;
import ma.jurika.ticket.domain.port.MemberDirectory;
import ma.jurika.ticket.domain.port.TicketEventPublisher;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lot L4b (E4, RG-DEM-01, 05, 08 ; RG-TKT-06) : un ticket hors creation tire ses demarches
 * du referentiel du corpus selon ses decisions, de bout en bout (jurika_app, RLS active) :
 * import par le super-administrateur, choix des decisions, renvoi vers la matrice de la
 * famille, condition lue sur les donnees du ticket, cochage d'une ligne du corpus,
 * obstacles nommes au passage de statut.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
                "spring.cloud.discovery.enabled=false",
                "eureka.client.enabled=false",
                "jurika.trial.remote.enabled=false",
                "jurika.audit.async-enabled=false",
                "jurika.demo-seed=false",
                "jurika.jwt.algorithm=HS256"
        }
)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DemarchesCorpusIT {

    static final String SECRET = UUID.randomUUID() + "-" + UUID.randomUUID();
    static final UUID WS_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID KARIM = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final String MODIF = "MATRICE_DECISIONS_MODIFICATIONS_SARL";
    static final String COMPTES = "MATRICE_COMPTES_RESULTAT_SARL";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_corpus")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("testcontainers-init.sql");

    private static boolean migree;

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        migrerChaineAmont();
        registry.add("jurika.jwt.hmac-secret", () -> SECRET);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "jurika_app");
        registry.add("spring.datasource.password", () -> "jurika_app_it");
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    private static synchronized void migrerChaineAmont() {
        if (migree) return;
        migrer("filesystem:../auth-service/src/main/resources/db/migration", "flyway_history_auth", null);
        migrer("classpath:db/migration", "flyway_history_ticket", "20");
        migrer("filesystem:../dataroom-service/src/main/resources/db/migration", "flyway_history_dataroom", null);
        migrer("classpath:db/migration", "flyway_history_ticket", null);
        migrer("filesystem:../workflow-service/src/main/resources/db/migration", "flyway_history_workflow", null);
        migree = true;
    }

    private static void migrer(String emplacement, String historique, String cible) {
        var config = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations(emplacement).table(historique)
                .baselineOnMigrate(true).baselineVersion("0");
        if (cible != null) config.target(cible);
        config.load().migrate();
    }

    private static JdbcTemplate proprietaire() {
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    @TestConfiguration
    static class Infrastructure {
        @Bean
        ConnectionFactory rabbitConnectionFactory() {
            return mock(ConnectionFactory.class);
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockBean private MemberDirectory annuaire;
    @MockBean private TicketEventPublisher evenements;
    @MockBean private SourceReferentiel source;

    private final JdbcTemplate owner = proprietaire();
    private static UUID ticketId;

    @BeforeEach
    void preparer() {
        when(annuaire.roleOf(any(), any())).thenReturn(Optional.of("EMPLOYE"));
        when(annuaire.estActif(any(), any())).thenReturn(true);
        when(source.lire()).thenReturn(referentiel());
    }

    private static String jeton(String role) {
        return "Bearer " + Jwts.builder()
                .claim(JwtClaims.USER_ID, KARIM.toString())
                .claim(JwtClaims.WORKSPACE_ID, WS_A.toString())
                .claim(JwtClaims.ROLE, role)
                .claim(JwtClaims.EMAIL, "test@rls.test")
                .claim(JwtClaims.TYPE, JwtClaims.TYPE_ACCESS)
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(JwtKeyConfig.legacyHmacKey(SECRET))
                .compact();
    }

    private static Ligne ligne(String matrice, int ordre, String decision, String statut, String libelle,
                               Condition condition, boolean obligatoire, Integer numero) {
        return new Ligne(matrice, "F", "SARL", ordre, decision, null, statut, libelle, "Greffe", condition,
                obligatoire, null, null, null, numero, null, null);
    }

    private static Ligne delai(String matrice, int ordre, String libelle, ReferentielCorpus.Delai d) {
        return new Ligne(matrice, "F", "SARL", ordre, "approbation_comptes", null, "DEROULEMENT_DEMARCHE", libelle,
                "Greffe", null, false, d, null, null, 1, null, null);
    }

    /**
     * Matrice des modifications : approbation_comptes (renvoyee vers la matrice des comptes,
     * decision n 1), transfert_siege (lignes propres). Matrice des comptes : la decision n 1,
     * dont un depot conditionne par $CAC_EXISTE.
     */
    static ReferentielCorpus referentiel() {
        List<Ligne> lignes = List.of(
                ligne(MODIF, 1, "approbation_comptes", "GENERATION_DOCUMENTS", "Ligne propre a ignorer (renvoi)", null, true, 1),
                ligne(MODIF, 2, "transfert_siege", "GENERATION_DOCUMENTS", "G\u00e9n\u00e9ration du PV de transfert", null, true, 30),
                ligne(COMPTES, 1, "approbation_comptes", "GENERATION_DOCUMENTS", "G\u00e9n\u00e9ration du PV d'approbation", null, true, 1),
                ligne(COMPTES, 2, "approbation_comptes", "DEROULEMENT_DEMARCHE", "D\u00e9p\u00f4t du rapport du commissaire \u2014 d\u00e9p\u00f4t",
                        new Condition("CAC_EXISTE", "EGAL", "oui", "Si un commissaire"), true, 1),
                ligne(COMPTES, 3, "approbation_comptes", "DEROULEMENT_DEMARCHE", "D\u00e9p\u00f4t du rapport du commissaire \u2014 retrait du r\u00e9c\u00e9piss\u00e9",
                        new Condition("CAC_EXISTE", "EGAL", "oui", "Si un commissaire"), true, 1),
                ligne(COMPTES, 4, "approbation_comptes", "DEROULEMENT_DEMARCHE", "Contr\u00f4le du dossier",
                        new Condition(null, "PHRASE", null, "Pi\u00e8ce produite au cours du ticket"), true, 1),
                delai(COMPTES, 5, "Publication de l'avis (d\u00e9lai sans d\u00e9part nomm\u00e9)",
                        new ReferentielCorpus.Delai("30 jours \u2014 texte de la matrice : dans les trente jours", 30, "JOURS", null, null)),
                delai(COMPTES, 6, "Inscription (d\u00e9part nomm\u00e9)",
                        new ReferentielCorpus.Delai("1 mois \u00e0 compter de $DATE_APPROBATION", 1, "MOIS", "DATE_APPROBATION", null)),
                delai(COMPTES, 7, "Annonce (date limite)",
                        new ReferentielCorpus.Delai("au plus tard le $ANNONCE_LEGALE_DATE_LIMITE", null, null, null, "ANNONCE_LEGALE_DATE_LIMITE")));
        return new ReferentielCorpus("CORPUS_IT", lignes,
                List.of(new Renvoi(MODIF, 1, "A", "Approbation des comptes", "approbation_comptes", "COMPTES",
                        COMPTES, 1, "La matrice de la famille fait foi")),
                List.of(new Decision(MODIF, 1, "A", "approbation_comptes", "Approbation des comptes annuels", "AGO"),
                        new Decision(MODIF, 30, "C", "transfert_siege", "Transfert du si\u00e8ge social", "AGE"),
                        new Decision(COMPTES, 1, "A", "approbation_comptes", "Approbation des \u00e9tats de synth\u00e8se", "AGO")),
                List.of(),
                new Rapport(Map.of(MODIF, 2, COMPTES, 7), 9, List.of(), List.of(), List.of()));
    }

    @Test
    @Order(1)
    void sans_referentiel_charge_le_passage_est_bloque_et_le_dit() throws Exception {
        MvcResult imp = mvc.perform(post("/api/v1/tickets").header("Authorization", jeton("EMPLOYE"))
                        .contentType("application/json").content("""
                                { "titre": "Import PARACOSME", "type": "IMPORT",
                                  "companyInfo": { "raisonSociale": "PARACOSME", "formeJuridique": "SARL" } }
                                """))
                .andExpect(status().is2xxSuccessful()).andReturn();
        String dossierId = json.readTree(imp.getResponse().getContentAsString()).get("dossierId").asText();
        MvcResult r = mvc.perform(post("/api/v1/tickets").header("Authorization", jeton("EMPLOYE"))
                        .contentType("application/json").content("""
                                { "titre": "Approbation 2025", "type": "MODIFICATION", "dossierId": "%s" }
                                """.formatted(dossierId)))
                .andExpect(status().is2xxSuccessful()).andReturn();
        ticketId = UUID.fromString(json.readTree(r.getResponse().getContentAsString()).get("id").asText());

        JsonNode d = lire("/api/v1/tickets/" + ticketId + "/decisions");
        assertThat(d.get("corpus").asBoolean()).isTrue();
        assertThat(d.get("manques").toString()).contains("non charg");
        mvc.perform(post("/api/v1/tickets/" + ticketId + "/transition").header("Authorization", jeton("EMPLOYE"))
                        .contentType("application/json").content("{\"target\":\"GENERATION_DOCUMENTS\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(2)
    void le_super_administrateur_importe_puis_les_decisions_sont_proposees() throws Exception {
        mvc.perform(post("/api/v1/tickets/referentiel-demarches/import").header("Authorization", jeton("EMPLOYE")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/tickets/referentiel-demarches/import").header("Authorization", jeton("SUPER_ADMIN")))
                .andExpect(status().isOk());
        JsonNode d = lire("/api/v1/tickets/" + ticketId + "/decisions");
        assertThat(d.get("forme").asText()).isEqualTo("SARL");
        assertThat(d.get("matrices").toString()).contains(MODIF).contains("MATRICE_INCIDENTS_ASSEMBLEE");
        assertThat(d.get("proposees").toString()).contains("Approbation des comptes annuels").contains(COMPTES);
        assertThat(d.get("manques").toString()).contains("Aucune d");
    }

    @Test
    @Order(3)
    void les_decisions_choisissent_les_lignes_et_le_renvoi_fait_foi() throws Exception {
        mvc.perform(put("/api/v1/tickets/" + ticketId + "/decisions").header("Authorization", jeton("EMPLOYE"))
                        .contentType("application/json").content("""
                                { "decisions": [ { "matrice": "%s", "code": "approbation_comptes" } ] }
                                """.formatted(MODIF)))
                .andExpect(status().isOk());
        JsonNode vue = lire("/api/v1/tickets/" + ticketId + "/demarches");
        String texte = vue.toString();
        assertThat(texte).contains("G\u00e9n\u00e9ration du PV d'approbation").doesNotContain("Ligne propre a ignorer");
        assertThat(texte).doesNotContain("PV de transfert");
        // Donnee non saisie : la ligne est a apprecier (conditionnelle), raison nommee.
        JsonNode depot = ligneDe(vue, "d\u00e9p\u00f4t");
        assertThat(depot.get("applicabilite").asText()).isEqualTo("A_APPRECIER");
        assertThat(depot.get("obligatoire").asBoolean()).isFalse();
        assertThat(depot.get("raisonApplicabilite").asText()).contains("$CAC_EXISTE non renseign");
        assertThat(depot.get("formaliteVolet").asText()).isEqualTo("DEPOT");
        assertThat(ligneDe(vue, "retrait").get("formaliteVolet").asText()).isEqualTo("RETRAIT");
        assertThat(ligneDe(vue, "Contr\u00f4le du dossier").get("raisonApplicabilite").asText()).contains("Pi\u00e8ce produite");
        // Une decision etrangere a la matrice est refusee.
        mvc.perform(put("/api/v1/tickets/" + ticketId + "/decisions").header("Authorization", jeton("EMPLOYE"))
                        .contentType("application/json").content("""
                                { "decisions": [ { "matrice": "MATRICE_X", "code": "inventee" } ] }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(4)
    void la_donnee_du_ticket_tranche_la_condition() throws Exception {
        owner.update("INSERT INTO dossier_variables (workspace_id, ticket_id, variable, valeur, origine, saisie_par_id) "
                + "VALUES (?, ?, 'CAC_EXISTE', 'non', 'SAISIE', ?)", WS_A, ticketId, KARIM);
        String texte = lire("/api/v1/tickets/" + ticketId + "/demarches").toString();
        assertThat(texte).as("condition contraire : sans objet, absente de la liste")
                .doesNotContain("D\u00e9p\u00f4t du rapport du commissaire");
        owner.update("UPDATE dossier_variables SET valeur = 'oui' WHERE ticket_id = ? AND variable = 'CAC_EXISTE'", ticketId);
        JsonNode depot = ligneDe(lire("/api/v1/tickets/" + ticketId + "/demarches"), "d\u00e9p\u00f4t");
        assertThat(depot.get("applicabilite").asText()).isEqualTo("APPLICABLE");
        assertThat(depot.get("obligatoire").asBoolean()).isTrue();
    }

    @Test
    @Order(5)
    void une_ligne_du_corpus_se_coche_et_le_passage_nomme_ce_qui_manque() throws Exception {
        mvc.perform(post("/api/v1/tickets/" + ticketId + "/transition").header("Authorization", jeton("EMPLOYE"))
                        .contentType("application/json").content("{\"target\":\"GENERATION_DOCUMENTS\"}"))
                .andExpect(status().isOk());
        JsonNode pv = ligneDe(lire("/api/v1/tickets/" + ticketId + "/demarches"), "PV d'approbation");
        MvcResult refus = mvc.perform(post("/api/v1/tickets/" + ticketId + "/transition").header("Authorization", jeton("EMPLOYE"))
                        .contentType("application/json").content("{\"target\":\"DEROULEMENT_DEMARCHE\"}"))
                .andExpect(status().is4xxClientError()).andReturn();
        assertThat(refus.getResponse().getContentAsString()).contains("G\u00e9n\u00e9ration du PV d'approbation");
        mvc.perform(post("/api/v1/tickets/" + ticketId + "/demarches/" + pv.get("ordre").asInt() + "/cocher")
                        .header("Authorization", jeton("EMPLOYE"))
                        .contentType("application/json").content("{\"documentIds\":[]}"))
                .andExpect(status().isOk());
        assertThat(owner.queryForObject("SELECT etat FROM ticket_demarches WHERE ticket_id = ? AND demarche_id = ?",
                String.class, ticketId, UUID.fromString(pv.get("demarcheId").asText()))).isEqualTo("COCHEE");
        mvc.perform(post("/api/v1/tickets/" + ticketId + "/transition").header("Authorization", jeton("EMPLOYE"))
                        .contentType("application/json").content("{\"target\":\"DEROULEMENT_DEMARCHE\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(6)
    void les_delais_partent_d_une_donnee_ou_d_une_date_saisie_sur_la_ligne() throws Exception {
        java.time.LocalDate aujourdhui = java.time.LocalDate.now(java.time.ZoneId.of("Africa/Casablanca"));
        JsonNode vue = lire("/api/v1/tickets/" + ticketId + "/demarches");
        JsonNode sansDepart = ligneDe(vue, "sans d\u00e9part nomm\u00e9");
        assertThat(sansDepart.get("delaiAttendu").asText()).isEqualTo("DEPART");
        assertThat(sansDepart.get("echeance").isNull()).as("aucune date fabriquee").isTrue();
        assertThat(ligneDe(vue, "Inscription (").get("delaiDonnee").asText()).isEqualTo("DATE_APPROBATION");
        assertThat(ligneDe(vue, "date limite").get("delaiAttendu").asText()).isEqualTo("ECHEANCE");

        // Point de depart saisi sur la ligne : l'echeance se calcule (RG-DEM-06), et une
        // echeance depassee entre aux points d'attention.
        java.time.LocalDate depart = aujourdhui.minusDays(40);
        mvc.perform(put("/api/v1/tickets/" + ticketId + "/demarches/" + sansDepart.get("ordre").asInt() + "/delai")
                        .header("Authorization", jeton("EMPLOYE")).contentType("application/json")
                        .content("{\"depart\":\"" + depart + "\"}"))
                .andExpect(status().isOk());
        // Point de depart porte par une donnee du ticket.
        owner.update("INSERT INTO dossier_variables (workspace_id, ticket_id, variable, valeur, origine, saisie_par_id) "
                + "VALUES (?, ?, 'DATE_APPROBATION', '2026-09-15', 'SAISIE', ?)", WS_A, ticketId, KARIM);
        vue = lire("/api/v1/tickets/" + ticketId + "/demarches");
        assertThat(ligneDe(vue, "sans d\u00e9part nomm\u00e9").get("echeance").asText()).isEqualTo(depart.plusDays(30).toString());
        assertThat(ligneDe(vue, "Inscription (").get("echeance").asText()).isEqualTo("2026-10-15");
        assertThat(vue.get("avancement").get("pointsAttention").toString()).contains("sans d\u00e9part nomm\u00e9")
                .contains("DEPASSE");
        // La date attendue est la bonne : une echeance n'est pas un depart.
        mvc.perform(put("/api/v1/tickets/" + ticketId + "/demarches/" + ligneDe(vue, "date limite").get("ordre").asInt() + "/delai")
                        .header("Authorization", jeton("EMPLOYE")).contentType("application/json")
                        .content("{\"depart\":\"2026-10-01\"}"))
                .andExpect(status().isConflict());
    }

    private JsonNode lire(String url) throws Exception {
        MvcResult r = mvc.perform(get(url).header("Authorization", jeton("EMPLOYE"))).andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString());
    }

    private static JsonNode ligneDe(JsonNode vue, String fragment) {
        for (JsonNode phase : vue.get("phases")) {
            for (JsonNode l : phase.get("demarches")) {
                if (l.get("libelle").asText().contains(fragment)) return l;
            }
        }
        throw new AssertionError("Aucune ligne contenant : " + fragment + " dans " + vue);
    }
}
