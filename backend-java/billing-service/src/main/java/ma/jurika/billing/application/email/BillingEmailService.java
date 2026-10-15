package ma.jurika.billing.application.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import ma.jurika.billing.infrastructure.config.BillingProperties;
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
 *  - facture-disponible      (paiement confirme -> RG-BL05)
 *  - conversion-active       (premiere souscription payee)
 *  - annulation              (fin d'abonnement -> RG-BL07)
 *  - reactivation            (reprise apres archivage)
 *
 * <p>RG-BL16 : tous les emails en FR.
 * Envoyes @Async pour ne pas bloquer l'appelant. Si SMTP KO, l'echec est
 * journalise (le paiement reste persiste en base).
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
    public void sendConversionActive(String to, Map<String, Object> vars) {
        send(to, "Bienvenue dans JURIKA : votre abonnement est actif", "email/conversion-active", vars);
    }

    @Async
    public void sendCancellation(String to, Map<String, Object> vars) {
        send(to, "Confirmation d\u2019annulation de votre abonnement JURIKA", "email/annulation", vars);
    }

    @Async
    public void sendReactivation(String to, Map<String, Object> vars) {
        send(to, "Votre abonnement JURIKA est r\u00e9activ\u00e9", "email/reactivation", vars);
    }

    /**
     * Lot L7 (E8), article 4.4 du contrat : rappel d'une echeance impayee, envoye SANS
     * {@code @Async} : le resultat est rendu a l'appelant, qui le journalise (envoye ou echec) et
     * ne suspend jamais l'acces sans rappel envoye. Jamais avale.
     *
     * @return null si le courriel est parti, sinon le motif de l'echec
     */
    public String envoyerRappelImpaye(String to, Map<String, Object> vars) {
        if (to == null || to.isBlank()) {
            return "Adresse \u00e9lectronique du cabinet absente";
        }
        if (props.getFromEmail() == null || props.getFromEmail().isBlank()) {
            return "Exp\u00e9diteur non configur\u00e9 (SMTP_FROM)";
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(props.getFromEmail());
            helper.setTo(to);
            helper.setSubject("Rappel : \u00e9ch\u00e9ance de votre abonnement JURIKA non r\u00e9gl\u00e9e");
            Context ctx = new Context();
            vars.forEach(ctx::setVariable);
            helper.setText(templateEngine.process("email/rappel-impaye", ctx), true);
            attachInlineAssets(helper);
            mailSender.send(message);
            log.info("Rappel d'echeance impayee envoye to={}", to);
            return null;
        } catch (Exception e) {
            log.warn("Echec du rappel d'echeance impayee to={} : {}", to, e.getMessage());
            String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return m.length() > 500 ? m.substring(0, 500) : m;
        }
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
