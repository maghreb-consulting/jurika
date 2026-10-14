package ma.jurika.billing.integration;

import ma.jurika.billing.api.dto.BillingDtos.ChangementForfaitRequest;
import ma.jurika.billing.api.dto.BillingDtos.ChangementForfaitVue;
import ma.jurika.billing.api.dto.BillingDtos.EnregistrementPaiementRequest;
import ma.jurika.billing.api.dto.BillingDtos.PaiementVue;
import ma.jurika.billing.api.dto.BillingDtos.PreparationPaiementRequest;
import ma.jurika.billing.api.dto.BillingDtos.PreparationPaiementVue;
import ma.jurika.billing.api.dto.BillingDtos.ValidationPaiementRequest;
import ma.jurika.billing.application.AdministrationFacturationService;
import ma.jurika.billing.application.RefusNommeException;
import ma.jurika.billing.application.cabinet.CabinetsPort;
import ma.jurika.billing.application.contrat.ContratAbonnementPort;
import ma.jurika.billing.application.paiement.PaiementService;
import ma.jurika.billing.infrastructure.cmi.CmiSimulateurController;
import ma.jurika.billing.infrastructure.cmi.CmiSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lot L7 (E5) : parcours de facturation sur une vraie base (migrations billing V1 a V8).
 *
 * <ul>
 *   <li>RG-PAY-04, RG-PAY-05 : carte par le SIMULATEUR du CMI de bout en bout (formulaire signe,
 *       retour serveur signe envoye au callback) ; meme confirmation deux fois = un paiement, une
 *       facture ; signature ou montant faux : rien ; retour du navigateur seul : rien.</li>
 *   <li>RG-PAY-02 : virement, cheque, especes enregistres par le super-admin.</li>
 *   <li>RG-PAY-03 : facture a chaque paiement, refusee (mention nommee) si l'editeur est incomplet,
 *       numerotation sans rupture sous concurrence.</li>
 *   <li>RG-TAR-04, RG-TAR-05 : hausse annuelle au prorata, baisse refusee si l'usage depasse.</li>
 *   <li>RG-TAR-03, RG-TAR-07, RG-TAR-08 : echeance impayee archivee, reprise par une nouvelle
 *       souscription.</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"})
@AutoConfigureMockMvc
@ActiveProfiles("it")
class FacturationParcoursIT {

