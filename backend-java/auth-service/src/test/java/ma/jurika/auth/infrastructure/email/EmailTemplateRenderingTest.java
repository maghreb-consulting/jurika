package ma.jurika.auth.infrastructure.email;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 3 / TASK 2 — verifie le rendu Thymeleaf des 7 templates emails brandes
 * Maghreb Consulting. On s'assure que :
 * <ul>
 *     <li>Le moteur ne leve pas d'erreur de parsing</li>
 *     <li>Les variables sont substituees</li>
 *     <li>Le footer Maghreb Consulting + warning phishing sont presents (quand pertinent)</li>
 * </ul>
 */
class EmailTemplateRenderingTest {

    private static SpringTemplateEngine engine;

    @BeforeAll
    static void initEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        // SpringTemplateEngine evalue ${...} via SpEL (pas OGNL), aligne sur l'engine prod.
        engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
    }

    private static String render(String templateName, Map<String, Object> vars) {
        Context ctx = new Context();
        ctx.setVariables(vars);
        return engine.process("email/" + templateName, ctx);
    }

    /**
     * Texte lisible d'un rendu. Les templates emails ecrivent leurs accents en
     * ENTITES HTML ({@code augment&eacute;e}) : c'est deliberé — certains clients
     * mail anciens abiment l'UTF-8. Une assertion ASCII sur le HTML brut ne peut
     * donc jamais correspondre. On decode les entites puis on retire les
     * diacritiques, ce qui garde les assertions lisibles ET les rend insensibles
     * a un futur passage aux caracteres accentues litteraux.
     */
    private static String plainText(String html) {
        String decoded = html
                .replace("&eacute;", "e").replace("&egrave;", "e").replace("&ecirc;", "e")
                .replace("&euml;", "e").replace("&agrave;", "a").replace("&acirc;", "a")
                .replace("&ccedil;", "c").replace("&ocirc;", "o").replace("&icirc;", "i")
                .replace("&iuml;", "i").replace("&ucirc;", "u").replace("&ugrave;", "u")
                .replace("&nbsp;", " ").replace("&mdash;", "-").replace("&amp;", "&");
        // Filet : accents ecrits en UTF-8 litteral plutot qu'en entites.
        return Normalizer.normalize(decoded, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    @Test
    @DisplayName("welcome.html : rend variables + brand Maghreb Consulting + phishing box")
    void renderWelcome() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("firstName", "Karim");
        vars.put("brand", "JURIKA");
        vars.put("workspaceCode", "JUR-A4F2K");
        vars.put("email", "karim@cabinet.ma");
        vars.put("temporaryPassword", "Aa9$kL2p#Mq8");
        vars.put("verificationUrl", "https://app.jurika.ma/verify?token=xyz");
        vars.put("ttlHours", "24");
        vars.put("phishingWarning", "ignore");
        vars.put("subject", "Bienvenue");
        vars.put("unsubscribeUrl", "https://app.jurika.ma/unsubscribe?u=xyz");

        String html = render("welcome", vars);

        assertThat(html).contains("Karim");
        assertThat(html).contains("JUR-A4F2K");
        assertThat(html).contains("karim@cabinet.ma");
        assertThat(html).contains("Aa9$kL2p#Mq8");
        assertThat(html).contains("Maghreb Consulting");
        assertThat(html).contains("contact@maghreb-consulting.ma");
        assertThat(html).contains("Avertissement anti-phishing");
        assertThat(html).contains("Conforme RGPD");
    }

    @Test
    @DisplayName("verify-email.html : substitue firstName + URL de verification")
    void renderVerifyEmail() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("firstName", "Aicha");
        vars.put("verificationUrl", "https://app.jurika.ma/verify?token=abc");
        vars.put("unsubscribeUrl", null);

        String html = render("verify-email", vars);

        assertThat(html).contains("Aicha");
        assertThat(html).contains("https://app.jurika.ma/verify?token=abc");
        assertThat(html).contains("Maghreb Consulting");
        assertThat(html).contains("24 heures");
    }

    @Test
    @DisplayName("password-changed.html : injecte ip + user-agent + date + footer")
    void renderPasswordChanged() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("firstName", "Karim");
        vars.put("changedAt", "22/05/2026 14:32 GMT");
        vars.put("ipAddress", "196.12.34.56");
        vars.put("userAgent", "Chrome 128 / Windows 11");
        vars.put("unsubscribeUrl", null);

        String html = render("password-changed", vars);

        assertThat(html).contains("Karim");
        assertThat(html).contains("196.12.34.56");
        assertThat(html).contains("Chrome 128");
        assertThat(html).contains("22/05/2026");
        assertThat(html).contains("support@jurika.ma");
        assertThat(html).contains("Avertissement anti-phishing");
    }

    @Test
    @DisplayName("login-new-device.html : alerte + lien securite + sans location")
    void renderLoginNewDeviceNoGeo() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("firstName", "Karim");
        vars.put("loginAt", "22/05/2026 14:32 GMT");
        vars.put("ipAddress", "196.12.34.56");
        vars.put("userAgent", "Chrome 128");
        vars.put("location", null);
        vars.put("securityUrl", "https://app.jurika.ma/account/security");
        vars.put("unsubscribeUrl", null);

        String html = render("login-new-device", vars);

        assertThat(html).contains("196.12.34.56");
        assertThat(html).contains("https://app.jurika.ma/account/security");
        assertThat(html).contains("Maghreb Consulting");
        assertThat(html).doesNotContain("Localisation approximative");
    }

    @Test
    @DisplayName("login-new-device.html : ajoute la section location si fournie")
    void renderLoginNewDeviceWithGeo() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("firstName", "Karim");
        vars.put("loginAt", "22/05/2026 14:32 GMT");
        vars.put("ipAddress", "196.12.34.56");
        vars.put("userAgent", "Chrome 128");
        vars.put("location", "Casablanca, MA");
        vars.put("securityUrl", "https://app.jurika.ma/account/security");
        vars.put("unsubscribeUrl", null);

        String html = render("login-new-device", vars);

        assertThat(html).contains("Localisation approximative");
        assertThat(html).contains("Casablanca, MA");
    }

    @Test
    @DisplayName("2fa-enabled.html : confirme methode + bonnes pratiques + branding")
    void render2faEnabled() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("firstName", "Karim");
        vars.put("method", "Google Authenticator (TOTP)");
        vars.put("enabledAt", "22/05/2026 14:32 GMT");
        vars.put("unsubscribeUrl", null);

        String html = render("2fa-enabled", vars);

        assertThat(html).contains("Google Authenticator (TOTP)");
        assertThat(html).contains("codes de recuperation");
        assertThat(html).contains("Maghreb Consulting");
        assertThat(html).contains("Avertissement anti-phishing");
    }

    @Test
    @DisplayName("recovery-codes-regenerated.html : annonce anciens codes invalides")
    void renderRecoveryCodesRegenerated() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("firstName", "Karim");
        vars.put("regeneratedAt", "22/05/2026 14:32 GMT");
        vars.put("count", "10");
        vars.put("ipAddress", "196.12.34.56");
        vars.put("unsubscribeUrl", null);

        String html = render("recovery-codes-regenerated", vars);

        assertThat(html).contains("anciens codes ne sont plus valides");
        assertThat(html).contains("196.12.34.56");
        assertThat(html).contains("10");
        assertThat(html).contains("Maghreb Consulting");
    }

    @Test
    @DisplayName("ticket-assigned.html : injecte les meta du ticket + CTA")
    void renderTicketAssigned() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("firstName", "Karim");
        vars.put("ticketReference", "TCK-2026-00042");
        vars.put("ticketTitle", "Modification statuts SARL XYZ");
        vars.put("ticketType", "MODIFICATION");
        vars.put("priority", "NORMALE");
        vars.put("clientName", "SARL Exemple");
        vars.put("assignedBy", "Superviseur Adil");
        vars.put("dueDate", "30/05/2026");
        vars.put("ticketUrl", "https://app.jurika.ma/tickets/TCK-2026-00042");
        vars.put("unsubscribeUrl", null);

        String html = render("ticket-assigned", vars);

        assertThat(html).contains("TCK-2026-00042");
        assertThat(html).contains("Modification statuts SARL XYZ");
        assertThat(html).contains("Superviseur Adil");
        assertThat(html).contains("30/05/2026");
        assertThat(html).contains("https://app.jurika.ma/tickets/TCK-2026-00042");
        assertThat(html).contains("Maghreb Consulting");
    }

    @Test
    @DisplayName("header : nom JURIKA present en texte (wordmark) sans dependre d'une image")
    void headerRendersJurikaWordmarkAsText() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("firstName", "Aicha");
        vars.put("verificationUrl", "https://app.jurika.ma/verify?token=abc");
        vars.put("unsubscribeUrl", null);
        // logoUrl volontairement absent : on ne compte PAS sur une image externe.

        String html = render("verify-email", vars);

        // Le wordmark texte "JURI"+"KA" est rendu meme si les images sont bloquees.
        assertThat(html).contains(">JURI<");
        assertThat(html).contains(">KA<");
        assertThat(plainText(html)).contains("Gestion juridique augmentee");
        // Source de l'embleme = CID embarque par defaut (pas d'URL externe).
        assertThat(html).contains("cid:jurika-logo");
        // L'ancienne URL externe non deployee (bloquee par Gmail/Outlook) a disparu.
        assertThat(html).doesNotContain("favicon-192.png");
        assertThat(html).doesNotContain("app.jurika.ai");
    }

    @Test
    @DisplayName("footer : sans unsubscribeUrl utilise le texte de repli")
    void footerFallbackWithoutUnsubscribeUrl() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("firstName", "Karim");
        vars.put("changedAt", "22/05/2026 14:32 GMT");
        vars.put("ipAddress", "196.12.34.56");
        vars.put("userAgent", "Chrome");
        vars.put("unsubscribeUrl", null);

        String html = render("password-changed", vars);

        // Assertions sur le texte decode : sur le HTML brut, « Gerer mes notifications »
        // serait absent meme si le lien etait rendu (le template ecrit « G&eacute;rer »),
        // et le doesNotContain passerait alors sans rien prouver.
        assertThat(plainText(html)).contains("preferences de notification");
        assertThat(plainText(html)).doesNotContain("Gerer mes notifications");
    }
    // Lot L5 (E10) : gestion de son compte (RG-CPT-02, RG-COM-10).

    private static Map<String, Object> varsCompte() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("firstName", "Karim");
        vars.put("loginEmail", "karim.benali@jurika.ma");
        vars.put("contactEmail", "karim@cabinet.ma");
        vars.put("unsubscribeUrl", null);
        vars.put("ttlHours", "24");
        vars.put("requestedAt", "12/10/2026 14:32");
        vars.put("ipAddress", "196.12.34.56");
        return vars;
    }

    @Test
    @DisplayName("email-change-confirm.html : lien de confirmation et nouvelle adresse")
    void renderEmailChangeConfirm() {
        Map<String, Object> vars = varsCompte();
        vars.put("nouvelleAdresse", "perso-7@exemple.ma");
        vars.put("confirmationUrl", "https://app.jurika.ma/confirmer-changement-compte?token=jeton-42");

        String html = render("email-change-confirm", vars);

        assertThat(html).contains("href=\"https://app.jurika.ma/confirmer-changement-compte?token=jeton-42\"");
        assertThat(html).contains("perso-7@exemple.ma");
        assertThat(plainText(html)).contains("expire dans").contains("24");
        assertThat(plainText(html).replaceAll("\\s+", " ")).contains("ancienne adresse reste celle du compte");
    }

    @Test
    @DisplayName("email-change-notice.html : avis a l'ancienne adresse, sans lien de confirmation")
    void renderEmailChangeNotice() {
        Map<String, Object> vars = varsCompte();
        vars.put("nouvelleAdresse", "perso-7@exemple.ma");

        String html = render("email-change-notice", vars);

        assertThat(html).contains("perso-7@exemple.ma").contains("196.12.34.56").contains("12/10/2026 14:32");
        assertThat(html).doesNotContain("token=");
        assertThat(plainText(html).replaceAll("\\s+", " ")).contains("Tant que ce lien n").contains("rien ne change");
    }

    @Test
    @DisplayName("login-change-confirm.html : lien, nouvel identifiant et identifiant actuel")
    void renderLoginChangeConfirm() {
        Map<String, Object> vars = varsCompte();
        vars.put("nouvelIdentifiant", "k.benali@jurika.ma");
        vars.put("confirmationUrl", "https://app.jurika.ma/confirmer-changement-compte?token=jeton-43");

        String html = render("login-change-confirm", vars);

        assertThat(html).contains("token=jeton-43");
        assertThat(html).contains("k.benali@jurika.ma").contains("karim.benali@jurika.ma");
    }

    // Lot L5 (E9, RG-COM-09) : les courriels au client indiquent le cabinet a l'origine.
    @Test
    @DisplayName("client-invite.html : nomme le cabinet qui invite")
    void clientInviteNommeLeCabinet() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("firstName", "Karim");
        vars.put("cabinet", "Cabinet Zerhouni Conseil");
        vars.put("raisonSociale", "ATLAS SARL");
        vars.put("workspaceCode", "JUR-A4F2K");
        vars.put("loginEmail", "karim.c1@jurika.ma");
        vars.put("contactEmail", "karim@example.ma");
        vars.put("temporaryPassword", "Aa9kL2pMq8");
        vars.put("loginUrl", "https://app.jurika.ma/login");

        String texte = plainText(render("client-invite", vars));

        assertThat(texte).contains("Cabinet Zerhouni Conseil");
        assertThat(texte).contains("Votre cabinet <strong>Cabinet Zerhouni Conseil</strong>");
        assertThat(texte).contains("ATLAS SARL");
    }

    @Test
    @DisplayName("client-notification.html : cabinet, annonce, objet et lien")
    void clientNotificationNommeLeCabinet() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("cabinet", "Cabinet Zerhouni Conseil");
        vars.put("firstName", "Karim");
        vars.put("titre", "Une requête de votre cabinet");
        vars.put("annonce", "Votre cabinet Cabinet Zerhouni Conseil vous adresse une requête sur le dossier ATLAS SARL.");
        vars.put("raisonSociale", "ATLAS SARL");
        vars.put("objet", "Copie de la CIN du gérant");
        vars.put("bouton", "Répondre à la requête");
        vars.put("lienUrl", "https://app.jurika.ma/mes-requetes");
        vars.put("loginEmail", "karim.c1@jurika.ma");
        vars.put("contactEmail", "karim@example.ma");

        String html = render("client-notification", vars);
        String texte = plainText(html);

        assertThat(texte).contains("Message de votre cabinet").contains("Cabinet Zerhouni Conseil");
        assertThat(texte).contains("vous adresse une requete sur le dossier ATLAS SARL");
        assertThat(texte).contains("Copie de la CIN du gerant");
        assertThat(html).contains("href=\"https://app.jurika.ma/mes-requetes\"");
        assertThat(texte).contains("Maghreb Consulting");
    }
}
