package ma.jurika.billing.application;

import ma.jurika.billing.api.dto.BillingDtos;
import ma.jurika.billing.infrastructure.stripe.BillingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Sprint 12 — Plan Enterprise = devis manuel. POST /api/v1/billing/contact-sales
 * envoie un mail simple a l'equipe ventes. RG-BL10.
 *
 * <p>Pas d'integration CRM Sprint 12 — la lead arrive juste dans la boite
 * mail configuree (BILLING_SALES_EMAIL).
 */
@Service
public class ContactSalesUseCase {

    private static final Logger log = LoggerFactory.getLogger(ContactSalesUseCase.class);

    private final JavaMailSender mailSender;
    private final BillingProperties billingProperties;

    public ContactSalesUseCase(JavaMailSender mailSender, BillingProperties billingProperties) {
        this.mailSender = mailSender;
        this.billingProperties = billingProperties;
    }

    public BillingDtos.ContactSalesResponse send(UUID workspaceId, BillingDtos.ContactSalesRequest req) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(billingProperties.getFromEmail());
            message.setTo(billingProperties.getSalesEmail());
            message.setSubject("[JURIKA Enterprise] Demande contact — " + safe(req.workspaceName()));
            message.setText(buildBody(workspaceId, req));
            mailSender.send(message);
            log.info("Contact-sales envoye workspace={} contact={}", workspaceId, req.contactEmail());
            return new BillingDtos.ContactSalesResponse(true,
                    "Demande recue. L'equipe commerciale reviendra vers vous sous 24h ouvrees.");
        } catch (RuntimeException e) {
            // Best-effort : si SMTP KO en dev, on log et on retourne accepted=false
            // pour que l'UI puisse afficher un fallback mailto:.
            log.warn("Echec envoi contact-sales workspace={} : {}", workspaceId, e.getMessage());
            return new BillingDtos.ContactSalesResponse(false,
                    "Erreur temporaire. Ecris-nous directement a " + billingProperties.getSalesEmail());
        }
    }

    private String buildBody(UUID workspaceId, BillingDtos.ContactSalesRequest req) {
        return """
                Nouvelle demande de contact Enterprise depuis JURIKA.

                Workspace ID  : %s
                Cabinet       : %s
                Contact       : %s <%s>
                Telephone     : %s

                Message :
                %s

                ---
                Envoyee depuis billing-service. Action attendue : revenir vers le contact sous 24h ouvrees
                avec un devis (Sprint 12 n'utilise PAS Stripe pour Enterprise — facturation manuelle).
                """.formatted(
                workspaceId,
                safe(req.workspaceName()),
                safe(req.contactName()),
                safe(req.contactEmail()),
                safe(req.phone()),
                safe(req.message())
        );
    }

    private static String safe(String s) { return s == null ? "(non renseigne)" : s; }
}
