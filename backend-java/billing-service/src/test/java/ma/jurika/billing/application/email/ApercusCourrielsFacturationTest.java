package ma.jurika.billing.application.email;

import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.mail.javamail.JavaMailSender;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Recette R3 : apercu local du courriel de facture (HTML, texte brut, PDF joint). Sur demande :
 * {@code mvn -o test -pl billing-service -am -Dtest=ApercusCourrielsFacturationTest
 * -Dcourriels.apercus=<dossier> -Dsurefire.failIfNoSpecifiedTests=false}.
 */
@EnabledIfSystemProperty(named = "courriels.apercus", matches = ".+")
class ApercusCourrielsFacturationTest {

    @Test
    void ecrire_les_apercus() throws Exception {
        CourrielsFacturationStructureTest.init();
        Path dossier = Path.of(System.getProperty("courriels.apercus"));
        Files.createDirectories(dossier);
        for (ExemplairesCourrielsFacturation.Exemplaire e : ExemplairesCourrielsFacturation.tous()) {
            JavaMailSender mail = CourrielsFacturationStructureTest.relais();
            new BillingEmailService(mail, CourrielsFacturationStructureTest.moteur, CourrielsFacturationStructureTest.proprietes())
                    .envoyer("destinataire@exemple.test", e.objet(), e.gabarit(), e.variables(), e.pieces());
            MimeMessage m = CourrielsFacturationStructureTest.capture(mail);
            String html = null;
            String texte = null;
            byte[] logo = null;
            for (Part p : CourrielsFacturationStructureTest.feuilles(m)) {
                if (Part.ATTACHMENT.equalsIgnoreCase(p.getDisposition())) {
                    Files.write(dossier.resolve(p.getFileName()), p.getInputStream().readAllBytes());
                } else if (p.isMimeType("text/html")) html = (String) p.getContent();
                else if (p.isMimeType("text/plain")) texte = (String) p.getContent();
                else if (p.isMimeType("image/png")) logo = p.getInputStream().readAllBytes();
            }
            String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(logo);
            Files.writeString(dossier.resolve(e.code() + ".html"), html.replace("cid:logo-jurika", dataUri));
            Files.writeString(dossier.resolve(e.code() + ".txt"), "Objet : " + m.getSubject() + "\n\n" + texte);
        }
    }
}
