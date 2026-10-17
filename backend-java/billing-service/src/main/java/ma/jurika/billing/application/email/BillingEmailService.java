package ma.jurika.billing.application.email;

import jakarta.mail.internet.MimeMessage;
import ma.jurika.billing.infrastructure.config.BillingProperties;
import ma.jurika.common.courriel.CourrielJurika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Map;

/**
 * Sprint 12 T10 \u2014 service envoi emails billing avec rendu Thymeleaf.
 *
 * <p>Gabarits :
 *  - facture-disponible      (paiement confirme -> RG-BL05), PDF de la facture en piece jointe
 *  - conversion-active       (premiere souscription payee)
 *  - annulation              (fin d'abonnement -> RG-BL07)
 *  - reactivation            (reprise apres archivage)
 *  - rappel-impaye           (lot L7, article 4.4 du contrat)
 *
 * <p>RG-BL16 : tous les emails en FR. Recette R3 : composition commune (CourrielJurika) :
 * mise en page commune, logo en ligne par Content-ID, texte brut, aucune image de decor.
 */
@Service
public class BillingEmailService {

    private static final Logger log = LoggerFactory.getLogger(BillingEmailService.class);

    public static final String GABARIT_FACTURE = "email/facture-disponible";

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

    /**
     * Facture disponible, avec son PDF en piece jointe (seule piece jointe attendue des courriels).
     * Remarque (recette R3) : aucun appelant aujourd'hui ; l'envoi au paiement reste a brancher.
     */
    @Async
    public void sendInvoiceAvailable(String to, Map<String, Object> vars, List<CourrielJurika.PieceJointe> pdf) {
        send(to, "Votre facture JURIKA est disponible", GABARIT_FACTURE, vars, pdf);
    }

    @Async
    public void sendConversionActive(String to, Map<String, Object> vars) {
        send(to, "Bienvenue dans JURIKA : votre abonnement est actif", "email/conversion-active", vars, List.of());
    }

    @Async
    public void sendCancellation(String to, Map<String, Object> vars) {
        send(to, "Confirmation d\u2019annulation de votre abonnement JURIKA", "email/annulation", vars, List.of());
    }

    @Async
    public void sendReactivation(String to, Map<String, Object> vars) {
        send(to, "Votre abonnement JURIKA est r\u00e9activ\u00e9", "email/reactivation", vars, List.of());
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
        String erreur = envoyer(to, "Rappel : \u00e9ch\u00e9ance de votre abonnement JURIKA non r\u00e9gl\u00e9e",
                "email/rappel-impaye", vars, List.of());
        if (erreur == null) log.info("Rappel d'echeance impayee envoye to={}", to);
        else log.warn("Echec du rappel d'echeance impayee to={} : {}", to, erreur);
        return erreur;
    }

    /**
     * Envoi synchrone d'un courriel de billing (recette R3 : utilise aussi par la route des
     * exemplaires). Jamais avale : rend null si le courriel est parti, sinon le motif.
     */
    public String envoyer(String to, String objet, String gabarit, Map<String, Object> vars,
                          List<CourrielJurika.PieceJointe> pieces) {
        if (to == null || to.isBlank()) {
            return "Adresse \u00e9lectronique du destinataire absente";
        }
        if (props.getFromEmail() == null || props.getFromEmail().isBlank()) {
            return "Exp\u00e9diteur non configur\u00e9 (SMTP_FROM)";
        }
        try {
            Context ctx = new Context();
            vars.forEach(ctx::setVariable);
            String html = templateEngine.process(gabarit, ctx);
            MimeMessage message = mailSender.createMimeMessage();
            CourrielJurika.composer(message, props.getFromEmail(), props.getFromName(), to, objet, html, pieces);
            mailSender.send(message);
            return null;
        } catch (Exception e) {
            String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return m.length() > 500 ? m.substring(0, 500) : m;
        }
    }

    private void send(String to, String subject, String templatePath, Map<String, Object> vars,
                      List<CourrielJurika.PieceJointe> pieces) {
        String erreur = envoyer(to, subject, templatePath, vars, pieces);
        if (erreur == null) {
            log.info("Email billing envoye to={} template={}", to, templatePath);
        } else {
            log.warn("Echec envoi email billing to={} template={} : {}", to, templatePath, erreur);
        }
    }
}
