package ma.jurika.auth.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.auth.application.essai.ConditionsEssai;
import ma.jurika.auth.application.essai.CycleEssaiService;
import ma.jurika.auth.application.essai.EtatEssai;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.EventPublisher;
import ma.jurika.auth.domain.port.PurgeObjetsEssai;
import ma.jurika.auth.domain.port.SmsSender;
import ma.jurika.auth.infrastructure.scheduling.CycleEssaiJob;
import ma.jurika.common.security.TenantContext;
import ma.jurika.common.validation.DnsMxValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lot L7 (E4) : essai gratuit de bout en bout, en role d'execution jurika_app
 * (RLS active). Inscription (RG-ESS-01, RG-ESS-02, RG-CTR-10), signalement
 * (RG-ESS-03), cycle (RG-ESS-10), prolongation et fin (RG-ESS-11),
 * suppression definitive et ses garde-fous.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
                "jurika.ratelimit.signup-cabinet.max-per-hour=1000",
                "jurika.essai.cycle.cron=-"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("it")
class EssaiGratuitIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_essai")
            .withUsername("jurika_it")
            .withPassword("jurika_it_pwd")
            .withInitScript("testcontainers-init.sql")
            .withReuse(false);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "jurika_app");
        registry.add("spring.datasource.password", () -> "jurika_app_it");
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "16379");
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        public TaskExecutor taskExecutor() {
            return new SyncTaskExecutor();
        }

        @Bean
        public ConnectionFactory rabbitConnectionFactory() {
            return mock(ConnectionFactory.class);
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private CycleEssaiService cycle;
    @Autowired private CycleEssaiJob job;
    @Autowired private ma.jurika.auth.application.InviteEmployeUseCase inviter;

    /** Preparation et assertions en PROPRIETAIRE, hors RLS. */
    private final JdbcTemplate jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

    @MockBean private EmailSender emailSender;
    @MockBean private SmsSender smsSender;
    @MockBean private EventPublisher eventPublisher;
    @MockBean private DnsMxValidator dnsMxValidator;
    @MockBean private PurgeObjetsEssai purgeObjets;

    private static final AtomicInteger COMPTEUR = new AtomicInteger(10);

    @BeforeEach
    void preparer() {
        reset(emailSender, smsSender, eventPublisher, dnsMxValidator, purgeObjets);
        when(dnsMxValidator.validate(anyString())).thenReturn(new DnsMxValidator.Result(true, "OK"));
        TenantContext.clear();
    }

    @AfterEach
    void nettoyer() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------- outils

    record Identite(String email, String telephone) {}

    static Identite nouvelleIdentite() {
        int n = COMPTEUR.incrementAndGet();
        return new Identite("essai" + n + "@cabinet-essai.ma", "+2126610000" + String.format("%02d", n));
    }

    private String corpsDemande(Identite id, boolean conditions) {
        return """
                {"nom":"Benali","prenom":"Karim","organisation":"Cabinet Benali",
                 "telephone":"%s","email":"%s","conditionsAcceptees":%s,"conditionsVersion":"%s"}
                """.formatted(id.telephone(), id.email(), conditions, ConditionsEssai.VERSION);
    }

    private UUID demander(Identite id, String ip, String appareil) throws Exception {
        var requete = post("/api/v1/public/essai/demandes").contentType("application/json")
                .header("X-Forwarded-For", ip).content(corpsDemande(id, true));
        if (appareil != null) requete = requete.header("X-Jurika-Appareil", appareil);
        MvcResult r = mvc.perform(requete)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.smsEnvoye").value(true))
                .andExpect(jsonPath("$.emailEnvoye").value(true))
                .andReturn();
        return UUID.fromString(json.readTree(r.getResponse().getContentAsString()).get("demandeId").asText());
    }

    private String dernierCodeSms(String telephone) {
        ArgumentCaptor<String> corps = ArgumentCaptor.forClass(String.class);
        verify(smsSender, org.mockito.Mockito.atLeastOnce()).send(eq(telephone), corps.capture());
        return corps.getValue().replaceAll("\\D*(\\d{6}).*", "$1");
    }

    @SuppressWarnings("unchecked")
    private String dernierJeton() {
        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(emailSender, org.mockito.Mockito.atLeastOnce())
                .sendTemplated(anyString(), anyString(), eq("essai-confirmation"), vars.capture());
        String url = (String) vars.getValue().get("verificationUrl");
        return url.substring(url.indexOf("jeton=") + "jeton=".length());
    }

    /** Ouvre un essai complet ; renvoie l'identifiant du workspace. */
    private UUID ouvrirEssai(Identite id, String ip, String appareil) throws Exception {
        UUID demande = demander(id, ip, appareil);
        mvc.perform(post("/api/v1/public/essai/demandes/" + demande + "/telephone").contentType("application/json")
                        .content("{\"code\":\"" + dernierCodeSms(id.telephone()) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telephoneVerifie").value(true))
                .andExpect(jsonPath("$.essaiCree").value(false));
        MvcResult r = mvc.perform(post("/api/v1/public/essai/confirmer-adresse").contentType("application/json")
                        .content("{\"jeton\":\"" + dernierJeton() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.essaiCree").value(true))
                .andReturn();
        JsonNode etat = json.readTree(r.getResponse().getContentAsString());
        assertThat(etat.get("workspaceCode").asText()).matches("^JUR-[A-Z0-9]{5}$");
        return jdbc.queryForObject("SELECT id FROM workspaces WHERE code = ?", UUID.class,
                etat.get("workspaceCode").asText());
    }

    // -------------------------------------------------------------- tests

    @Test
    void conditions_de_l_essai_servies_avec_version_et_empreinte() throws Exception {
        mvc.perform(get("/api/v1/public/essai/conditions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(ConditionsEssai.VERSION))
                .andExpect(jsonPath("$.empreinte").value(org.hamcrest.Matchers.matchesPattern("^[0-9a-f]{64}$")))
                .andExpect(jsonPath("$.texte").value(org.hamcrest.Matchers.containsString("7 jours")));
    }

    @Test
    void inscription_complete_cree_un_essai_de_7_jours_et_trace_l_acceptation() throws Exception {
        Identite id = nouvelleIdentite();
        Instant avant = Instant.now();
        UUID ws = ouvrirEssai(id, "10.1.1.1", "appareil-0001-unique");

        Map<String, Object> w = jdbc.queryForMap("""
                SELECT trial_status, trial_started_at, trial_ends_at, status, ice FROM workspaces WHERE id = ?
                """, ws);
        assertThat(w.get("trial_status")).isEqualTo("TRIAL_ACTIVE");
        assertThat(w.get("status")).isEqualTo("ACTIVE");
        assertThat(w.get("ice")).isNull();
        Instant debut = ((Timestamp) w.get("trial_started_at")).toInstant();
        Instant fin = ((Timestamp) w.get("trial_ends_at")).toInstant();
        assertThat(Duration.between(debut, fin)).isEqualTo(Duration.ofDays(7));
        assertThat(debut).isAfterOrEqualTo(avant.minusSeconds(1));

        Map<String, Object> u = jdbc.queryForMap("""
                SELECT role, first_name, last_name, contact_email, phone, email_verified_at, phone_verified_at
                FROM users WHERE workspace_id = ?
                """, ws);
        assertThat(u.get("role")).isEqualTo("SUPERVISEUR");
        assertThat(u.get("contact_email")).isEqualTo(id.email());
        assertThat(u.get("phone")).isEqualTo(id.telephone());
        assertThat(u.get("email_verified_at")).isNotNull();
        assertThat(u.get("phone_verified_at")).isNotNull();

        Map<String, Object> acc = jdbc.queryForMap("""
                SELECT version, empreinte, texte, adresse_ip, acceptees_le FROM essai_conditions_acceptations
                WHERE workspace_id = ?
                """, ws);
        assertThat(acc.get("version")).isEqualTo(ConditionsEssai.VERSION);
        assertThat(acc.get("adresse_ip")).isEqualTo("10.1.1.1");
        assertThat(acc.get("acceptees_le")).isNotNull();
        assertThat(acc.get("empreinte")).isEqualTo(ConditionsEssai.sha256((String) acc.get("texte")));

        Map<String, Object> reg = jdbc.queryForMap(
                "SELECT email_empreinte, signalement FROM essais_registre WHERE workspace_id = ?", ws);
        assertThat((String) reg.get("email_empreinte")).hasSize(64).doesNotContain("@");
        // Identifiants envoyes par courriel (welcome) a l'adresse verifiee.
        verify(emailSender).sendTemplated(eq(id.email()), anyString(), eq("welcome"), anyMap());
    }

    @Test
    void refus_sans_conditions_acceptees_et_adresse_jetable() throws Exception {
        Identite id = nouvelleIdentite();
        mvc.perform(post("/api/v1/public/essai/demandes").contentType("application/json")
                        .content(corpsDemande(id, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("conditions")));
        Identite jetable = new Identite("karim@yopmail.com", id.telephone());
        mvc.perform(post("/api/v1/public/essai/demandes").contentType("application/json")
                        .content(corpsDemande(jetable, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("jetables")));
        verify(smsSender, never()).send(anyString(), anyString());
    }

    @Test
    void adresse_ou_telephone_deja_utilises_refuses_avec_invitation_a_se_connecter() throws Exception {
        Identite id = nouvelleIdentite();
        ouvrirEssai(id, "10.2.2.2", null);
        Identite memeAdresse = new Identite(id.email(), nouvelleIdentite().telephone());
        mvc.perform(post("/api/v1/public/essai/demandes").contentType("application/json")
                        .content(corpsDemande(memeAdresse, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("connectez-vous")));
        Identite memeTelephone = new Identite(nouvelleIdentite().email(), id.telephone());
        mvc.perform(post("/api/v1/public/essai/demandes").contentType("application/json")
                        .content(corpsDemande(memeTelephone, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("contactez JURIKA")));
        // Adresse d'un compte existant (seed de demonstration, karim@jurika.ma).
        Identite compte = new Identite("karim@jurika.ma", nouvelleIdentite().telephone());
        mvc.perform(post("/api/v1/public/essai/demandes").contentType("application/json")
                        .content(corpsDemande(compte, true)))
                .andExpect(status().isConflict());
    }

    @Test
    void code_sms_incorrect_refuse_et_essai_non_cree() throws Exception {
        Identite id = nouvelleIdentite();
        UUID demande = demander(id, "10.3.3.3", null);
        mvc.perform(post("/api/v1/public/essai/demandes/" + demande + "/telephone").contentType("application/json")
                        .content("{\"code\":\"000000x\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/public/essai/confirmer-adresse").contentType("application/json")
                        .content("{\"jeton\":\"" + dernierJeton() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerifie").value(true))
                .andExpect(jsonPath("$.essaiCree").value(false));
        Integer tentatives = jdbc.queryForObject(
                "SELECT code_sms_tentatives FROM essai_demandes WHERE id = ?", Integer.class, demande);
        assertThat(tentatives).isEqualTo(1);
    }

    @Test
    void meme_reseau_et_meme_appareil_signales_jamais_bloques() throws Exception {
        UUID premier = ouvrirEssai(nouvelleIdentite(), "10.9.9.9", "appareil-commun-01");
        UUID second = ouvrirEssai(nouvelleIdentite(), "10.9.9.9", "appareil-commun-01");
        assertThat(jdbc.queryForObject("SELECT signalement FROM essais_registre WHERE workspace_id = ?",
                String.class, premier)).isNull();
        String signalement = jdbc.queryForObject("SELECT signalement FROM essais_registre WHERE workspace_id = ?",
                String.class, second);
        assertThat(signalement).contains("r\u00e9seau").contains("appareil");
        assertThat(jdbc.queryForObject("SELECT trial_status FROM workspaces WHERE id = ?", String.class, second))
                .isEqualTo("TRIAL_ACTIVE");
    }

    @Test
    void cycle_lecture_seule_avertissement_puis_suppression_definitive() throws Exception {
        UUID ws = ouvrirEssai(nouvelleIdentite(), "10.4.4.4", null);
        jdbc.update("UPDATE workspaces SET trial_ends_at = now() - interval '1 hour' WHERE id = ?", ws);

        reset(emailSender);
        CycleEssaiJob.Bilan b1 = job.executerUnPassage();
        assertThat(b1.erreurs()).isZero();
        Map<String, Object> w = jdbc.queryForMap(
                "SELECT trial_status, essai_expire_le, essai_avertissement_le FROM workspaces WHERE id = ?", ws);
        assertThat(w.get("trial_status")).isEqualTo("TRIAL_EXPIRED");
        assertThat(w.get("essai_expire_le")).isNotNull();
        assertThat(w.get("essai_avertissement_le")).isNotNull();
        verify(emailSender).sendTemplated(anyString(), anyString(), eq("essai-avertissement-suppression"), anyMap());
        verify(purgeObjets, never()).purger(ws);

        TenantContext.set(ws);
        EtatEssai etat = cycle.etat(ws);
        TenantContext.clear();
        assertThat(etat.lectureSeule()).isTrue();
        assertThat(etat.suppressionLe()).isEqualTo(etat.lectureSeuleDepuis().plus(Duration.ofDays(30)));

        // Au terme des 30 jours : objets puis lignes supprimes.
        jdbc.update("UPDATE workspaces SET essai_expire_le = now() - interval '31 days' WHERE id = ?", ws);
        when(purgeObjets.purger(ws)).thenReturn(4);
        CycleEssaiJob.Bilan b2 = job.executerUnPassage();
        assertThat(b2.supprimes()).isGreaterThanOrEqualTo(1);
        verify(purgeObjets).purger(ws);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workspaces WHERE id = ?", Long.class, ws)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE workspace_id = ?", Long.class, ws)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM essai_conditions_acceptations WHERE workspace_id = ?",
                Long.class, ws)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE workspace_id = ?", Long.class, ws))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT supprime_le FROM essais_registre WHERE workspace_id = ?",
                Timestamp.class, ws)).isNotNull();
        // Trace : les tables absentes de ce schema (auth seul) sont nommees, jamais comptees 0.
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_log WHERE action = 'ESSAI_SUPPRIME_DEFINITIVEMENT'
                   AND entity_id = ? AND metadata -> 'tablesAbsentes' @> '["tickets"]'::jsonb
                """, Long.class, ws)).isOne();
    }

    @Test
    void suppression_impossible_si_purge_minio_echoue() throws Exception {
        UUID ws = ouvrirEssai(nouvelleIdentite(), "10.5.5.5", null);
        jdbc.update("""
                UPDATE workspaces SET trial_status = 'TRIAL_EXPIRED', essai_expire_le = now() - interval '31 days',
                       essai_avertissement_le = now() - interval '31 days' WHERE id = ?
                """, ws);
        when(purgeObjets.purger(ws)).thenThrow(new IllegalStateException("dataroom injoignable"));
        CycleEssaiJob.Bilan b = job.executerUnPassage();
        assertThat(b.erreurs()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workspaces WHERE id = ?", Long.class, ws)).isOne();
    }

    @Test
    void garde_fous_de_la_fonction_de_suppression() throws Exception {
        UUID ws = ouvrirEssai(nouvelleIdentite(), "10.6.6.6", null);
        // Essai en cours : refuse.
        assertThatThrownBy(() -> jdbc.queryForList("SELECT * FROM essai_supprimer_definitivement(?)", ws))
                .hasMessageContaining("lecture seule");
        // Expire depuis 31 jours mais jamais averti : refuse.
        jdbc.update("""
                UPDATE workspaces SET trial_status = 'TRIAL_EXPIRED', essai_expire_le = now() - interval '31 days'
                WHERE id = ?
                """, ws);
        assertThatThrownBy(() -> jdbc.queryForList("SELECT * FROM essai_supprimer_definitivement(?)", ws))
                .hasMessageContaining("averti");
        // Averti mais lecture seule depuis 10 jours seulement : refuse.
        jdbc.update("""
                UPDATE workspaces SET essai_avertissement_le = now() - interval '10 days',
                       essai_expire_le = now() - interval '10 days' WHERE id = ?
                """, ws);
        assertThatThrownBy(() -> jdbc.queryForList("SELECT * FROM essai_supprimer_definitivement(?)", ws))
                .hasMessageContaining("30 jours");
        // Abonne (meme resilie) : jamais supprime.
        jdbc.update("""
                UPDATE workspaces SET essai_expire_le = now() - interval '40 days',
                       subscription_status = 'cancelled' WHERE id = ?
                """, ws);
        assertThatThrownBy(() -> jdbc.queryForList("SELECT * FROM essai_supprimer_definitivement(?)", ws))
                .hasMessageContaining("abonnement");
        // Cabinet qui n'est pas un essai (seed de demonstration) : jamais supprime.
        UUID demo = UUID.fromString("11111111-1111-1111-1111-111111111111");
        jdbc.update("""
                UPDATE workspaces SET trial_status = 'TRIAL_EXPIRED', essai_expire_le = now() - interval '40 days',
                       essai_avertissement_le = now() - interval '40 days' WHERE id = ?
                """, demo);
        try {
            assertThatThrownBy(() -> jdbc.queryForList("SELECT * FROM essai_supprimer_definitivement(?)", demo))
                    .hasMessageContaining("n'est pas un essai gratuit");
        } finally {
            jdbc.update("""
                    UPDATE workspaces SET trial_status = NULL, essai_expire_le = NULL, essai_avertissement_le = NULL
                    WHERE id = ?
                    """, demo);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workspaces WHERE id IN (?, ?)", Long.class, ws, demo))
                .isEqualTo(2L);
    }

    @Test
    void suppression_sur_schema_d_auth_seul_rapporte_les_tables_absentes() throws Exception {
        // V45 : liste explicite. Ce contexte ne migre que le schema d'auth : les
        // tables des autres services sont rendues avec lignes = NULL (absentes),
        // jamais comptees 0. La couverture complete est gardee par
        // SuppressionEssaiCouvertureIT (schema complet de jurika_db).
        UUID ws = ouvrirEssai(nouvelleIdentite(), "10.7.7.7", null);
        jdbc.update("""
                UPDATE workspaces SET trial_status = 'TRIAL_EXPIRED', essai_expire_le = now() - interval '31 days',
                       essai_avertissement_le = now() - interval '31 days' WHERE id = ?
                """, ws);
        var lignes = jdbc.queryForList("SELECT * FROM essai_supprimer_definitivement(?)", ws);
        assertThat(lignes).anySatisfy(l -> {
            assertThat(l.get("table_nom")).isEqualTo("tickets");
            assertThat(l.get("lignes")).isNull();
        });
        assertThat(lignes).anySatisfy(l -> {
            assertThat(l.get("table_nom")).isEqualTo("users");
            assertThat(l.get("lignes")).isEqualTo(1L);
        });
        assertThat(lignes.get(lignes.size() - 1)).containsEntry("table_nom", "workspaces").containsEntry("lignes", 1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workspaces WHERE id = ?", Long.class, ws)).isZero();
    }

    @Test
    void super_admin_prolonge_une_fois_et_met_fin() throws Exception {
        UUID ws = ouvrirEssai(nouvelleIdentite(), "10.8.8.8", null);
        Instant finInitiale = jdbc.queryForObject("SELECT trial_ends_at FROM workspaces WHERE id = ?",
                Timestamp.class, ws).toInstant();
        TenantContext.set(ws);
        EtatEssai prolonge = cycle.prolonger(ws, 5, null);
        assertThat(prolonge.fin()).isEqualTo(finInitiale.plus(Duration.ofDays(5)));
        assertThat(prolonge.prolongeable()).isFalse();
        assertThatThrownBy(() -> cycle.prolonger(ws, 3, null)).hasMessageContaining("qu\u2019une fois");
        assertThatThrownBy(() -> cycle.mettreFin(ws, " ", null)).hasMessageContaining("motif");
        EtatEssai fini = cycle.mettreFin(ws, "Usage abusif constat\u00e9", null);
        TenantContext.clear();
        assertThat(fini.lectureSeule()).isTrue();
        assertThat(jdbc.queryForObject("SELECT essai_fin_motif FROM workspaces WHERE id = ?", String.class, ws))
                .isEqualTo("Usage abusif constat\u00e9");
        verify(emailSender).sendTemplated(anyString(), anyString(), eq("essai-avertissement-suppression"), anyMap());
    }

    @Test
    void essai_limite_a_un_employe() throws Exception {
        UUID ws = ouvrirEssai(nouvelleIdentite(), "10.10.10.10", null);
        TenantContext.set(ws);
        inviter.execute(new ma.jurika.auth.application.InviteEmployeUseCase.Command(ws, "employe1@cabinet-essai.ma",
                "Salma", "Idrissi", null, ma.jurika.common.security.Role.EMPLOYE, null, null, null));
        TenantContext.set(ws);
        assertThatThrownBy(() -> inviter.execute(new ma.jurika.auth.application.InviteEmployeUseCase.Command(ws,
                "employe2@cabinet-essai.ma", "Omar", "Alami", null, ma.jurika.common.security.Role.EMPLOYE,
                null, null, null)))
                .isInstanceOf(ma.jurika.common.exception.PlanLimitException.class)
                .hasMessageContaining("1 employ\u00e9");
        TenantContext.clear();
    }


    @Test
    void statut_d_essai_herite_hors_registre_ignore_par_le_cycle() {
        // Cabinet de demonstration qui porterait un ancien statut d'essai (Sprint 11, avant UX-3).
        UUID demo = UUID.fromString("11111111-1111-1111-1111-111111111111");
        jdbc.update("UPDATE workspaces SET trial_status = 'TRIAL_ACTIVE', trial_ends_at = now() - interval '2 days' "
                + "WHERE id = ?", demo);
        try {
            job.executerUnPassage();
            assertThat(jdbc.queryForObject("SELECT trial_status FROM workspaces WHERE id = ?", String.class, demo))
                    .isEqualTo("TRIAL_ACTIVE");
            verify(emailSender, never()).sendTemplated(anyString(), anyString(),
                    eq("essai-avertissement-suppression"), anyMap());
        } finally {
            jdbc.update("UPDATE workspaces SET trial_status = NULL, trial_ends_at = NULL WHERE id = ?", demo);
        }
    }

}
