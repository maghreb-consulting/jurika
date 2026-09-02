package ma.jurika.auth.infrastructure.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import ma.jurika.auth.domain.port.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
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

    /** Content-ID reference par les templates via <img src="cid:jurika-logo">. */
    private static final String LOGO_CID = "jurika-logo";
    /** Embleme embarque (specifique email), present dans jurika-common (donc sur le classpath). */
    private static final String LOGO_RESOURCE = "branding/jurika-email-logo.png";
    /** Motif zellige marocain en fond d'en-tete, reference via background-image:url('cid:jurika-zellij'). */
    private static final String ZELLIJ_CID = "jurika-zellij";
    private static final String ZELLIJ_RESOURCE = "branding/jurika-zellij.png";

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final String fromAddress;
    private final String fromName;

    public SmtpEmailSender(JavaMailSender mailSender,
                           SpringTemplateEngine templateEngine,
                           @Value("${jurika.email.from:noreply@jurika.ma}") String fromAddress,
                           @Value("${jurika.email.from-name:JURIKA}") String fromName) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    public void sendTemplated(String to, String subject, String templateName, Map<String, Object> variables) {
        Context ctx = new Context();
        ctx.setVariables(variables);
        String html = templateEngine.process("email/" + templateName, ctx);

        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            attachInlineAssets(helper);
            mailSender.send(message);
            log.info("Email '{}' envoye a {}", templateName, to);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Echec envoi email '{}' a {} : {}", templateName, to, e.getMessage());
            throw new EmailDeliveryException("Echec envoi email a " + to, e);
        }
    }

    /**
     * Attache les assets inline (Content-ID) references par les templates :
     * l'embleme ({@code cid:jurika-logo}) et le motif zellige de l'en-tete
     * ({@code cid:jurika-zellij}). Evite toute dependance a une URL externe
     * (bloquee par Gmail/Outlook). Best-effort : une ressource absente n'echoue
     * pas l'envoi — le wordmark texte et le navy plein restent la garantie
     * visuelle. Doit etre appele APRES {@code setText(...)}.
     */
    private void attachInlineAssets(MimeMessageHelper helper) {
        addInlineIfPresent(helper, LOGO_CID, LOGO_RESOURCE);
        addInlineIfPresent(helper, ZELLIJ_CID, ZELLIJ_RESOURCE);
    }

    private void addInlineIfPresent(MimeMessageHelper helper, String cid, String resource) {
        try {
            ClassPathResource res = new ClassPathResource(resource);
            if (res.exists()) {
                helper.addInline(cid, res, "image/png");
            }
        } catch (MessagingException e) {
            log.warn("Asset CID non attache ({}) : {}", resource, e.getMessage());
        }
    }
}
