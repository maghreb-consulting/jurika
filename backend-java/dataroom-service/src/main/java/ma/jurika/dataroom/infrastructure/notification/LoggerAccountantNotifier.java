package ma.jurika.dataroom.infrastructure.notification;

import ma.jurika.dataroom.domain.port.AccountantNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adapter de developpement : logge la notification au lieu d'envoyer un vrai email.
 * En prod, remplacer par un adapter SMTP (Resend / SES / Mailgun).
 */
@Component
public class LoggerAccountantNotifier implements AccountantNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggerAccountantNotifier.class);

    @Override
    @Async
    public void notifyUpload(String toEmail, UUID dossierId, String raisonSociale,
                              short annee, String categorie, String documentName, UUID uploaderId) {
        log.info("[ACCOUNTANT-NOTIF] to={} dossier={} ({}), {}/{}, doc=\"{}\", uploaderId={}",
                toEmail, dossierId, raisonSociale, annee, categorie, documentName, uploaderId);
    }
}