    @Container
    static final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_billing");

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
    }

    /** Le simulateur poste le retour serveur au callback par MockMvc (meme chemin HTTP que le CMI). */
    static final AtomicReference<MockMvc> MVC = new AtomicReference<>();

    @TestConfiguration
    static class Emetteur {
        @Bean
        CmiSimulateurController.EmetteurRetour emetteurRetour() {
            return (url, params) -> {
                try {
                    MockHttpServletRequestBuilder req = post("/api/v1/billing/cmi/callback")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED);
                    params.forEach(req::param);
                    return MVC.get().perform(req).andReturn().getResponse().getContentAsString();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PaiementService paiements;
    @Autowired AdministrationFacturationService admin;

    @MockBean CabinetsPort cabinets;
    @MockBean ContratAbonnementPort contrat;

    static final UUID ADMIN = UUID.randomUUID();
    UUID ws;

    @BeforeEach
    void setUp() {
        MVC.set(mvc);
        reset(cabinets, contrat);
        jdbc.update("DELETE FROM cmi_retours");
        jdbc.update("DELETE FROM factures");
        jdbc.update("DELETE FROM factures_sequence");
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM subscriptions");
        editeurComplet();
        ws = UUID.randomUUID();
        when(cabinets.identite(any())).thenAnswer(inv -> new CabinetsPort.IdentiteCabinet(inv.getArgument(0),
                "Cabinet Atlas", "001234567000089", "1234567", "RC 98765", "12 rue des Juristes", "Rabat",
                "contact@atlas.invalid", "ACTIVE", "professionnel"));
        when(cabinets.usage(any())).thenReturn(usage(0, 0));
    }

    private void editeurComplet() {
        jdbc.update("""
                UPDATE editeur_parametres SET raison_sociale = 'Maghreb Consulting', forme_juridique = 'SARL',
                  siege_social = '1 avenue de test', ville = 'Casablanca', rc_numero = 'RC TEST 1', ice = '000000000000001',
                  identifiant_fiscal = 'IF TEST 1', taxe_professionnelle = 'TP TEST 1', taux_tva_pourcent = 20.00 WHERE id = 1
                """);
    }

    static CabinetsPort.UsageCabinet usage(int employes, int dossiers) {
        return new CabinetsPort.UsageCabinet("max", employes, 4, false, dossiers, 100, false, 0L, 25, false,
                LocalDate.now(), LocalDate.now().plusMonths(1), true);
    }

    private PreparationPaiementVue preparerCarte() {
        return paiements.preparer(ws, new PreparationPaiementRequest("SOUSCRIPTION", "professionnel", "monthly", "CARD",
                "contact@atlas.invalid", true));
    }

    private String callback(Map<String, String> params) throws Exception {
        MockHttpServletRequestBuilder req = post("/api/v1/billing/cmi/callback").contentType(MediaType.APPLICATION_FORM_URLENCODED);
        params.forEach(req::param);
        return mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String statut(Long id) {
        return jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, id);
    }

    private int factures() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM factures", Integer.class);
    }

    // ---------------------------------------------------------------- carte (CMI)

    @Test
    void carte_de_bout_en_bout_par_le_simulateur_du_cmi() throws Exception {
        PreparationPaiementVue prep = preparerCarte();
        assertThat(prep.cmi().url()).endsWith("/cmi-simulateur/passerelle");
        assertThat(prep.cmi().champs()).containsEntry("currency", "504").containsEntry("amount", "900.00")
                .containsKeys("clientid", "oid", "okUrl", "failUrl", "callbackUrl", "rnd", "hashAlgorithm", "HASH");

        MockHttpServletRequestBuilder page = post("/api/v1/billing/cmi-simulateur/passerelle")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED);
        prep.cmi().champs().forEach(page::param);
        String html = mvc.perform(page).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("aucun paiement r\u00e9el").contains("900.00");

        MockHttpServletRequestBuilder decision = post("/api/v1/billing/cmi-simulateur/decision")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("decision", "accepter");
        prep.cmi().champs().forEach(decision::param);
        String retour = mvc.perform(decision).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(retour).contains("ACTION=POSTAUTH").contains("/cmi/retour/ok");

        Long id = prep.paiement().id();
        assertThat(statut(id)).isEqualTo("COMPLETED");
        assertThat(factures()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT numero FROM factures", String.class))
                .isEqualTo("F" + LocalDate.now(ZoneId.of("Africa/Casablanca")).getYear() + "-000001");
        assertThat(jdbc.queryForObject("SELECT plan_code FROM subscriptions WHERE workspace_id = ? AND status = 'active'",
                String.class, ws)).isEqualTo("professionnel");
        verify(cabinets).activer(eq(ws), eq("professionnel"), any(), eq("monthly"), any());
        verify(cabinets).emettreIdentifiants(eq(ws), anyString());
        verify(contrat, atLeastOnce()).ouvrirConditionsParticulieres(any());
        assertThat(jdbc.queryForObject("SELECT activation_propagee FROM payments WHERE id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void la_meme_confirmation_recue_deux_fois_ne_produit_qu_un_paiement_et_une_facture() throws Exception {
        PreparationPaiementVue prep = preparerCarte();
        Map<String, String> retour = CmiSimulateurController.retourServeur(prep.cmi().champs(), true, "cle-de-test-du-magasin");

        assertThat(callback(retour)).isEqualTo("ACTION=POSTAUTH");
        assertThat(callback(retour)).isEqualTo("ACTION=POSTAUTH");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE status = 'COMPLETED'", Integer.class)).isEqualTo(1);
        assertThat(factures()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscriptions", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cmi_retours", Integer.class)).isEqualTo(2);
    }

    @Test
    void signature_invalide_rien_n_est_acquis() throws Exception {
        PreparationPaiementVue prep = preparerCarte();
        Map<String, String> retour = new java.util.LinkedHashMap<>(
                CmiSimulateurController.retourServeur(prep.cmi().champs(), true, "cle-de-test-du-magasin"));
        retour.put("HASH", CmiSpecification.signer(retour, "cle-d-un-fraudeur"));

        assertThat(callback(retour)).isEqualTo("FAILURE");
        assertThat(statut(prep.paiement().id())).isEqualTo("PENDING");
        assertThat(factures()).isZero();
    }

    @Test
    void montant_different_du_paiement_prepare_rien_n_est_acquis() throws Exception {
        PreparationPaiementVue prep = preparerCarte();
        Map<String, String> commande = new java.util.LinkedHashMap<>(prep.cmi().champs());
        commande.put("amount", "1.00");
        assertThat(callback(CmiSimulateurController.retourServeur(commande, true, "cle-de-test-du-magasin"))).isEqualTo("FAILURE");
        assertThat(statut(prep.paiement().id())).isEqualTo("PENDING");
    }

    @Test
    void le_retour_du_navigateur_ne_confirme_jamais_le_paiement() throws Exception {
        PreparationPaiementVue prep = preparerCarte();
        MvcResult r = mvc.perform(post("/api/v1/billing/cmi/retour/ok").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("oid", prep.cmi().champs().get("oid")).param("ProcReturnCode", "00").param("Response", "Approved"))
                .andExpect(status().isSeeOther()).andReturn();
        assertThat(r.getResponse().getHeader("Location"))
                .startsWith("https://app.test.invalid/app/billing/paiement/retour")
                .contains("paiement=" + prep.paiement().id());
        assertThat(statut(prep.paiement().id())).isEqualTo("PENDING");
        assertThat(factures()).isZero();
    }

    @Test
    void paiement_refuse_par_le_cmi_accuse_de_reception() throws Exception {
        PreparationPaiementVue prep = preparerCarte();
        assertThat(callback(CmiSimulateurController.retourServeur(prep.cmi().champs(), false, "cle-de-test-du-magasin")))
                .isEqualTo("APPROVED");
        assertThat(statut(prep.paiement().id())).isEqualTo("FAILED");
        verify(cabinets, never()).activer(any(), any(), any(), any(), any());
    }

    // ---------------------------------------------------------------- hors ligne (super-admin)

    @Test
    void le_super_admin_enregistre_un_virement_qui_active_le_cabinet() {
        LocalDate recu = LocalDate.now(ZoneId.of("Africa/Casablanca")).minusDays(2);
        PaiementVue p = paiements.enregistrer(new EnregistrementPaiementRequest(ws, "SOUSCRIPTION", "essentiel", "yearly",
                "BANK_TRANSFER", recu, "VIR-2026-0042", null, null), ADMIN);

        assertThat(p.statut()).isEqualTo("COMPLETED");
        assertThat(p.montants().htCentimes()).isEqualTo(3750_00);
        assertThat(p.montants().ttcCentimes()).isEqualTo(4500_00);
        assertThat(p.periodeDebut()).isEqualTo(recu);
        assertThat(p.periodeFin()).isEqualTo(recu.plusYears(1));
        assertThat(p.factureNumero()).isNotNull();
        verify(cabinets).activer(eq(ws), eq("essentiel"), eq(recu), eq("yearly"), eq(recu.plusYears(1)));
        verify(cabinets, never()).emettreIdentifiants(any(), any());
    }

    @Test
    void le_cabinet_ne_confirme_jamais_son_virement_le_super_admin_constate_la_reception() {
        PreparationPaiementVue prep = paiements.preparer(ws, new PreparationPaiementRequest("SOUSCRIPTION", "max", "monthly",
                "BANK_TRANSFER", null, false));
        assertThat(prep.paiement().statut()).isEqualTo("PENDING");
        assertThat(prep.instructions()).extracting(i -> i.libelle()).contains("B\u00e9n\u00e9ficiaire", "RIB");
        assertThat(prep.instructions()).anySatisfy(i -> assertThat(i.valeur()).contains("Maghreb Consulting"));
        assertThat(admin.abonnements()).isEmpty();
        assertThat(paiements.paiementsAEnregistrer()).hasSize(1);

        assertThatThrownBy(() -> paiements.valider(prep.paiement().id(), new ValidationPaiementRequest(null, "VIR-1", null), ADMIN))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Date de r\u00e9ception");
        PaiementVue v = paiements.valider(prep.paiement().id(), new ValidationPaiementRequest(LocalDate.now(), "VIR-1", null), ADMIN);
        assertThat(v.statut()).isEqualTo("COMPLETED");
        assertThat(factures()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- factures

    @Test
    void facture_refusee_si_une_mention_de_l_editeur_manque_puis_emise_quand_elle_est_saisie() {
        jdbc.update("UPDATE editeur_parametres SET ice = NULL, taxe_professionnelle = '' WHERE id = 1");
        PaiementVue p = paiements.enregistrer(new EnregistrementPaiementRequest(ws, "SOUSCRIPTION", "essentiel", "monthly",
                "CASH", LocalDate.now(), "RECU-7", null, null), ADMIN);

        assertThat(p.statut()).isEqualTo("COMPLETED");
        assertThat(p.factureNumero()).isNull();
        assertThat(p.factureRefus()).contains("ICE de l\u2019\u00e9diteur").contains("taxe professionnelle (TP)");
        assertThat(factures()).isZero();

        editeurComplet();
        assertThat(admin.emettreFacturesEnAttente().reussis()).isEqualTo(1);
        assertThat(factures()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT facture_refus FROM payments WHERE id = ?", String.class, p.id())).isNull();
        byte[] pdf = jdbc.queryForObject("SELECT pdf FROM factures", byte[].class);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void numerotation_sans_rupture_sous_concurrence() throws Exception {
        int n = 12;
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Callable<PaiementVue>> taches = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            UUID cabinet = UUID.randomUUID();
            taches.add(() -> paiements.enregistrer(new EnregistrementPaiementRequest(cabinet, "SOUSCRIPTION", "essentiel",
                    "monthly", "CHEQUE", LocalDate.now(), "CHQ-" + cabinet, null, null), ADMIN));
        }
        List<String> numeros = Collections.synchronizedList(new ArrayList<>());
        for (Future<PaiementVue> f : pool.invokeAll(taches)) numeros.add(f.get().factureNumero());
        pool.shutdown();

        int annee = LocalDate.now(ZoneId.of("Africa/Casablanca")).getYear();
        List<String> attendus = new ArrayList<>();
        for (int i = 1; i <= n; i++) attendus.add(String.format("F%d-%06d", annee, i));
        assertThat(numeros).containsExactlyInAnyOrderElementsOf(attendus);
        assertThat(jdbc.queryForObject("SELECT dernier_numero FROM factures_sequence WHERE annee = ?", Integer.class, annee))
                .isEqualTo(n);
    }

    // ---------------------------------------------------------------- changement de forfait et archivage

    @Test
    void baisse_refusee_si_l_usage_depasse_le_forfait_vise_avec_ce_qui_depasse() {
        paiements.enregistrer(new EnregistrementPaiementRequest(ws, "SOUSCRIPTION", "max", "monthly", "CASH",
                LocalDate.now(), "R-1", null, null), ADMIN);
        when(cabinets.usage(ws)).thenReturn(usage(3, 30));

        assertThatThrownBy(() -> paiements.changer(ws, ADMIN, new ChangementForfaitRequest("essentiel", null, null)))
                .isInstanceOfSatisfying(RefusNommeException.class, e -> assertThat(e.elements())
                        .anySatisfy(x -> assertThat(x).contains("3 acc\u00e8s employ\u00e9s actifs pour 1"))
                        .anySatisfy(x -> assertThat(x).contains("30 dossiers ouverts ce mois-ci pour 25")));

        when(cabinets.usage(ws)).thenReturn(usage(1, 10));
        ChangementForfaitVue ok = paiements.changer(ws, ADMIN, new ChangementForfaitRequest("essentiel", null, null));
        assertThat(ok.resultat()).isEqualTo("PROGRAMME");
        assertThat(jdbc.queryForObject("SELECT plan_code || '>' || plan_a_venir FROM subscriptions WHERE workspace_id = ?",
                String.class, ws)).isEqualTo("max>essentiel");
    }

    @Test
    void hausse_en_annuel_au_prorata_effective_a_la_confirmation_du_paiement() {
        paiements.enregistrer(new EnregistrementPaiementRequest(ws, "SOUSCRIPTION", "essentiel", "yearly", "CASH",
                LocalDate.now(ZoneId.of("Africa/Casablanca")), "R-1", null, null), ADMIN);

        ChangementForfaitVue c = paiements.changer(ws, ADMIN, new ChangementForfaitRequest("professionnel", null, "BANK_TRANSFER"));
        assertThat(c.resultat()).isEqualTo("PAIEMENT_REQUIS");
        assertThat(c.apercu().moisRestants()).isEqualTo(12);
        assertThat(c.paiement().paiement().montants().htCentimes()).isEqualTo(3750_00);
        assertThat(jdbc.queryForObject("SELECT plan_code FROM subscriptions WHERE workspace_id = ?", String.class, ws))
                .isEqualTo("essentiel");

        paiements.valider(c.paiement().paiement().id(), new ValidationPaiementRequest(LocalDate.now(), "VIR-9", null), ADMIN);
        assertThat(jdbc.queryForObject("SELECT plan_code FROM subscriptions WHERE workspace_id = ?", String.class, ws))
                .isEqualTo("professionnel");
        assertThat(factures()).isEqualTo(2);
    }

    @Test
    void echeance_impayee_archivee_puis_reprise_par_une_nouvelle_souscription() {
        LocalDate debut = LocalDate.now(ZoneId.of("Africa/Casablanca")).minusMonths(1);
        paiements.enregistrer(new EnregistrementPaiementRequest(ws, "SOUSCRIPTION", "essentiel", "monthly", "CASH",
                debut, "R-1", null, null), ADMIN);

        assertThat(admin.archiver().reussis()).isEqualTo(1);
        verify(cabinets).archiver(ws, "IMPAYE");
        assertThat(jdbc.queryForObject("SELECT status FROM subscriptions WHERE workspace_id = ?", String.class, ws)).isEqualTo("unpaid");

        paiements.enregistrer(new EnregistrementPaiementRequest(ws, "SOUSCRIPTION", "professionnel", "monthly", "CASH",
                LocalDate.now(), "R-2", null, null), ADMIN);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscriptions WHERE workspace_id = ?", Integer.class, ws)).isEqualTo(2);
        verify(cabinets).activer(eq(ws), eq("professionnel"), any(), eq("monthly"), any());
    }

    @Test
    void sur_devis_montant_a_decider_presente_au_super_admin() {
        paiements.enregistrer(new EnregistrementPaiementRequest(ws, "SOUSCRIPTION", "sur_devis", "monthly", "BANK_TRANSFER",
                LocalDate.now(), "VIR-SD", 2500_00L, null), ADMIN);
        when(cabinets.usage(ws)).thenReturn(usage(6, 140));
        var vue = admin.abonnements().get(0);
        assertThat(vue.forfait()).isEqualTo("sur_devis");
        assertThat(vue.surDevis().dossiersDuMois()).isEqualTo(140);
        assertThat(vue.surDevis().montantHt()).isNull();
        assertThat(vue.surDevis().motif()).contains("d\u00e9cider");
        assertThat(vue.prochaineEcheance().montants().htCentimes()).isEqualTo(2500_00);
    }
}
