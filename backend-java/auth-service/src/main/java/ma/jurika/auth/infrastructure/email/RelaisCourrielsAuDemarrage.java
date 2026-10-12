package ma.jurika.auth.infrastructure.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Lot L5 (E2) : le relais des courriels est dit au demarrage (jamais l'identifiant ni le
 * mot de passe). Un relais "localhost" dans un conteneur ne mene nulle part : il est
 * signale en avertissement au lieu d'echouer en silence a chaque envoi (defaut constate
 * sur le Z440 jusqu'au lot L5 : auth envoyait vers localhost:1025).
 */
@Component
public class RelaisCourrielsAuDemarrage {

    private static final Logger log = LoggerFactory.getLogger(RelaisCourrielsAuDemarrage.class);
    private static final Set<String> RELAIS_LOCAUX = Set.of("localhost", "127.0.0.1", "::1");

    private final String fournisseur;
    private final String hote;
    private final int port;
    private final boolean authentifie;
    private final boolean starttls;
    private final String expediteur;

    public RelaisCourrielsAuDemarrage(@Value("${jurika.email.provider:smtp}") String fournisseur,
                                      @Value("${spring.mail.host:}") String hote,
                                      @Value("${spring.mail.port:0}") int port,
                                      @Value("${spring.mail.properties.mail.smtp.auth:false}") boolean authentifie,
                                      @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}") boolean starttls,
                                      @Value("${jurika.email.from:}") String expediteur) {
        this.fournisseur = fournisseur;
        this.hote = hote;
        this.port = port;
        this.authentifie = authentifie;
        this.starttls = starttls;
        this.expediteur = expediteur;
    }

    static String resume(String fournisseur, String hote, int port, boolean authentifie,
                         boolean starttls, String expediteur) {
        if (!"smtp".equals(fournisseur)) {
            return "Courriels : fournisseur " + fournisseur + " (aucun envoi reel), expediteur " + expediteur;
        }
        return "Courriels : relais SMTP " + hote + ":" + port
                + (authentifie ? ", authentifie" : ", sans authentification")
                + (starttls ? ", STARTTLS" : ", sans STARTTLS")
                + ", expediteur " + expediteur;
    }

    static boolean relaisLocal(String fournisseur, String hote) {
        return "smtp".equals(fournisseur) && (hote == null || hote.isBlank() || RELAIS_LOCAUX.contains(hote.trim()));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void annoncer() {
        log.info(resume(fournisseur, hote, port, authentifie, starttls, expediteur));
        if (relaisLocal(fournisseur, hote)) {
            log.warn("Courriels : relais local ({}) : dans un conteneur, aucun courriel ne partira. "
                    + "Definir SMTP_HOST (voir infrastructure/COURRIELS.md).", hote);
        }
    }
}
