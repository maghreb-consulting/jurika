package ma.jurika.auth.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.model.UserStatus;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.TokenIssuer;
import ma.jurika.common.security.Role;
import ma.jurika.auth.domain.port.EventPublisher;
import ma.jurika.auth.domain.port.SmsSender;
import ma.jurika.common.observability.BusinessMetrics;
import ma.jurika.common.validation.DnsMxValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lot L5 (E10) : gestion de son compte (CDC 3.6, RG-CPT-01 et RG-CPT-02).
 *
 * <p>Base PostgreSQL reelle (Testcontainers), application en role jurika_app
 * (RLS active), identifiant de connexion distinct de l'adresse
 * ({@code jurika.auth.login-email-enabled=true}, comme en production).
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
                "spring.main.allow-bean-definition-overriding=true",
                "jurika.auth.login-email-enabled=true"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("it")
class GestionCompteIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_compte")
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
    static class SyncWiring {
        @Bean @Primary public TaskExecutor taskExecutor() { return new SyncTaskExecutor(); }
        @Bean public MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
        @Bean public BusinessMetrics businessMetrics(MeterRegistry r) { return new BusinessMetrics(r); }
        @Bean public ConnectionFactory rabbitConnectionFactory() { return mock(ConnectionFactory.class); }
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private TokenIssuer tokenIssuer;
    /** Lecture de l'etat reel en proprietaire, hors RLS. */
    private final JdbcTemplate jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

    @MockBean private EmailSender emailSender;
    @MockBean private SmsSender smsSender;
    @MockBean private EventPublisher eventPublisher;
    @MockBean private DnsMxValidator dnsMxValidator;

    private static final UUID STARTER_PLAN = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String MOT_DE_PASSE = "NewStrong@2026!";
    private static int compteur = 0;

    @BeforeEach
    void dns() {
        when(dnsMxValidator.validate(anyString())).thenReturn(new DnsMxValidator.Result(true, "OK"));
    }

    // ------------------------------------------------------------ adresse

    @Test
    @DisplayName("Adresse : lien a la nouvelle, avis a l'ancienne ; rien ne change avant la confirmation, tout apres")
    void changement_adresse_confirme_par_lien() throws Exception {
        Compte c = compteActif();
        String nouvelle = "nouvelle-" + c.suffixe + "@example.org";

        mvc.perform(post("/api/v1/auth/me/adresse").header("Authorization", "Bearer " + c.access)
                        .contentType("application/json")
                        .content("{\"nouvelleAdresse\":\"" + nouvelle + "\"}"))
                .andExpect(status().isAccepted());

        // Lien a la NOUVELLE adresse, avis a l'ANCIENNE.
        Map<String, Object> lien = capturer(nouvelle, "email-change-confirm");
        String jeton = jetonDe((String) lien.get("confirmationUrl"));
        Map<String, Object> avis = capturer(c.adresse, "email-change-notice");
        assertThat(avis.get("nouvelleAdresse")).isEqualTo(nouvelle);
        verify(emailSender, never()).sendTemplated(eq(c.adresse), anyString(), eq("email-change-confirm"), anyMap());

        // Jeton stocke par son empreinte seulement.
        List<Map<String, Object>> jetons = jdbc.queryForList(
                "SELECT token_hash, email, ROUND(EXTRACT(EPOCH FROM expires_at - created_at) / 3600) AS heures "
                        + "FROM email_verification_tokens "
                        + "WHERE user_id = ? AND purpose = 'EMAIL_CHANGE'", c.userId);
        assertThat(jetons).hasSize(1);
        assertThat(jetons.get(0).get("token_hash")).isNotEqualTo(jeton);
        assertThat(jetons.get(0).get("email")).isEqualTo(nouvelle);
        // Duree de vie des liens de verification existants (jurika.auth.email-verification-ttl-hours = 24).
        assertThat(((Number) jetons.get(0).get("heures")).intValue()).isEqualTo(24);

        // Avant confirmation : l'ancienne adresse reste ; la demande est en attente.
        assertThat(adresseEnBase(c.userId)).isEqualTo(c.adresse);
        JsonNode demandes = lire(mvc.perform(get("/api/v1/auth/me/demandes")
                .header("Authorization", "Bearer " + c.access)).andExpect(status().isOk()).andReturn());
        assertThat(demandes.get("adresse").get("nouvelleValeur").asText()).isEqualTo(nouvelle);
        assertThat(demandes.get("identifiant").isNull()).isTrue();

        // Confirmation (route publique, sans jeton d'acces).
        mvc.perform(post("/api/v1/auth/compte/confirmer").contentType("application/json")
                        .content("{\"token\":\"" + jeton + "\"}"))
                .andExpect(status().isOk());
        assertThat(adresseEnBase(c.userId)).isEqualTo(nouvelle);

        Map<String, Object> trace = audit(c.workspaceId, "CONTACT_EMAIL_CHANGED");
        assertThat((String) trace.get("metadata")).contains(c.adresse).contains(nouvelle);
        assertThat((String) trace.get("metadata")).doesNotContain(jeton);
        assertThat(audit(c.workspaceId, "CONTACT_EMAIL_CHANGE_REQUESTED")).isNotNull();

        // Lien reutilise : refuse.
        MvcResult rejeu = mvc.perform(post("/api/v1/auth/compte/confirmer").contentType("application/json")
                        .content("{\"token\":\"" + jeton + "\"}"))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(lire(rejeu).get("code").asText()).isEqualTo("LIEN_DEJA_UTILISE");
    }

    @Test
    @DisplayName("Adresse : lien expire refuse, l'ancienne adresse reste")
    void lien_expire_refuse() throws Exception {
        Compte c = compteActif();
        String nouvelle = "expire-" + c.suffixe + "@example.org";
        mvc.perform(post("/api/v1/auth/me/adresse").header("Authorization", "Bearer " + c.access)
                        .contentType("application/json")
                        .content("{\"nouvelleAdresse\":\"" + nouvelle + "\"}"))
                .andExpect(status().isAccepted());
        String jeton = jetonDe((String) capturer(nouvelle, "email-change-confirm").get("confirmationUrl"));
        jdbc.update("UPDATE email_verification_tokens SET expires_at = NOW() - INTERVAL '1 minute' "
                + "WHERE user_id = ? AND purpose = 'EMAIL_CHANGE'", c.userId);

        MvcResult r = mvc.perform(post("/api/v1/auth/compte/confirmer").contentType("application/json")
                        .content("{\"token\":\"" + jeton + "\"}"))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(lire(r).get("code").asText()).isEqualTo("LIEN_EXPIRE");
        assertThat(adresseEnBase(c.userId)).isEqualTo(c.adresse);
    }

    @Test
    @DisplayName("Adresse : une nouvelle demande rend la precedente inutilisable ; l'annulation retire la demande")
    void nouvelle_demande_et_annulation() throws Exception {
        Compte c = compteActif();
        String premiere = "premiere-" + c.suffixe + "@example.org";
        String seconde = "seconde-" + c.suffixe + "@example.org";
        demanderAdresse(c, premiere);
        String jeton1 = jetonDe((String) capturer(premiere, "email-change-confirm").get("confirmationUrl"));
        demanderAdresse(c, seconde);

        mvc.perform(post("/api/v1/auth/compte/confirmer").contentType("application/json")
                        .content("{\"token\":\"" + jeton1 + "\"}"))
                .andExpect(status().isBadRequest());
        assertThat(adresseEnBase(c.userId)).isEqualTo(c.adresse);

        String jeton2 = jetonDe((String) capturer(seconde, "email-change-confirm").get("confirmationUrl"));
        mvc.perform(delete("/api/v1/auth/me/demandes/adresse").header("Authorization", "Bearer " + c.access))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/compte/confirmer").contentType("application/json")
                        .content("{\"token\":\"" + jeton2 + "\"}"))
                .andExpect(status().isBadRequest());
        assertThat(adresseEnBase(c.userId)).isEqualTo(c.adresse);
    }

    @Test
    @DisplayName("Le lien de changement d'adresse n'est pas un lien de verification : /verify-email le refuse sans le consommer")
    void verify_email_refuse_un_jeton_de_changement() throws Exception {
        Compte c = compteActif();
        String nouvelle = "detour-" + c.suffixe + "@example.org";
        demanderAdresse(c, nouvelle);
        String jeton = jetonDe((String) capturer(nouvelle, "email-change-confirm").get("confirmationUrl"));

        mvc.perform(post("/api/v1/auth/verify-email").contentType("application/json")
                        .content("{\"token\":\"" + jeton + "\"}"))
                .andExpect(status().isNotFound());
        Integer ouverts = jdbc.queryForObject("SELECT COUNT(*) FROM email_verification_tokens "
                + "WHERE user_id = ? AND purpose = 'EMAIL_CHANGE' AND used_at IS NULL", Integer.class, c.userId);
        assertThat(ouverts).isEqualTo(1);
    }

    // -------------------------------------------------------- identifiant

    @Test
    @DisplayName("Identifiant : lien a l'adresse du compte ; l'ancien identifiant reste actif avant la confirmation")
    void changement_identifiant_confirme_par_lien() throws Exception {
        Compte c = compteActif();
        String nouvel = "id-" + c.suffixe + "@jurika.ma";

        mvc.perform(post("/api/v1/auth/me/identifiant").header("Authorization", "Bearer " + c.access)
                        .contentType("application/json")
                        .content("{\"nouvelIdentifiant\":\"" + nouvel + "\"}"))
                .andExpect(status().isAccepted());
        Map<String, Object> lien = capturer(c.adresse, "login-change-confirm");
        assertThat(lien.get("nouvelIdentifiant")).isEqualTo(nouvel);
        String jeton = jetonDe((String) lien.get("confirmationUrl"));

        // Avant : l'ancien identifiant ouvre la session, le nouveau non.
        connexion(c.code, c.identifiant, MOT_DE_PASSE, 200);
        connexion(c.code, nouvel, MOT_DE_PASSE, 401);

        mvc.perform(post("/api/v1/auth/compte/confirmer").contentType("application/json")
                        .content("{\"token\":\"" + jeton + "\"}"))
                .andExpect(status().isOk());

        // Apres : le nouveau seulement.
        connexion(c.code, nouvel, MOT_DE_PASSE, 200);
        connexion(c.code, c.identifiant, MOT_DE_PASSE, 401);
        Map<String, Object> trace = audit(c.workspaceId, "LOGIN_EMAIL_CHANGED");
        assertThat((String) trace.get("metadata")).contains(c.identifiant).contains(nouvel);
    }

    @Test
    @DisplayName("Identifiant deja pris dans le cabinet : refuse (409)")
    void identifiant_deja_pris() throws Exception {
        Compte c = compteActif();
        MvcResult inv = mvc.perform(post("/api/v1/auth/invite-employee").header("Authorization", "Bearer " + c.access)
                        .contentType("application/json")
                        .content("""
                                {"email":"collegue-%s@example.org","firstName":"Salma","lastName":"Collegue%s","role":"EMPLOYE"}
                                """.formatted(c.suffixe, compteur)))
                .andExpect(status().isCreated()).andReturn();
        String identifiantCollegue = lire(inv).get("loginEmail").asText();

        mvc.perform(post("/api/v1/auth/me/identifiant").header("Authorization", "Bearer " + c.access)
                        .contentType("application/json")
                        .content("{\"nouvelIdentifiant\":\"" + identifiantCollegue + "\"}"))
                .andExpect(status().isConflict());
    }

    // ---------------------------------------------------------- nom, mdp

    @Test
    @DisplayName("Nom et prenom : modifies et traces (ancienne et nouvelle valeur)")
    void modification_du_nom() throws Exception {
        Compte c = compteActif();
        mvc.perform(patch("/api/v1/auth/me/nom").header("Authorization", "Bearer " + c.access)
                        .contentType("application/json")
                        .content("{\"firstName\":\"Yasmine\",\"lastName\":\"El Idrissi\"}"))
                .andExpect(status().isNoContent());
        JsonNode me = lire(mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + c.access))
                .andExpect(status().isOk()).andReturn());
        assertThat(me.get("firstName").asText()).isEqualTo("Yasmine");
        assertThat(me.get("lastName").asText()).isEqualTo("El Idrissi");
        String meta = (String) audit(c.workspaceId, "USER_NAME_CHANGED").get("metadata");
        assertThat(meta).contains("Yasmine").contains("El Idrissi").contains("User");
    }

    @Test
    @DisplayName("Mot de passe : l'actuel faux est refuse, le mot de passe ne change pas")
    void mot_de_passe_actuel_faux_refuse() throws Exception {
        Compte c = compteActif();
        clearInvocations(emailSender);
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + c.access)
                        .contentType("application/json")
                        .content("""
                                {"oldPassword":"Faux@Passe2026!","newPassword":"Autre@Passe2026!","confirmPassword":"Autre@Passe2026!"}
                                """))
                .andExpect(status().isUnauthorized());
        connexion(c.code, c.identifiant, MOT_DE_PASSE, 200);
        connexion(c.code, c.identifiant, "Autre@Passe2026!", 401);
        verify(emailSender, never()).sendTemplated(anyString(), anyString(), eq("password-changed"), anyMap());
    }

    // ------------------------------------------------------------ outils

    record Compte(UUID userId, UUID workspaceId, String code, String identifiant,
                  String adresse, String access, String suffixe) {}

    private Compte compteActif() throws Exception {
        String suffixe = "cpt" + (++compteur) + "_" + System.nanoTime();
        String adresse = suffixe + "@example.com";
        MvcResult reg = mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                        .content("""
                                { "workspaceName": "Cabinet %s",
                                  "contactEmail": "contact-%s@example.com",
                                  "subscriptionId": "%s",
                                  "firstName": "User", "lastName": "Nom%s",
                                  "phone": "+212600%06d",
                                  "email": "%s", "password": "ignored-by-server-v2-strict" }
                                """.formatted(suffixe, suffixe, STARTER_PLAN, compteur,
                                compteur % 1_000_000, adresse)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode r = lire(reg);
        String code = r.get("workspaceCode").asText();
        String identifiant = r.get("loginEmail").asText();
        assertThat(identifiant).isNotEqualTo(adresse);

        Map<String, Object> bienvenue = capturer(adresse, "welcome");
        String temporaire = (String) bienvenue.get("temporaryPassword");
        mvc.perform(post("/api/v1/auth/verify-email").contentType("application/json")
                        .content("{\"token\":\"" + jetonDe((String) bienvenue.get("verificationUrl")) + "\"}"))
                .andExpect(status().isOk());
        String acces1 = lire(connexion(code, identifiant, temporaire, 200)).get("accessToken").asText();
        assertThat(acces1).isNotBlank();
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + acces1)
                        .contentType("application/json")
                        .content("""
                                {"oldPassword":"%s","newPassword":"%s","confirmPassword":"%s"}
                                """.formatted(temporaire, MOT_DE_PASSE, MOT_DE_PASSE)))
                .andExpect(status().isOk());
        UUID userId = UUID.fromString(r.get("userId").asText());
        UUID workspaceId = UUID.fromString(r.get("workspaceId").asText());
        // La double authentification (RG-AU30) est hors du sujet : elle est posee
        // en base et la session est emise par l'emetteur reel de jetons, comme
        // apres un /verify-2fa reussi.
        jdbc.update("UPDATE users SET twofa_method = 'TOTP', totp_enabled = TRUE WHERE id = ?", userId);
        Instant maintenant = Instant.now();
        User u = new User(userId, workspaceId, identifiant, identifiant, adresse, null,
                "User", "Nom" + compteur, null, Role.SUPERVISEUR, "secret-chiffre", true, false, "TOTP",
                maintenant, null, null, (short) 0, null, null, UserStatus.ACTIVE, maintenant);
        String acces = tokenIssuer.issue(u).accessToken();
        return new Compte(userId, workspaceId, code, identifiant, adresse, acces, suffixe);
    }

    private void demanderAdresse(Compte c, String nouvelle) throws Exception {
        mvc.perform(post("/api/v1/auth/me/adresse").header("Authorization", "Bearer " + c.access)
                        .contentType("application/json")
                        .content("{\"nouvelleAdresse\":\"" + nouvelle + "\"}"))
                .andExpect(status().isAccepted());
    }

    private MvcResult connexion(String code, String identifiant, String motDePasse, int attendu) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("""
                                {"workspaceCode":"%s","email":"%s","password":"%s"}
                                """.formatted(code, identifiant, motDePasse)))
                .andExpect(status().is(attendu)).andReturn();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> capturer(String destinataire, String gabarit) {
        ArgumentCaptor<Map<String, Object>> captor =
                (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
        verify(emailSender, atLeastOnce()).sendTemplated(eq(destinataire), anyString(), eq(gabarit), captor.capture());
        return captor.getValue();
    }

    private static String jetonDe(String url) {
        int i = url.indexOf("?token=");
        assertThat(i).as("lien sans jeton : " + url).isPositive();
        return url.substring(i + "?token=".length());
    }

    private JsonNode lire(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    private String adresseEnBase(UUID userId) {
        return jdbc.queryForObject("SELECT contact_email FROM users WHERE id = ?", String.class, userId);
    }

    private Map<String, Object> audit(UUID workspaceId, String action) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT user_id, metadata::text AS metadata FROM audit_log "
                        + "WHERE workspace_id = ? AND action = ? ORDER BY created_at DESC LIMIT 1",
                workspaceId, action);
        assertThat(rows).as("trace " + action).isNotEmpty();
        return rows.get(0);
    }
}
