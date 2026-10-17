package ma.jurika.auth.infrastructure.email;

import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import ma.jurika.auth.application.ExemplairesCourriels;
import ma.jurika.auth.application.ExemplairesCourriels.Exemplaire;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Recette R3 : apercus locaux des exemplaires (HTML et texte brut), pour les regarder dans un
 * navigateur. Ne tourne que sur demande :
 * {@code mvn -o test -pl auth-service -am -Dtest=ApercusCourrielsTest -Dcourriels.apercus=<dossier>
 * -Dsurefire.failIfNoSpecifiedTests=false}. Le logo (cid:logo-jurika) y est remplace par une
 * image data: pour l'affichage hors messagerie ; le message envoye, lui, garde le Content-ID.
 */
@EnabledIfSystemProperty(named = "courriels.apercus", matches = ".+")
class ApercusCourrielsTest {

    @Test
    void ecrire_les_apercus() throws Exception {
        CourrielsAuthStructureTest.init();
        Path dossier = Path.of(System.getProperty("courriels.apercus"));
        Files.createDirectories(dossier);
        for (Exemplaire e : ExemplairesCourriels.tous()) {
            MimeMessage m = CourrielsAuthStructureTest.envoyer(e);
            String html = null;
            String texte = null;
            byte[] logo = null;
            for (Part p : CourrielsAuthStructureTest.feuilles(m)) {
                if (p.isMimeType("text/html")) html = (String) p.getContent();
                else if (p.isMimeType("text/plain")) texte = (String) p.getContent();
                else if (p.isMimeType("image/png")) logo = p.getInputStream().readAllBytes();
            }
            String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(logo);
            Files.writeString(dossier.resolve(e.code() + ".html"), html.replace("cid:logo-jurika", dataUri));
            Files.writeString(dossier.resolve(e.code() + ".txt"), "Objet : " + m.getSubject() + "\n\n" + texte);
        }
    }
}
