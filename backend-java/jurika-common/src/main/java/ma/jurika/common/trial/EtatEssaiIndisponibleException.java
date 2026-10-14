package ma.jurika.common.trial;

/**
 * Lot L7 (E4) : l'etat d'essai d'un cabinet n'a pas pu etre lu (auth-service
 * injoignable, reponse illisible). Les controles de l'essai qui en dependent
 * (telechargement, lecture seule, limites) refusent plutot que de laisser
 * passer : aucun controle silencieusement inoperant.
 */
public class EtatEssaiIndisponibleException extends RuntimeException {

    public EtatEssaiIndisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
