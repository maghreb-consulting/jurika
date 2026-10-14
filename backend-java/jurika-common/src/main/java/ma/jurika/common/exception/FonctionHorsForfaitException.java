package ma.jurika.common.exception;

import ma.jurika.common.billing.FonctionForfait;

/**
 * Lot L7 (E5), CDC 4.5 : fonction non comprise dans le forfait du cabinet. Controlee par le serveur
 * (P6) ; HTTP 402 (meme famille que le quota atteint), avec un message qui nomme la fonction et le
 * forfait qui la donne.
 */
public class FonctionHorsForfaitException extends BusinessException {

    private final FonctionForfait fonction;

    public FonctionHorsForfaitException(FonctionForfait fonction, String message) {
        super("FONCTION_HORS_FORFAIT", message);
        this.fonction = fonction;
    }

    public FonctionForfait fonction() {
        return fonction;
    }
}
