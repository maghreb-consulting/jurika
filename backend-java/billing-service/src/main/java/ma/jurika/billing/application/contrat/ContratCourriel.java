package ma.jurika.billing.application.contrat;

import jakarta.mail.internet.MimeMessage;
import ma.jurika.billing.infrastructure.config.BillingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
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
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(props.getFromEmail());
            helper.setTo(destinataire);
            helper.setSubject(objet);
            Context ctx = new Context();
            variables.forEach(ctx::setVariable);
            helper.setText(templateEngine.process(gabarit, ctx), true);
            ClassPathResource logo = new ClassPathResource("branding/jurika-email-logo.png");
            if (logo.exists()) helper.addInline("jurika-logo", logo, "image/png");
            ClassPathResource zellige = new ClassPathResource("branding/jurika-zellij.png");
            if (zellige.exists()) helper.addInline("jurika-zellij", zellige, "image/png");
            if (piece != null) {
                helper.addAttachment(piece.nom(), new ByteArrayResource(piece.contenu()), "application/pdf");
            }
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
