package ma.jurika.auth.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.EventPublisher;
import ma.jurika.auth.domain.port.SmsSender;
import ma.jurika.common.validation.DnsMxValidator;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
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
 * Recette R2 (2026-10-17) : double authentification par SMS.
 *
 * <p>Le fournisseur de SMS est ici un double (SmsSender simule : "fournisseur configure") ; le
 * cas "aucun fournisseur" est couvert par DisponibiliteSms2faTest et Sms2faIndisponibleTest.
 * Couvre :
 * <ol>
 *   <li>defaut trouve : choisir SMS (ou TOTP) sans rien verifier retirait la double
 *       authentification (connexion sans second facteur, configuration reputee faite) ;</li>
 *   <li>activation du SMS sur un numero saisi, verifie par un code, puis connexion par code
 *       SMS ; aucun code dans les reponses HTTP ;</li>
 *   <li>passage de l'application d'authentification au SMS : code de la methode actuelle exige,
 *       codes de recuperation conserves.</li>
 * </ol>
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("it")
class DoubleAuthentificationSmsIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_sms")
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

    /** Horloge maitrisee pour les codes TOTP (anti-rejeu : un pas ne sert qu'une fois). */
    static final HorlogeReglable HORLOGE = new HorlogeReglable(Instant.now());

    static final class HorlogeReglable extends Clock {
        private final AtomicReference<Instant> instant;
        HorlogeReglable(Instant depart) { this.instant = new AtomicReference<>(depart); }
        void avancer(Duration duree) { instant.updateAndGet(i -> i.plus(duree)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }

    @TestConfiguration
    static class Cablage {
        @Bean @Primary public TaskExecutor taskExecutor() { return new SyncTaskExecutor(); }
        @Bean @Primary public Clock horlogeTest() { return HORLOGE; }
        @Bean public ConnectionFactory rabbitConnectionFactory() { return mock(ConnectionFactory.class); }
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    private final JdbcTemplate jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

    @MockBean private EmailSender emailSender;
    @MockBean private SmsSender smsSender;
    @MockBean private EventPublisher eventPublisher;
    @MockBean private DnsMxValidator dnsMxValidator;

    private static final UUID SUBSCRIPTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String MOT_DE_PASSE = "NewStrong@2026!";

    @BeforeEach
    void init() {
        reset(emailSender, smsSender, eventPublisher, dnsMxValidator);
        when(dnsMxValidator.validate(anyString())).thenReturn(new DnsMxValidator.Result(true, "OK"));
    }

    // ------------------------------------------------------------------ 1

    @Test
    void choisir_sms_sans_verifier_le_numero_ne_retire_pas_la_double_authentification() throws Exception {
        Compte u = compteSansDoubleAuthentification("smsbypass1@example.com", "+212600000101");

        mvc.perform(post("/api/v1/auth/2fa/choose-method").header("Authorization", "Bearer " + u.jeton)
                        .contentType("application/json").content("{\"method\":\"SMS\"}"))
                .andExpect(status().isNoContent());

        // Aucun numero verifie : la configuration reste exigee, jamais une session sans second facteur.
        JsonNode l = connexion(u);
        assertThat(l.get("requires2fa").asBoolean()).isFalse();
        assertThat(l.get("requires2faSetup").asBoolean()).as("configuration toujours exigee").isTrue();
        assertThat(jdbc.queryForObject("SELECT twofa_method FROM users WHERE id = ?", String.class, u.userId))
                .as("le choix seul n'active aucune methode").isNull();
    }

    @Test
    void choisir_sms_apres_l_application_ne_retire_pas_le_code_de_l_application() throws Exception {
        Compte u = compteSansDoubleAuthentification("smsbypass2@example.com", "+212600000102");
        activerTotp(u);

        mvc.perform(post("/api/v1/auth/2fa/choose-method").header("Authorization", "Bearer " + u.jeton)
                        .contentType("application/json").content("{\"method\":\"SMS\"}"))
                .andExpect(status().isNoContent());

        JsonNode l = connexion(u);
        assertThat(l.get("requires2fa").asBoolean()).as("le code de l'application reste exige").isTrue();
        assertThat(l.get("twofaMethod").asText()).isEqualTo("TOTP");
    }

    // ------------------------------------------------------------------ 2

    @Test
    void activation_du_sms_sur_un_numero_saisi_puis_connexion_par_code_sms() throws Exception {
        Compte u = compteSansDoubleAuthentification("smsactiv1@example.com", "+212600000103");
        String numero = "+212611223344";

        mvc.perform(get("/api/v1/auth/2fa/setup-options").header("Authorization", "Bearer " + u.jeton))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smsAvailable").value(true))
                .andExpect(jsonPath("$.smsSimule").value(false))
                .andExpect(jsonPath("$.methodeActive").isEmpty());

        MvcResult envoi = mvc.perform(post("/api/v1/auth/2fa/sms/send").header("Authorization", "Bearer " + u.jeton)
                        .contentType("application/json")
                        .content("{\"purpose\":\"2FA_SETUP\",\"telephone\":\"" + numero + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedPhone").value(org.hamcrest.Matchers.endsWith("3344")))
                .andReturn();
        String codeActivation = dernierCode(numero);
        assertThat(envoi.getResponse().getContentAsString()).as("aucun code dans la reponse").doesNotContain(codeActivation);

        // Mauvais code : refuse, l'essai est compte.
        mvc.perform(post("/api/v1/auth/2fa/sms/verify").header("Authorization", "Bearer " + u.jeton)
                        .contentType("application/json")
                        .content("{\"purpose\":\"2FA_SETUP\",\"code\":\"" + autreCode(codeActivation) + "\"}"))
                .andExpect(status().isUnauthorized());

        MvcResult verif = mvc.perform(post("/api/v1/auth/2fa/sms/verify").header("Authorization", "Bearer " + u.jeton)
                        .contentType("application/json")
                        .content("{\"purpose\":\"2FA_SETUP\",\"code\":\"" + codeActivation + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andReturn();
        String jetonApres = champ(verif, "accessToken");
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + jetonApres)).andExpect(status().isOk());

        Map<String, Object> ligne = jdbc.queryForMap(
                "SELECT phone, phone_e164, phone_verified_at, twofa_method FROM users WHERE id = ?", u.userId);
        assertThat(ligne.get("phone_e164")).isEqualTo(numero);
        assertThat(ligne.get("phone")).isEqualTo(numero);
        assertThat(ligne.get("phone_verified_at")).isNotNull();
        assertThat(ligne.get("twofa_method")).isEqualTo("SMS");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE user_id = ? AND action = '2FA_ENABLED' AND metadata::text LIKE '%SMS%'",
                Long.class, u.userId)).as("activation tracee").isEqualTo(1L);

        // Connexion : code SMS exige, envoye par la route publique du defi, puis verifie.
        JsonNode l = connexion(u);
        assertThat(l.get("requires2fa").asBoolean()).isTrue();
        assertThat(l.get("twofaMethod").asText()).isEqualTo("SMS");
        reset(smsSender);
        MvcResult defi = mvc.perform(post("/api/v1/auth/login/sms/challenge").contentType("application/json")
                        .content("{\"userId\":\"%s\",\"workspaceId\":\"%s\"}".formatted(u.userId, u.workspaceId)))
                .andExpect(status().isAccepted())
                // Indicateur de plateforme (meme reponse pour tout compte) : SMS simule ou non.
                .andExpect(jsonPath("$.simule").value(false))
                .andReturn();
        String codeConnexion = dernierCode(numero);
        assertThat(defi.getResponse().getContentAsString()).doesNotContain(codeConnexion);

        mvc.perform(post("/api/v1/auth/verify-2fa").contentType("application/json")
                        .content("{\"userId\":\"%s\",\"workspaceId\":\"%s\",\"code\":\"%s\"}"
                                .formatted(u.userId, u.workspaceId, codeConnexion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString());
        // Code a usage unique.
        mvc.perform(post("/api/v1/auth/verify-2fa").contentType("application/json")
                        .content("{\"userId\":\"%s\",\"workspaceId\":\"%s\",\"code\":\"%s\"}"
                                .formatted(u.userId, u.workspaceId, codeConnexion)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void numero_mal_forme_refuse_sans_envoi() throws Exception {
        Compte u = compteSansDoubleAuthentification("smsnum1@example.com", "+212600000104");
        mvc.perform(post("/api/v1/auth/2fa/sms/send").header("Authorization", "Bearer " + u.jeton)
                        .contentType("application/json")
                        .content("{\"purpose\":\"2FA_SETUP\",\"telephone\":\"0611223344\"}"))
                .andExpect(status().isBadRequest());
        verify(smsSender, never()).send(eq("0611223344"), anyString());
    }

    // ------------------------------------------------------------- essais

    /**
     * Limitation des essais (regles existantes : 5 essais par code, RG-AU37). Defaut trouve :
     * l'echec etait compte PUIS annule avec la transaction (exception), donc jamais compte.
     */
    @Test
    void cinq_codes_sms_faux_epuisent_le_code_et_verrouillent_le_compte() throws Exception {
        Compte u = compteSansDoubleAuthentification("smsessais1@example.com", "+212600000106");
        String numero = "+212633445566";
        mvc.perform(post("/api/v1/auth/2fa/sms/send").header("Authorization", "Bearer " + u.jeton)
                        .contentType("application/json")
                        .content("{\"purpose\":\"2FA_SETUP\",\"telephone\":\"" + numero + "\"}"))
                .andExpect(status().isOk());
        String bon = dernierCode(numero);
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/2fa/sms/verify").header("Authorization", "Bearer " + u.jeton)
                            .contentType("application/json")
                            .content("{\"purpose\":\"2FA_SETUP\",\"code\":\"" + autreCode(bon) + "\"}"))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(jdbc.queryForObject("SELECT attempts FROM sms_otp_codes WHERE user_id = ? AND purpose = '2FA_SETUP'",
                Integer.class, u.userId)).as("essais comptes").isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT locked_until FROM users WHERE id = ?", Instant.class, u.userId))
                .as("compte verrouille").isNotNull();
        // Meme le bon code est refuse ensuite.
        mvc.perform(post("/api/v1/auth/2fa/sms/verify").header("Authorization", "Bearer " + u.jeton)
                        .contentType("application/json")
                        .content("{\"purpose\":\"2FA_SETUP\",\"code\":\"" + bon + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SMS_OTP_TOO_MANY_ATTEMPTS"));
        assertThat(jdbc.queryForObject("SELECT twofa_method FROM users WHERE id = ?", String.class, u.userId)).isNull();
    }

    @Test
    void codes_de_l_application_faux_a_la_connexion_verrouillent_le_compte() throws Exception {
        Compte u = compteSansDoubleAuthentification("totpessais1@example.com", "+212600000107");
        activerTotp(u);
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/verify-2fa").contentType("application/json")
                            .content("{\"userId\":\"%s\",\"workspaceId\":\"%s\",\"code\":\"000000\"}"
                                    .formatted(u.userId, u.workspaceId)))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(jdbc.queryForObject("SELECT locked_until FROM users WHERE id = ?", Instant.class, u.userId))
                .as("compte verrouille apres 5 echecs").isNotNull();
    }

    /** Meme regle (RG-AU37, echecs du mot de passe OU du second facteur confondus). */
    @Test
    void cinq_mots_de_passe_faux_verrouillent_le_compte() throws Exception {
        Compte u = compteSansDoubleAuthentification("mdpessais1@example.com", "+212600000108");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                            .content("{\"workspaceCode\":\"%s\",\"email\":\"%s\",\"password\":\"Faux-mot-de-passe-1!\"}"
                                    .formatted(u.workspaceCode, u.email)))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(jdbc.queryForObject("SELECT failed_login_attempts FROM users WHERE id = ?", Integer.class, u.userId))
                .as("echecs comptes").isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT locked_until FROM users WHERE id = ?", Instant.class, u.userId))
                .as("compte verrouille").isNotNull();
    }

    // ------------------------------------------------------------------ 3

    @Test
    void passage_de_l_application_au_sms_exige_le_code_actuel_et_conserve_les_codes_de_recuperation() throws Exception {
        Compte u = compteSansDoubleAuthentification("smschange1@example.com", "+212600000105");
        String secret = activerTotp(u);
        mvc.perform(post("/api/v1/auth/2fa/recovery-codes/generate").header("Authorization", "Bearer " + u.jeton))
                .andExpect(status().isOk());
        List<UUID> codesAvant = jdbc.queryForList(
                "SELECT id FROM recovery_codes WHERE user_id = ? AND used_at IS NULL ORDER BY id", UUID.class, u.userId);
        assertThat(codesAvant).hasSize(10);
        String numero = "+212622334455";

        // Sans le code de la methode actuelle : refuse, aucun SMS.
        mvc.perform(post("/api/v1/auth/2fa/sms/send").header("Authorization", "Bearer " + u.jeton)
                        .contentType("application/json")
                        .content("{\"purpose\":\"2FA_SETUP\",\"telephone\":\"" + numero + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("2FA_CODE_ACTUEL_REQUIS"));
        // Code faux : refuse.
        mvc.perform(post("/api/v1/auth/2fa/sms/send").header("Authorization", "Bearer " + u.jeton)
                        .contentType("application/json")
                        .content("{\"purpose\":\"2FA_SETUP\",\"telephone\":\"" + numero + "\",\"codeActuel\":\"000000\"}"))
                .andExpect(status().isUnauthorized());
        verify(smsSender, never()).send(eq(numero), anyString());

        HORLOGE.avancer(Duration.ofSeconds(30));
        String codeTotp = String.format("%06d", new GoogleAuthenticator().getTotpPassword(secret, HORLOGE.millis()));
        mvc.perform(post("/api/v1/auth/2fa/sms/send").header("Authorization", "Bearer " + u.jeton)
                        .contentType("application/json")
                        .content("{\"purpose\":\"2FA_SETUP\",\"telephone\":\"" + numero + "\",\"codeActuel\":\"" + codeTotp + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/2fa/sms/verify").header("Authorization", "Bearer " + u.jeton)
                        .contentType("application/json")
                        .content("{\"purpose\":\"2FA_SETUP\",\"code\":\"" + dernierCode(numero) + "\"}"))
                .andExpect(status().isOk());

        List<UUID> codesApres = jdbc.queryForList(
                "SELECT id FROM recovery_codes WHERE user_id = ? AND used_at IS NULL ORDER BY id", UUID.class, u.userId);
        assertThat(codesApres).as("codes de recuperation conserves").isEqualTo(codesAvant);

        JsonNode l = connexion(u);
        assertThat(l.get("requires2fa").asBoolean()).isTrue();
        assertThat(l.get("twofaMethod").asText()).isEqualTo("SMS");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE user_id = ? AND action = '2FA_ENABLED' AND metadata::text LIKE '%TOTP%' AND metadata::text LIKE '%SMS%'",
                Long.class, u.userId)).as("changement trace (methode precedente)").isEqualTo(1L);
    }

    // ================================================================ outils

    record Compte(UUID userId, UUID workspaceId, String workspaceCode, String email, String jeton) {}

    private Compte compteSansDoubleAuthentification(String email, String telephone) throws Exception {
        String suffixe = email.split("@")[0];
        String inscription = """
                { "workspaceName": "Cabinet %s", "contactEmail": "contact-%s@example.com",
                  "subscriptionId": "%s", "firstName": "User", "lastName": "%s", "phone": "%s",
                  "email": "%s", "password": "ignored-by-server-v2-strict" }
                """.formatted(suffixe, suffixe, SUBSCRIPTION_ID, suffixe, telephone, email);
        MvcResult reg = mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(inscription))
                .andExpect(status().isCreated()).andReturn();
        JsonNode r = json.readTree(reg.getResponse().getContentAsString());
        String code = r.get("workspaceCode").asText();
        UUID workspaceId = UUID.fromString(r.get("workspaceId").asText());
        UUID userId = UUID.fromString(r.get("userId").asText());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Map<String, Object>> vars = (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
        verify(emailSender, atLeastOnce()).sendTemplated(eq(email), anyString(), eq("welcome"), vars.capture());
        String temporaire = (String) vars.getValue().get("temporaryPassword");
        String url = (String) vars.getValue().get("verificationUrl");
        String jetonCourriel = url.substring(url.indexOf("?token=") + "?token=".length());
        mvc.perform(post("/api/v1/auth/verify-email").contentType("application/json")
                .content("{\"token\":\"" + jetonCourriel + "\"}")).andExpect(status().isOk());

        MvcResult l1 = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("{\"workspaceCode\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(code, email, temporaire)))
                .andExpect(status().isOk()).andReturn();
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + champ(l1, "accessToken"))
                        .contentType("application/json")
                        .content("{\"oldPassword\":\"%s\",\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}"
                                .formatted(temporaire, MOT_DE_PASSE, MOT_DE_PASSE)))
                .andExpect(status().isOk());
        Compte provisoire = new Compte(userId, workspaceId, code, email, null);
        JsonNode l2 = connexion(provisoire);
        return new Compte(userId, workspaceId, code, email, l2.get("accessToken").asText());
    }

    /** Active l'application d'authentification ; renvoie le secret. */
    private String activerTotp(Compte u) throws Exception {
        MvcResult s = mvc.perform(post("/api/v1/auth/setup-2fa").header("Authorization", "Bearer " + u.jeton))
                .andExpect(status().isOk()).andReturn();
        String secret = champ(s, "secret");
        HORLOGE.avancer(Duration.ofSeconds(30));
        int code = new GoogleAuthenticator().getTotpPassword(secret, HORLOGE.millis());
        mvc.perform(post("/api/v1/auth/setup-2fa/confirm").header("Authorization", "Bearer " + u.jeton)
                        .contentType("application/json").content("{\"code\":" + code + "}"))
                .andExpect(status().isOk());
        return secret;
    }

    private JsonNode connexion(Compte u) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("{\"workspaceCode\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(u.workspaceCode, u.email, MOT_DE_PASSE)))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString());
    }

    /** Code a 6 chiffres du dernier SMS envoye a ce numero (double du fournisseur). */
    private String dernierCode(String numero) {
        ArgumentCaptor<String> corps = ArgumentCaptor.forClass(String.class);
        verify(smsSender, atLeastOnce()).send(eq(numero), corps.capture());
        String code = corps.getValue().replaceAll(".*?(\\d{6}).*", "$1");
        assertThat(code).matches("\\d{6}");
        return code;
    }

    private static String autreCode(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    private String champ(MvcResult r, String nom) throws Exception {
        return json.readTree(r.getResponse().getContentAsString()).get(nom).asText();
    }
}
