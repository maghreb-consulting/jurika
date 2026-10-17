package ma.jurika.auth.infrastructure.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.common.courriel.CourrielJurika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.List;
import java.util.Map;

/**
 * Active uniquement quand {@code jurika.email.provider=smtp} (defaut).
 * En dev sans MTA reachable, configurer {@code jurika.email.provider=log} pour
 * basculer sur {@link LogEmailSender} (workspace code + MDP temp visibles dans
 * les logs auth-service au lieu de partir vers un MailHog/SMTP injoignable).
 */
@Component
@ConditionalOnProperty(name = "jurika.email.provider", havingValue = "smtp", matchIfMissing = true)
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final String fromAddress;
    private final String fromName;
    private final String supportAddress;

    public SmtpEmailSender(JavaMailSender mailSender,
                           SpringTemplateEngine templateEngine,
                           @Value("${jurika.email.from:}") String fromAddress,
                           @Value("${jurika.email.from-name:JURIKA}") String fromName,
                           @Value("${jurika.email.support:}") String supportAddress) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        // Lot L6 (E5) : contact cite dans les courriels (alertes de securite...). jurika.ma n'est
        // pas deploye : a defaut de JURIKA_SUPPORT_EMAIL, l'adresse d'expediteur verifiee.
        this.supportAddress = (supportAddress == null || supportAddress.isBlank()) ? fromAddress : supportAddress;
    }

    @Override
    public void sendTemplated(String to, String subject, String templateName, Map<String, Object> variables) {
        // Lot L6 (E5) : pas d'expediteur par defaut (jurika.ma n'est pas deploye avant la mise en
        // production). Sans SMTP_FROM, rien ne part et l'erreur le dit.
        if (fromAddress == null || fromAddress.isBlank()) {
            log.error("Courriel '{}' non envoye : aucune adresse d'expediteur (SMTP_FROM)", templateName);
            throw new EmailDeliveryException("Aucune adresse d'expediteur configuree (SMTP_FROM) : courriel non envoye", null);
        }
        Context ctx = new Context();
        ctx.setVariables(variables);
        if (!variables.containsKey("supportEmail")) ctx.setVariable("supportEmail", supportAddress);
        String html = templateEngine.process("email/" + templateName, ctx);

        MimeMessage message = mailSender.createMimeMessage();
        try {
            // Recette R3 : composition commune (multipart/related, texte brut + HTML, logo en
            // ligne par Content-ID ; aucune image de decor, aucune partie orpheline).
            CourrielJurika.composer(message, fromAddress, fromName, to, subject, html, List.of());
            mailSender.send(message);
            log.info("Email '{}' envoye a {}", templateName, to);
        } catch (MessagingException | java.io.UnsupportedEncodingException
                 | IllegalArgumentException | IllegalStateException e) {
            log.error("Echec envoi email '{}' a {} : {}", templateName, to, e.getMessage());
            throw new EmailDeliveryException("Echec envoi email a " + to, e);
        }
    }
}
