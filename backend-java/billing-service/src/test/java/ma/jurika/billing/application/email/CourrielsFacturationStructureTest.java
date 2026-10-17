package ma.jurika.billing.application.email;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import ma.jurika.billing.application.contrat.ContratCourriel;
import ma.jurika.billing.infrastructure.config.BillingProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Recette R3 : courriels de billing-service (facture, contrat, rappels...). La facture garde son
 * PDF en piece jointe (attendu) : multipart/mixed [ multipart/related [ alternative (texte brut +
 * HTML), logo en ligne reference ], PDF en attachment ]. Aucune image de decor (plus de motif de
 * zellige joint), seul le PDF est en attachment. Tous les gabarits suivent la mise en page commune.
 */
class CourrielsFacturationStructureTest {

    static SpringTemplateEngine moteur;

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

    static JavaMailSender relais() {
        JavaMailSender mail = mock(JavaMailSender.class);
        when(mail.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        return mail;
    }

    static BillingProperties proprietes() {
        BillingProperties p = new BillingProperties();
        p.setFromEmail("expediteur@exemple.test");
        return p;
    }

    static MimeMessage capture(JavaMailSender mail) throws Exception {
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

    static List<String> multiparts(Part p) throws Exception {
        List<String> l = new ArrayList<>();
        if (p.isMimeType("multipart/*")) {
            l.add(p.getContentType().split(";")[0].trim().toLowerCase());
            Multipart mp = (Multipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) l.addAll(multiparts(mp.getBodyPart(i)));
        }
        return l;
    }

    /** Verifie la structure ; rend le HTML. */
    static String verifierAvecPdf(MimeMessage m, String nomPdf) throws Exception {
        assertThat(m.getFrom()[0].toString()).isEqualTo("JURIKA <expediteur@exemple.test>");
        assertThat(multiparts(m)).containsExactly("multipart/mixed", "multipart/related", "multipart/alternative");
        String html = null;
        String texte = null;
        List<Part> images = new ArrayList<>();
        List<Part> jointes = new ArrayList<>();
        for (Part p : feuilles(m)) {
            if (Part.ATTACHMENT.equalsIgnoreCase(p.getDisposition())) jointes.add(p);
            else if (p.isMimeType("text/html")) html = (String) p.getContent();
            else if (p.isMimeType("text/plain")) texte = (String) p.getContent();
            else if (p.isMimeType("image/*")) images.add(p);
            else throw new AssertionError("partie inattendue : " + p.getContentType());
        }
        assertThat(jointes).as("seul le PDF est en piece jointe").hasSize(1);
        assertThat(jointes.get(0).isMimeType("application/pdf")).isTrue();
        assertThat(jointes.get(0).getFileName()).isEqualTo(nomPdf);
        assertThat(images).as("une seule image : le logo").hasSize(1);
        assertThat(images.get(0).getDisposition()).isEqualToIgnoringCase(Part.INLINE);
        assertThat(((BodyPart) images.get(0)).getHeader("Content-ID")[0]).isEqualTo("<logo-jurika>");
        assertThat(html).contains("src=\"cid:logo-jurika\"").contains("alt=\"JURIKA\"")
                .contains("width=\"56\"").contains("height=\"56\"").contains("name=\"color-scheme\"")
                .doesNotContain("jurika-zellij").doesNotContain("url(");
        assertThat(texte).isNotBlank().doesNotContain("<").doesNotContainPattern("&[a-zA-Z]+;");
        return html;
    }

    @Test
    void facture_pdf_en_piece_jointe_logo_en_ligne_et_rien_d_autre() throws Exception {
        ExemplairesCourrielsFacturation.Exemplaire e = ExemplairesCourrielsFacturation.facture();
        JavaMailSender mail = relais();
        BillingEmailService service = new BillingEmailService(mail, moteur, proprietes());
        assertThat(service.envoyer("destinataire@exemple.test", e.objet(), e.gabarit(), e.variables(), e.pieces()))
                .as("envoye").isNull();
        MimeMessage m = capture(mail);
        assertThat(m.getSubject()).startsWith("[Exemplaire fictif] ");
        String html = verifierAvecPdf(m, "Facture-F-EXEMPLE-0001.pdf");
        assertThat(html).contains("F-EXEMPLE-0001");
        byte[] pdf = e.pieces().get(0).contenu();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void contrat_pdf_en_piece_jointe_sans_motif_de_zellige() throws Exception {
        JavaMailSender mail = relais();
        ContratCourriel courriel = new ContratCourriel(mail, moteur, proprietes());
        ContratCourriel.Resultat r = courriel.envoyer("destinataire@exemple.test", "Votre contrat",
                "email/contrat-copie", Map.of("cabinet", "Cabinet Exemple (fictif)"),
                new ContratCourriel.PieceJointe("Contrat.pdf", "%PDF-1.4 exemple".getBytes()));
        assertThat(r.envoye()).as(String.valueOf(r.erreur())).isTrue();
        verifierAvecPdf(capture(mail), "Contrat.pdf");
    }

    static Stream<String> gabarits() throws Exception {
        try (Stream<Path> l = Files.list(Path.of("src", "main", "resources", "templates", "email"))) {
            return l.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".html") && !n.startsWith("_"))
                    .map(n -> n.substring(0, n.length() - 5)).sorted().toList().stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gabarits")
    void chaque_gabarit_suit_la_mise_en_page_commune(String gabarit) throws Exception {
        String source = Files.readString(Path.of("src", "main", "resources", "templates", "email", gabarit + ".html"));
        assertThat(source).contains("~{email/_mise-en-page :: courriel(").doesNotContain("<head");
        Context ctx = new Context();
        ctx.setVariable("invoiceNumber", "F-EXEMPLE-0001");
        ctx.setVariable("planLabel", "Exemple");
        String html = moteur.process("email/" + gabarit, ctx);
        assertThat(html.split("<img", -1)).as("une seule image : le logo").hasSize(2);
        assertThat(html).contains("src=\"cid:logo-jurika\"").contains("alt=\"JURIKA\"")
                .doesNotContain("url(").doesNotContainIgnoringCase("#94A3B8");
    }
}
