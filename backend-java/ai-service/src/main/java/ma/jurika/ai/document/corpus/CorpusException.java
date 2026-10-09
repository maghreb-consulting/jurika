package ma.jurika.ai.document.corpus;

/**
 * Lot L2 : erreur BLOQUANTE de lecture ou de controle du corpus (fichier absent,
 * classeur illisible, en-tete manquant, code en double...). Le service refuse de
 * demarrer plutot que de servir un corpus incomplet.
 */
public class CorpusException extends RuntimeException {

    public CorpusException(String message) {
        super(message);
    }

    public CorpusException(String message, Throwable cause) {
        super(message, cause);
    }
}
