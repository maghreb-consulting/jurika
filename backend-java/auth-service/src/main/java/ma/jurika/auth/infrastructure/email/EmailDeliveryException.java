package ma.jurika.auth.infrastructure.email;

/**
 * Levee quand l'envoi SMTP echoue (probleme reseau, auth, destinataire invalide).
 * <p>
 * Cette exception est runtime : on ne veut pas bloquer la transaction principale
 * (creation user / verification) si l'email part en erreur. Le caller est responsable
 * de la strategie de retry / fallback.
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
