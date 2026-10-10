package ma.jurika.ai.workflow.identity;

/**
 * Lot L3 (motif 9) : les donnees de la societe n'ont pas pu etre lues. Avant L3, l'erreur
 * etait avalee (map vide) et l'acte partait sans elles ; la generation echoue
 * desormais, avec un message clair (503).
 */
public class IdentiteSocieteIndisponibleException extends RuntimeException {

    public IdentiteSocieteIndisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
