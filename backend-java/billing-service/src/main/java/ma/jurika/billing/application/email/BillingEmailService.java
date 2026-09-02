package ma.jurika.billing.application.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import ma.jurika.billing.infrastructure.stripe.BillingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Sprint 12 T10 — service envoi emails billing avec rendu Thymeleaf.
 *
 * <p>5 templates :
 *  - facture-disponible      (invoice.payment_succeeded -> RG-BL05)
 *  - paiement-echec          (invoice.payment_failed   -> RG-BL06)
 *  - conversion-active       (checkout.session.completed -> apres conversion)
 *  - annulation              (customer.subscription.deleted -> RG-BL07)
 *  - reactivation            (resume depuis Customer Portal)
 *
 * <p>RG-BL16 : tous les emails en FR.
 * Envoyes @Async pour ne pas bloquer le webhook handler. Si SMTP KO, log
 * + skip silencieux (l'event Stripe reste persiste cote DB, retry possible).
 */
@Service
public class BillingEmailService {

    private static final Logger log = LoggerFactory.getLogger(BillingEmailService.class);

    /** Content-ID reference par les templates via <img src="cid:jurika-logo">. */
    private static final String LOGO_CID = "jurika-logo";
    /** Embleme embarque (specifique email), present dans jurika-common (donc sur le classpath). */
    private static final String LOGO_RESOURCE = "branding/jurika-email-logo.png";
    /** Motif zellige marocain en fond d'en-tete, reference via background-image:url('cid:jurika-zellij'). */
    private static final String ZELLIJ_CID = "jurika-zellij";
    private static final String ZELLIJ_RESOURCE = "branding/jurika-zellij.png";

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final BillingProperties props;

    public BillingEmailService(JavaMailSender mailSender,
                                TemplateEngine templateEngine,
                                BillingProperties props) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.props = props;
    }

    @Async
    public void sendInvoiceAvailable(String to, Map<String, Object> vars) {
        send(to, "Votre facture JURIKA est disponible", "email/facture-disponible", vars);
    }

    @Async
    public void sendPaymentFailed(String to, Map<String, Object> vars) {
        send(to, "[URGENT] Echec de paiement — mettez a jour votre carte", "email/paiement-echec", vars);
    }

    @Async
    public void sendConversionActive(String to, Map<String, Object> vars) {
        send(to, "Bienvenue dans JURIKA — votre abonnement est actif", "email/conversion-active", vars);
    }

    @Async
    public void sendCancellation(String to, Map<String, Object> vars) {
        send(to, "Confirmation d'annulation de votre abonnement JURIKA", "email/annulation", vars);
    }

    @Async
    public void sendReactivation(String to, Map<String, Object> vars) {
        send(to, "Votre abonnement JURIKA est reactive", "email/reactivation", vars);
    }

    private void send(String to, String subject, String templatePath, Map<String, Object> vars) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // multipart=true (MIXED_RELATED) : requis pour l'embleme inline CID.
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(props.getFromEmail());
            helper.setTo(to);
            helper.setSubject(subject);

            Context ctx = new Context();
            vars.forEach(ctx::setVariable);
            String html = templateEngine.process(templatePath, ctx);
            helper.setText(html, true);
            attachInlineAssets(helper);

            mailSender.send(message);
            log.info("Email billing envoye to={} template={}", to, templatePath);
        } catch (MessagingException | RuntimeException e) {
            log.warn("Echec envoi email billing to={} template={} : {} (skip silencieux)",
                    to, templatePath, e.getMessage());
        }
    }

    /**
     * Attache les assets inline (Content-ID) references par les templates : embleme
     * ({@code cid:jurika-logo}) et motif zellige de l'en-tete ({@code cid:jurika-zellij}).
     * Best-effort : ressource absente = pas d'echec (wordmark texte + navy plein en repli).
     * Doit etre appele APRES {@code setText(...)}.
     */
    private void attachInlineAssets(MimeMessageHelper helper) throws MessagingException {
        addInlineIfPresent(helper, LOGO_CID, LOGO_RESOURCE);
        addInlineIfPresent(helper, ZELLIJ_CID, ZELLIJ_RESOURCE);
    }

    private void addInlineIfPresent(MimeMessageHelper helper, String cid, String resource)
            throws MessagingException {
        ClassPathResource res = new ClassPathResource(resource);
        if (res.exists()) {
            helper.addInline(cid, res, "image/png");
        }
    }
}
