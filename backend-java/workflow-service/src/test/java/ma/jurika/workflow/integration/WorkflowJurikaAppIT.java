package ma.jurika.workflow.integration;

import ma.jurika.common.security.TenantContext;
import ma.jurika.workflow.api.WorkflowController.RegisterPieceRequest;
import ma.jurika.workflow.application.MagasinVariables;
import ma.jurika.workflow.application.WorkflowFinalizationService;
import ma.jurika.workflow.application.WorkflowProgressLookup;
import ma.jurika.workflow.application.WorkflowUseCases;
import ma.jurika.workflow.domain.model.VariableDuDossier;
import ma.jurika.workflow.domain.model.WorkflowProgress;
import ma.jurika.workflow.domain.model.WorkflowType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lot L0, etape E16 : workflow-service en role d'execution jurika_app (RLS
 * active, garde « hors transaction »), sur les vraies migrations. Un test par
 * chemin inventorie (L0_inventaire_sans_workspace.md, WF1 et section 2.6), et
 * chacun attend des LIGNES : sans workspace effectif, ces chemins rendaient
 * vide en silence (identite de la societe, magasin de variables, dossier de
 * fin de parcours non cree).
 *
 * <p>Le workspace courant est pose AVANT l'appel, comme le fait JwtAuthFilter
 * sur les routes authentifiees ; la route interne WF1, sans JWT, le recoit en
 * parametre.
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
@AutoConfigureMockMvc
class WorkflowJurikaAppIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_workflow")
            .withUsername("jurika_it")
            .withPassword("jurika_it_pwd")
            .withInitScript("testcontainers-init.sql")
            .withReuse(false);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        SchemaJurikaDb.migrer(POSTGRES);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        // L'application tourne en jurika_app (la RLS s'applique) ; Flyway migre
        // avec le proprietaire.
        registry.add("spring.datasource.username", () -> "jurika_app");
        registry.add("spring.datasource.password", () -> "jurika_app_it");
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    private static final UUID EMPLOYE = SchemaJurikaDb.EMPLOYE_SEME;

    /** Preparation et assertions en PROPRIETAIRE, hors RLS. */
    private final JdbcTemplate jdbc = SchemaJurikaDb.proprietaire(POSTGRES);
    @Autowired private MockMvc mvc;
    @Autowired private WorkflowUseCases workflows;
    @Autowired private WorkflowProgressLookup lookup;
    @Autowired private WorkflowFinalizationService finalisation;
    @Autowired private MagasinVariables magasin;
    @Autowired private ma.jurika.workflow.application.ProjecteurVariablesCreation projecteur;
    @Autowired private ma.jurika.workflow.application.DonneesAttenduesService donneesAttendues;
    @Autowired private ma.jurika.workflow.application.ClausesLibresService clausesLibres;

    private UUID workspaceId;
    private UUID dossierId;
    private UUID ticketId;

    @BeforeEach
    void seed() {
        jdbc.execute("DELETE FROM donnees_attendues");
        jdbc.execute("DELETE FROM dossier_variables");
        jdbc.execute("DELETE FROM workflow_progress");
        jdbc.execute("DELETE FROM tickets");
        jdbc.execute("DELETE FROM entreprise_dossiers");
        jdbc.execute("DELETE FROM workspaces");

        workspaceId = UUID.randomUUID();
        dossierId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
        SchemaJurikaDb.workspace(jdbc, workspaceId, "Cabinet Workflow", "JUR-F0001");
        jdbc.update("""
                INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale, forme_juridique,
                                                rc_numero, rc_tribunal, capital_social_mad, ville,
                                                responsable_id)
                VALUES (?, ?, 'NOVA INDUSTRIE', 'SARL', '123456', 'CASABLANCA', 100000, 'CASABLANCA', ?)
                """, dossierId, workspaceId, EMPLOYE);
        jdbc.update("""
                INSERT INTO tickets(id, workspace_id, reference, titre, type, statut, cree_par_id)
                VALUES (?, ?, 'T-2026-00901', 'Creation SARL', 'CREATION', 'CREATION_TICKET', ?)
                """, ticketId, workspaceId, EMPLOYE);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** Comme JwtAuthFilter : le workspace est pose avant le proxy transactionnel. */
    private <T> T dansLeWorkspace(Supplier<T> appel) {
        TenantContext.set(workspaceId);
        try {
            return appel.get();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void wf1_identite_interne_du_dossier_lue_sans_jwt() throws Exception {
        mvc.perform(get("/internal/dossiers/" + dossierId + "/identite").param("workspaceId", workspaceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.denomination").value("NOVA INDUSTRIE"))
                .andExpect(jsonPath("$.rcNumero").value("123456"));
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void wf1_identite_d_un_autre_workspace_reste_vide() throws Exception {
        mvc.perform(get("/internal/dossiers/" + dossierId + "/identite").param("workspaceId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.denomination").doesNotExist());
    }

    @Test
    void parcours_demarre_sauve_relit_et_gere_les_pieces() {
        WorkflowProgress demarre = dansLeWorkspace(() ->
                workflows.startOrResume(workspaceId, ticketId, WorkflowType.CREATION, EMPLOYE));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workflow_progress WHERE ticket_id = ? AND workspace_id = ?",
                Integer.class, ticketId, workspaceId)).isEqualTo(1);

        dansLeWorkspace(() -> workflows.save(workspaceId, ticketId, 1,
                Map.of("step1", Map.of("denomination", "NOVA INDUSTRIE")), EMPLOYE));
        WorkflowProgress relu = dansLeWorkspace(() -> workflows.get(workspaceId, ticketId));
        assertThat(relu.id()).isEqualTo(demarre.id());
        assertThat(relu.data()).containsKey("step1");

        dansLeWorkspace(() -> workflows.registerPiece(workspaceId, ticketId,
                new RegisterPieceRequest("CN", "Certificat negatif", "cn.pdf", 10L, "application/pdf", 1)));
        assertThat(jdbc.queryForObject("SELECT data->'pieces'->'CN' IS NOT NULL FROM workflow_progress WHERE id = ?",
                Boolean.class, demarre.id())).isTrue();
        dansLeWorkspace(() -> workflows.unregisterPiece(workspaceId, ticketId, "CN"));
        assertThat(jdbc.queryForObject("SELECT data->'pieces'->'CN' IS NOT NULL FROM workflow_progress WHERE id = ?",
                Boolean.class, demarre.id())).isFalse();

        // WorkflowProgressLookup : transaction NEUVE (REQUIRES_NEW), meme workspace.
        assertThat(dansLeWorkspace(() -> lookup.findInNewTransaction(workspaceId, ticketId)))
                .hasValueSatisfying(p -> assertThat(p.id()).isEqualTo(demarre.id()));
    }

    @Test
    void magasin_de_variables_ecrit_puis_relu() {
        dansLeWorkspace(() -> {
            magasin.poser(workspaceId, ticketId, "DENOMINATION", "NOVA INDUSTRIE",
                    VariableDuDossier.Origine.SAISIE, EMPLOYE, "test");
            return null;
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dossier_variables WHERE ticket_id = ? AND workspace_id = ?",
                Integer.class, ticketId, workspaceId)).isEqualTo(1);

        List<VariableDuDossier> lues = dansLeWorkspace(() -> magasin.lire(workspaceId, ticketId));
        assertThat(lues).hasSize(1);
        Map<String, Object> charge = dansLeWorkspace(() -> magasin.lirePourGeneration(workspaceId, ticketId));
        assertThat(charge).isNotEmpty();
    }

    /**
     * Lot L1, etape E12 (RG-VAR-02) : la provenance distingue la saisie, le calcul, la
     * valeur EXTRAITE d'une piece (confirmee par un employe, RG-VAR-09 : auteur
     * obligatoire) et la valeur reprise de la FICHE societe (sans auteur).
     */
    @Test
    void provenances_extraite_et_fiche() {
        dansLeWorkspace(() -> {
            magasin.poser(workspaceId, ticketId, "ASSOCIE_CIN", "AB123456",
                    VariableDuDossier.Origine.valueOf("EXTRAITE"), EMPLOYE, "extraction-cin");
            magasin.poser(workspaceId, ticketId, "RC_NUMERO", "123456",
                    VariableDuDossier.Origine.valueOf("FICHE"), null, "fiche-societe");
            return null;
        });
        List<VariableDuDossier> lues = dansLeWorkspace(() -> magasin.lire(workspaceId, ticketId));
        assertThat(lues).extracting(v -> v.variable() + "=" + v.origine())
                .contains("ASSOCIE_CIN=EXTRAITE", "RC_NUMERO=FICHE");
        // Une valeur extraite sans employe qui l'a confirmee est refusee par la base.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO dossier_variables (workspace_id, ticket_id, variable, valeur, origine, saisie_le) "
                        + "VALUES (?, ?, 'X', 'v', 'EXTRAITE', NOW())", workspaceId, ticketId))
                .hasMessageContaining("ck_dossier_variables_saisie_auteur");
    }

    @Test
    void fin_de_parcours_creation_cree_le_dossier_dans_le_workspace() {
        UUID cree = dansLeWorkspace(() -> finalisation.createEntrepriseDossierInNewTransaction(
                workspaceId, ticketId, EMPLOYE,
                Map.of("denomination", Map.of("denomination", "ATLAS CONSEIL", "formeJuridique", "SARL_AU"))));

        assertThat(cree).isNotNull();
        assertThat(jdbc.queryForObject("SELECT raison_sociale FROM entreprise_dossiers WHERE id = ? AND workspace_id = ?",
                String.class, cree, workspaceId)).isEqualTo("ATLAS CONSEIL");
    }

    // ---- Lot L3 : magasin initialise depuis la fiche (FICHE), extraction (EXTRAITE) ----

    private String origine(String variable) {
        return dansLeWorkspace(() -> magasin.lire(workspaceId, ticketId)).stream()
                .filter(v -> v.variable().equals(variable) && v.boucle() == null)
                .map(v -> v.valeur() + "/" + v.origine()).findFirst().orElse("absente");
    }

    /** Etape 1 valide de l'approbation des comptes (societe, exercice clos, date de l'AGO). */
    private Map<String, Object> etape1PvAgo() {
        java.time.LocalDate jour = java.time.LocalDate.now();
        return Map.of("dossierId", dossierId.toString(),
                "exerciceClos", String.valueOf(jour.getYear() - 1),
                "dateAGO", jour.minusDays(10).toString());
    }

    @Test
    void l3_la_fiche_alimente_le_magasin_a_chaque_etape_sans_recouvrir_la_saisie() {
        jdbc.update("UPDATE tickets SET type = 'PV_AGO', dossier_id = ? WHERE id = ?", dossierId, ticketId);
        jdbc.update("UPDATE entreprise_dossiers SET ice = '001234567000089', taxe_professionnelle = 'TP-77' WHERE id = ?",
                dossierId);
        dansLeWorkspace(() -> workflows.startOrResume(workspaceId, ticketId, WorkflowType.PV_AGO, EMPLOYE));
        // Une donnee saisie au ticket n'est jamais recouverte par la fiche.
        dansLeWorkspace(() -> {
            magasin.poser(workspaceId, ticketId, "RC_NUMERO", "999999", VariableDuDossier.Origine.SAISIE,
                    EMPLOYE, "etape-1");
            return null;
        });

        dansLeWorkspace(() -> workflows.executeStep(workspaceId, ticketId, 1, etape1PvAgo(), EMPLOYE));

        assertThat(origine("DENOMINATION")).isEqualTo("NOVA INDUSTRIE/FICHE");
        assertThat(origine("ICE")).isEqualTo("001234567000089/FICHE");
        assertThat(origine("IDENTIFIANT_TP")).isEqualTo("TP-77/FICHE");
        assertThat(origine("TRIBUNAL_VILLE")).isEqualTo("CASABLANCA/FICHE");
        assertThat(origine("RC_NUMERO")).isEqualTo("999999/SAISIE");

        // Une correction de la fiche se repercute (RG-VAR-05).
        jdbc.update("UPDATE entreprise_dossiers SET raison_sociale = 'NOVA INDUSTRIE MAROC' WHERE id = ?", dossierId);
        dansLeWorkspace(() -> workflows.executeStep(workspaceId, ticketId, 1, etape1PvAgo(), EMPLOYE));
        assertThat(origine("DENOMINATION")).isEqualTo("NOVA INDUSTRIE MAROC/FICHE");
    }

    @Test
    void l3_valeur_extraite_et_confirmee_marquee_extraite_et_decocher_n_efface_rien() {
        Map<String, Object> gerant = new java.util.LinkedHashMap<>();
        gerant.put("nom", "BENALI");
        gerant.put("prenom", "Karim");
        gerant.put("adresse", "12 rue de Fes, Rabat");
        gerant.put("_extraits", List.of("nom", "prenom"));
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("step1", Map.of("denomination", "NOVA", "cnNumero", "CN-2026-1", "_extraits", List.of("cnNumero")));
        data.put("step5", Map.of("dirigeants", List.of(gerant)));
        // Le contrat de bail a ete decoche : sa donnee saisie reste au magasin (RG-VAR-04).
        data.put("step7", Map.of("complements", Map.of("bailleurNom", "IMMO ATLAS"), "lignesRetenues", List.of(1)));

        dansLeWorkspace(() -> projecteur.projeter(workspaceId, ticketId, data, EMPLOYE));

        List<VariableDuDossier> lues = dansLeWorkspace(() -> magasin.lire(workspaceId, ticketId));
        assertThat(lues).extracting(v -> v.boucle() + ":" + v.variable() + "=" + v.origine())
                .contains("GERANTS:GERANT_NOM=EXTRAITE", "GERANTS:GERANT_PRENOM=EXTRAITE",
                        "GERANTS:GERANT_ADRESSE=SAISIE", "null:CERTIFICAT_NEGATIF_NUMERO=EXTRAITE",
                        "null:DENOMINATION=SAISIE", "null:BAILLEUR_NOM=SAISIE");
        assertThat(lues).filteredOn(v -> "EXTRAITE".equals(String.valueOf(v.origine())))
                .allSatisfy(v -> assertThat(v.saisiePar()).as("confirmee par l'employe").isEqualTo(EMPLOYE));
    }

    @Test
    void l3_charge_utile_de_la_creation_construite_par_le_serveur_pour_l_employe_en_charge() throws Exception {
        dansLeWorkspace(() -> {
            magasin.poser(workspaceId, ticketId, "DENOMINATION", "NOVA INDUSTRIE", VariableDuDossier.Origine.SAISIE,
                    EMPLOYE, "etape-1");
            magasin.poser(workspaceId, ticketId, "DATE_SIGNATURE", "2026-10-01", VariableDuDossier.Origine.SAISIE,
                    EMPLOYE, "etape-7");
            return null;
        });
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/internal/tickets/{t}/charge-utile-creation", ticketId)
                        .param("workspaceId", workspaceId.toString()).param("employeId", EMPLOYE.toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.societe.denomination").value("NOVA INDUSTRIE"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.societe.dateSignature").value("2026-10-01"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.ticketId").value(ticketId.toString()));
        // Un autre employe du cabinet n'obtient pas les donnees d'un ticket qui n'est pas le sien.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/internal/tickets/{t}/charge-utile-creation", ticketId)
                        .param("workspaceId", workspaceId.toString()).param("employeId", UUID.randomUUID().toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void l3_donnee_externe_reclamee_puis_recue_puis_close_a_la_regeneration() {
        var rc = new ma.jurika.workflow.application.DonneesAttenduesService.Donnee("RC_NUMERO", "Numero du registre du commerce");
        var dl = new ma.jurika.workflow.application.DonneesAttenduesService.Donnee("DATE_DEPOT_LEGAL", "Date du depot legal");
        dansLeWorkspace(() -> {
            donneesAttendues.enregistrer(workspaceId, ticketId, "CREATION_SARL", "ANNONCE_LEGALE_CONSTITUTION", List.of(rc, dl));
            return null;
        });
        var attendues = dansLeWorkspace(() -> donneesAttendues.lister(workspaceId, ticketId));
        assertThat(attendues).extracting(a -> a.variable() + "=" + a.recue())
                .containsExactlyInAnyOrder("RC_NUMERO=false", "DATE_DEPOT_LEGAL=false");

        // La donnee arrive au magasin (saisie de l'employe) : elle est signalee recue.
        dansLeWorkspace(() -> {
            magasin.poser(workspaceId, ticketId, "RC_NUMERO", "654321", VariableDuDossier.Origine.SAISIE, EMPLOYE, "etape-9");
            return null;
        });
        assertThat(dansLeWorkspace(() -> donneesAttendues.lister(workspaceId, ticketId)))
                .extracting(a -> a.variable() + "=" + a.recue()).contains("RC_NUMERO=true", "DATE_DEPOT_LEGAL=false");

        // Le document est regenere : il n'attend plus que la date du depot ; le RC est clos.
        dansLeWorkspace(() -> {
            donneesAttendues.enregistrer(workspaceId, ticketId, "CREATION_SARL", "ANNONCE_LEGALE_CONSTITUTION", List.of(dl));
            return null;
        });
        assertThat(dansLeWorkspace(() -> donneesAttendues.lister(workspaceId, ticketId)))
                .extracting(a -> a.variable()).containsExactly("DATE_DEPOT_LEGAL");
    }

    @Test
    void l3_une_donnee_de_la_fiche_societe_compte_comme_recue() throws Exception {
        jdbc.update("UPDATE tickets SET dossier_id = ? WHERE id = ?", dossierId, ticketId);
        var ice = new ma.jurika.workflow.application.DonneesAttenduesService.Donnee("ICE", "ICE");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/internal/tickets/{t}/donnees-attendues", ticketId).param("workspaceId", workspaceId.toString())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"workflowCode\":\"PV_AGO\",\"templateCode\":\"PV_X\",\"donnees\":[{\"variable\":\"ICE\",\"libelle\":\"ICE\"}]}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertThat(dansLeWorkspace(() -> donneesAttendues.lister(workspaceId, ticketId)).get(0).recue()).isFalse();
        jdbc.update("UPDATE entreprise_dossiers SET ice = '001234567000089' WHERE id = ?", dossierId);
        assertThat(dansLeWorkspace(() -> donneesAttendues.lister(workspaceId, ticketId)).get(0).recue()).isTrue();
    }

    // ---- Lot L3 (RG-GEN-05/06) : clauses libres au magasin, tracees ----

    private static ma.jurika.workflow.application.ClausesLibresService.Clause clause(String titre, String texte) {
        return new ma.jurika.workflow.application.ClausesLibresService.Clause("PV_APPROBATION_COMPTES_SARL",
                "A_LA_SUITE_DES_RESOLUTIONS", titre, texte, "adoptée", "1000", "0", "0", null, null);
    }

    @Test
    void l3_clauses_libres_au_magasin_avec_auteur_et_date_conservee_si_inchangee() throws Exception {
        var premiere = dansLeWorkspace(() -> clausesLibres.remplacer(workspaceId, ticketId, EMPLOYE,
                List.of(clause("Pouvoirs particuliers", "L'assemblee confere tous pouvoirs a M. X."),
                        clause("Remerciements", "L'assemblee remercie la gerance."))));
        assertThat(premiere).extracting(c -> c.titre()).containsExactly("Pouvoirs particuliers", "Remerciements");
        assertThat(premiere).allSatisfy(c -> assertThat(c.saisiePar()).isEqualTo(EMPLOYE));
        java.time.Instant date1 = premiere.get(0).saisieLe();
        Thread.sleep(20);

        var seconde = dansLeWorkspace(() -> clausesLibres.remplacer(workspaceId, ticketId, EMPLOYE,
                List.of(clause("Pouvoirs particuliers", "L'assemblee confere tous pouvoirs a M. X."))));
        assertThat(seconde).hasSize(1);
        assertThat(seconde.get(0).saisieLe().truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
                .as("clause inchangee : date d'origine").isEqualTo(date1.truncatedTo(java.time.temporal.ChronoUnit.MILLIS));

        // Lue telle quelle par ai-service a chaque generation (reutilisee a la regeneration).
        mvc.perform(get("/internal/tickets/{t}/clauses-libres", ticketId).param("workspaceId", workspaceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].titre").value("Pouvoirs particuliers"))
                .andExpect(jsonPath("$[0].texte").value("L'assemblee confere tous pouvoirs a M. X."));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> dansLeWorkspace(() -> clausesLibres.remplacer(
                        workspaceId, ticketId, EMPLOYE, List.of(clause(" ", "texte")))))
                .hasMessageContaining("titre");
    }
}
