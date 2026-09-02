package ma.jurika.auth.infrastructure.email;

import ma.jurika.auth.domain.port.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Fallback EmailSender pour le dev local quand aucun MTA n'est joignable
 * (MailHog absent, SMTP_HOST non configure...). Active via
 * {@code jurika.email.provider=log} dans {@code application-dev.yml} ou via
 * la variable d'env {@code JURIKA_EMAIL_PROVIDER=log}.
 *
 * <p>Le contenu critique (workspace code, MDP temporaire, lien de verification)
 * est emis a {@code WARN} avec un marqueur {@code [DEV EMAIL]} pour que le
 * developpeur puisse extraire les credentials depuis la sortie du service au
 * lieu d'avoir un signup silencieusement casse.
 *
 * <p>⚠️ NE PAS activer en production : le MDP temporaire en clair dans les
 * logs serait une fuite. Le bean est strictement opt-in via property.
 */
@Component
@ConditionalOnProperty(name = "jurika.email.provider", havingValue = "log")
public class LogEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

    @Override
    public void sendTemplated(String to, String subject, String templateName, Map<String, Object> variables) {
        // Variables sensibles affichees explicitement quand presentes — sinon dump
        // generique des cles non-secretes pour les autres templates (ticket-assigned,
        // login-new-device, etc.).
        String workspaceCode = stringOrNull(variables.get("workspaceCode"));
        String tempPassword = stringOrNull(variables.get("temporaryPassword"));
        String verifUrl = stringOrNull(variables.get("verificationUrl"));

        if (workspaceCode != null || tempPassword != null || verifUrl != null) {
            log.warn("[DEV EMAIL] to={} template={} subject=\"{}\" workspaceCode={} tempPassword={} verificationUrl={}",
                    to, templateName, subject,
                    workspaceCode == null ? "-" : workspaceCode,
                    tempPassword == null ? "-" : tempPassword,
                    verifUrl == null ? "-" : verifUrl);
        } else {
            log.warn("[DEV EMAIL] to={} template={} subject=\"{}\" keys={}",
                    to, templateName, subject, variables.keySet());
        }
    }

    private static String stringOrNull(Object o) {
        return o == null ? null : o.toString();
    }
}
