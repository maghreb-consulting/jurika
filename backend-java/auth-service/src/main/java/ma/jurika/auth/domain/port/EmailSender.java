package ma.jurika.auth.domain.port;

import java.util.Map;

/**
 * Port d'envoi d'emails transactionnels (bienvenue, verification, MDP temp).
 * <p>
 * Implementation : {@code infrastructure.email.SmtpEmailSender} (SMTP + Thymeleaf).
 * Test : substituable par un mock qui memorise les emails envoyes.
 */
public interface EmailSender {

    /**
     * Envoi d'un email HTML rendu via un template Thymeleaf.
     *
     * @param to           destinataire
     * @param subject      sujet
     * @param templateName nom du template (sans extension, ex: "welcome")
     * @param variables    variables substituees dans le template
     */
    void sendTemplated(String to, String subject, String templateName, Map<String, Object> variables);
}
