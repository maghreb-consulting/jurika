package ma.jurika.billing.application.contrat;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Lot L7 (E6) : refus nomme du contrat d'abonnement (code stable, message en francais, donnees
 * manquantes nommees). Traduit en reponse HTTP par {@code ContratExceptionHandler}.
 */
public class ContratException extends RuntimeException {

    private final HttpStatus statut;
    private final String code;
    private final List<String> donnees;

    public ContratException(HttpStatus statut, String code, String message, List<String> donnees) {
        super(message);
        this.statut = statut;
        this.code = code;
        this.donnees = donnees == null ? List.of() : List.copyOf(donnees);
    }

    public static ContratException invalide(String code, String message) {
        return new ContratException(HttpStatus.UNPROCESSABLE_ENTITY, code, message, List.of());
    }

    public static ContratException conflit(String code, String message) {
        return new ContratException(HttpStatus.CONFLICT, code, message, List.of());
    }

    public static ContratException introuvable(String code, String message) {
        return new ContratException(HttpStatus.NOT_FOUND, code, message, List.of());
    }

    public HttpStatus statut() { return statut; }
    public String code() { return code; }
    public List<String> donnees() { return donnees; }
}
