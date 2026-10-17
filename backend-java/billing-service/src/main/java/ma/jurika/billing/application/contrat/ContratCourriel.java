package ma.jurika.billing.application.contrat;

import jakarta.mail.internet.MimeMessage;
import ma.jurika.billing.infrastructure.config.BillingProperties;
import ma.jurika.common.courriel.CourrielJurika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Map;

/**
 * Lot L7 (E6) : courriels du contrat (copie de l'exemplaire RG-CTR-04, echeance a 30 jours
 * RG-CTR-06, nouvelle version des conditions generales RG-CTR-07), par le relais SMTP existant.
 * Le resultat est rendu a l'appelant, qui le journalise dans {@code contrat_avis} : un envoi
 * rate se voit (motif 9), il n'est jamais avale.
 */
@Component
public class ContratCourriel {

    private static final Logger log = LoggerFactory.getLogger(ContratCourriel.class);

    /** Resultat d'un envoi : null = envoye, sinon le motif de l'echec. */
    public record Resultat(String erreur) {
        public boolean envoye() { return erreur == null; }
    }

    public record PieceJointe(String nom, byte[] contenu) {}

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final BillingProperties props;

    public ContratCourriel(JavaMailSender mailSender, TemplateEngine templateEngine, BillingProperties props) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.props = props;
    }

    public Resultat envoyer(String destinataire, String objet, String gabarit, Map<String, Object> variables,
                            PieceJointe piece) {
        if (destinataire == null || destinataire.isBlank()) {
            return new Resultat("Adresse du destinataire absente");
        }
        if (props.getFromEmail() == null || props.getFromEmail().isBlank()) {
            return new Resultat("Exp\u00e9diteur non configur\u00e9 (SMTP_FROM)");
        }
        try {
            Context ctx = new Context();
            variables.forEach(ctx::setVariable);
            String html = templateEngine.process(gabarit, ctx);
            MimeMessage message = mailSender.createMimeMessage();
            // Recette R3 : composition commune (logo en ligne par Content-ID, texte brut, aucune
            // image de decor) ; le PDF du contrat reste la seule piece jointe.
            CourrielJurika.composer(message, props.getFromEmail(), props.getFromName(), destinataire, objet, html,
                    piece == null ? List.of()
                            : List.of(new CourrielJurika.PieceJointe(piece.nom(), piece.contenu(), "application/pdf")));
            mailSender.send(message);
            log.info("Courriel du contrat envoye gabarit={} destinataire={}", gabarit, destinataire);
            return new Resultat(null);
        } catch (Exception ex) {
            log.warn("Echec du courriel du contrat gabarit={} destinataire={} : {}", gabarit, destinataire, ex.getMessage());
            String m = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return new Resultat(m.length() > 500 ? m.substring(0, 500) : m);
        }
    }
}
