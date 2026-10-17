package ma.jurika.auth.infrastructure.email;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import ma.jurika.auth.application.ExemplairesCourriels;
import ma.jurika.auth.application.ExemplairesCourriels.Exemplaire;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Recette R3 : "le decor de zellige arrive en piece jointe PNG". Pour chaque courriel soumis a la
 * recette (bienvenue, identifiants d'un employe et d'un client, reinitialisation du mot de passe,
 * notification au client), le message REELLEMENT compose par SmtpEmailSender :
 * <ul>
 *   <li>racine multipart/related, une multipart/alternative (texte brut + HTML) ;</li>
 *   <li>une seule image, le logo, en ligne (inline) avec un Content-ID que le HTML reference ;</li>
 *   <li>aucune partie attachment, aucune image de decor (ni jurika-zellij, ni url(...)) ;</li>
 *   <li>alt "JURIKA", largeur et hauteur fixees ; meta color-scheme ; frise de zellige en
 *       caracteres ; pas de texte gris clair (#94A3B8) ;</li>
 *   <li>version texte brut lisible (ni balises, ni entites).</li>
 * </ul>
 */
class CourrielsAuthStructureTest {

    private static SpringTemplateEngine moteur;

    @BeforeAll
    static void init() {
        ClassLoaderTemplateResolver r = new ClassLoaderTemplateResolver();
        r.setPrefix("templates/");
        r.setSuffix(".html");
        r.setTemplateMode(TemplateMode.HTML);
        r.setCharacterEncoding("UTF-8");
        r.setCacheable(false);
        moteur = new SpringTemplateEngine();
        moteur.setTemplateResolver(r);
    }

    static Stream<Exemplaire> exemplaires() {
        return ExemplairesCourriels.tous().stream();
    }

    /** Message tel que le relais le recoit (ecrit puis relu, comme a la reception). */
    static MimeMessage envoyer(Exemplaire e) throws Exception {
        JavaMailSender mail = mock(JavaMailSender.class);
        when(mail.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        SmtpEmailSender sender = new SmtpEmailSender(mail, moteur, "expediteur@exemple.test", "JURIKA", "support@exemple.test");
        sender.sendTemplated("destinataire@exemple.test", e.objet(), e.gabarit(), e.variables());
        ArgumentCaptor<MimeMessage> c = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mail).send(c.capture());
        MimeMessage m = c.getValue();
        m.saveChanges();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        m.writeTo(out);
        return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(out.toByteArray()));
    }

    static List<Part> feuilles(Part p) throws Exception {
        List<Part> l = new ArrayList<>();
        if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) l.addAll(feuilles(mp.getBodyPart(i)));
        } else {
            l.add(p);
        }
        return l;
    }

    static Stream<String> gabarits() throws Exception {
        java.nio.file.Path dossier = java.nio.file.Path.of("src", "main", "resources", "templates", "email");
        try (Stream<java.nio.file.Path> l = java.nio.file.Files.list(dossier)) {
            return l.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".html") && !n.startsWith("_"))
                    .map(n -> n.substring(0, n.length() - 5)).sorted().toList().stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gabarits")
    void chaque_gabarit_suit_la_mise_en_page_commune(String gabarit) throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Path.of("src", "main", "resources", "templates", "email", gabarit + ".html"));
        assertThat(source).contains("~{email/_mise-en-page :: courriel(").doesNotContain("<head");
        org.thymeleaf.context.Context ctx = new org.thymeleaf.context.Context();
        ctx.setVariable("supportEmail", "support@exemple.test");
        String html = moteur.process("email/" + gabarit, ctx);
        assertThat(html.split("<img", -1)).as("une seule image : le logo").hasSize(2);
        assertThat(html).contains("src=\"cid:logo-jurika\"").contains("alt=\"JURIKA\"")
                .doesNotContain("url(").doesNotContainIgnoringCase("#94A3B8");
    }

    @Test
    void les_cinq_courriels_de_la_recette_ont_un_exemplaire() {
        assertThat(ExemplairesCourriels.tous()).extracting(Exemplaire::code).containsExactly(
                "bienvenue", "identifiants-employe", "identifiants-client", "reinitialisation", "notification-client");
        assertThat(ExemplairesCourriels.tous()).allSatisfy(e -> assertThat(e.objet()).startsWith("[Exemplaire fictif] "));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("exemplaires")
    void structure_mime_logo_en_ligne_reference_et_aucune_piece_jointe(Exemplaire e) throws Exception {
        MimeMessage m = envoyer(e);
        assertThat(m.getContentType()).as("racine").startsWith("multipart/related");
        assertThat(m.getFrom()[0].toString()).isEqualTo("JURIKA <expediteur@exemple.test>");

        String html = null;
        String texte = null;
        List<Part> images = new ArrayList<>();
        for (Part p : feuilles(m)) {
            assertThat(p.getDisposition()).as("aucune piece jointe").isNotEqualToIgnoringCase(Part.ATTACHMENT);
            if (p.isMimeType("text/html")) html = (String) p.getContent();
            else if (p.isMimeType("text/plain")) texte = (String) p.getContent();
            else if (p.isMimeType("image/*")) images.add(p);
            else throw new AssertionError("partie inattendue : " + p.getContentType());
        }
        assertThat(images).as("une seule image : le logo").hasSize(1);
        Part logo = images.get(0);
        assertThat(logo.isMimeType("image/png")).isTrue();
        assertThat(logo.getDisposition()).isEqualToIgnoringCase(Part.INLINE);
        String cid = ((BodyPart) logo).getHeader("Content-ID")[0];
        assertThat(cid).isEqualTo("<logo-jurika>");

        assertThat(html).contains("src=\"cid:logo-jurika\"")
                .contains("alt=\"JURIKA\"").contains("width=\"56\"").contains("height=\"56\"")
                .contains("name=\"color-scheme\"")
                .doesNotContain("jurika-zellij").doesNotContain("url(").doesNotContainIgnoringCase("#94A3B8");
        assertThat(html.split("<img", -1)).as("une seule balise img").hasSize(2);
        assertThat(html).as("frise de zellige en caracteres").containsAnyOf("&#9670;", "\u25c6");

        assertThat(texte).as("version texte brut").isNotBlank().contains("JURIKA")
                .doesNotContain("<").doesNotContainPattern("&[a-zA-Z]+;");
    }
}
