package ma.jurika.ai.document;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Lot L2 : aucun gabarit pour ce code, ni dans le corpus ni dans le classpath.
 * Remplace l'ancien "document de remplacement" (motif 9 : un document produit
 * sans son modele passait pour un acte). Rendu en 404 par Spring MVC.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class GabaritIntrouvableException extends RuntimeException {

    public GabaritIntrouvableException(String templateCode) {
        super("Gabarit introuvable (corpus et classpath) : " + templateCode);
    }
}
