package ma.jurika.ai.document;

import java.util.List;

/**
 * Lot L3 : generation refusee parce qu'une donnee INTERNE manque (regle des
 * variables). Porte chaque donnee manquante, nommee pour l'employe : le libelle du
 * champ (dictionnaire unique), le nom de la variable et la phrase ou elle s'imprime.
 * Rendue en 422 avec la liste structuree ({@code GenerationExceptionHandler}).
 */
public class GenerationRefuseeException extends RuntimeException {

    /** Une donnee manquante, nommee. {@code libelle} vaut le nom de la variable si le dictionnaire n'en donne pas. */
    public record DonneeManquante(String variable, String libelle, String endroit) {}

    private final String templateCode;
    private final transient List<DonneeManquante> donnees;

    public GenerationRefuseeException(String templateCode, String message, List<DonneeManquante> donnees) {
        super(message);
        this.templateCode = templateCode;
        this.donnees = List.copyOf(donnees);
    }

    public String templateCode() {
        return templateCode;
    }

    public List<DonneeManquante> donnees() {
        return donnees;
    }
}
