package ma.jurika.billing.application;

import ma.jurika.billing.api.dto.BillingDtos;
import ma.jurika.billing.infrastructure.config.BillingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Lot L7 (E5) : forfait Sur Devis = devis etabli par JURIKA (RG-SOU-01). POST /api/v1/billing/contact-sales
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

    /** Lot L7 : BILLING_SALES_EMAIL, a defaut l'adresse d'expedition verifiee ; jamais un domaine non deploye. */
    private String destinataire() {
        return billingProperties.adresseCommerciale();
    }

    public BillingDtos.ContactSalesResponse send(UUID workspaceId, BillingDtos.ContactSalesRequest req) {
        String a = destinataire();
        if (a == null) {
            log.error("Contact-sales non envoye workspace={} : aucune adresse (BILLING_SALES_EMAIL, SMTP_FROM)", workspaceId);
            return new BillingDtos.ContactSalesResponse(false,
                    "La demande n\u2019a pas pu \u00eatre envoy\u00e9e : l\u2019adresse de JURIKA n\u2019est pas encore configur\u00e9e. R\u00e9essayez plus tard.");
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(billingProperties.getFromEmail());
            message.setTo(a);
            message.setSubject("[JURIKA Sur Devis] Demande de devis : " + safe(req.workspaceName()));
            message.setText(buildBody(workspaceId, req));
            mailSender.send(message);
            log.info("Contact-sales envoye workspace={} contact={}", workspaceId, req.contactEmail());
            return new BillingDtos.ContactSalesResponse(true,
                    "Demande re\u00e7ue. L\u2019\u00e9quipe commerciale de JURIKA reviendra vers vous avec un devis.");
        } catch (RuntimeException e) {
            // Best-effort : si SMTP KO en dev, on log et on retourne accepted=false
            // pour que l'UI puisse afficher un fallback mailto:.
            log.warn("Echec envoi contact-sales workspace={} : {}", workspaceId, e.getMessage());
            return new BillingDtos.ContactSalesResponse(false,
                    "Erreur temporaire : la demande n\u2019a pas pu \u00eatre envoy\u00e9e. \u00c9crivez-nous directement \u00e0 " + a + ".");
        }
    }

    private String buildBody(UUID workspaceId, BillingDtos.ContactSalesRequest req) {
        return """
                Nouvelle demande de devis (forfait Sur Devis) depuis JURIKA.

                Workspace ID  : %s
                Cabinet       : %s
                Contact       : %s <%s>
                Telephone     : %s

                Message :
                %s

                ---
                Envoyee depuis billing-service. Action attendue : revenir vers le contact avec un devis.
                Le montant calcule sur l'usage reel (RG-PLN-05) est presente dans l'ecran Sur Devis du super-admin.
                """.formatted(
                workspaceId,
                safe(req.workspaceName()),
                safe(req.contactName()),
                safe(req.contactEmail()),
                safe(req.phone()),
                safe(req.message())
        );
    }

    private static String safe(String s) { return s == null ? "(non renseign\u00e9)" : s; }
}
