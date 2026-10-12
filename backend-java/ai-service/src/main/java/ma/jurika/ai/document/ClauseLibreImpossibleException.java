package ma.jurika.ai.document;

/**
 * Lot L4d (E10) : une clause libre ne peut pas etre placee a l'emplacement choisi sans risque
 * pour la numerotation ou les renvois du document. La generation est refusee avec ce message
 * (jamais un document aux renvois faux).
 */
public class ClauseLibreImpossibleException extends RuntimeException {

    private final String templateCode;

    public ClauseLibreImpossibleException(String templateCode, String message) {
        super(message);
        this.templateCode = templateCode;
    }

    public String templateCode() {
        return templateCode;
    }
}
