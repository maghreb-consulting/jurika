package ma.jurika.ai.rag;

import java.util.List;

/**
 * RG-COM-04 : la question contient une donnee personnelle ; elle n'est ni envoyee au modele
 * ni journalisee. Le message ne porte que la nature des donnees, jamais leur valeur.
 */
public class DonneePersonnelleDetecteeException extends RuntimeException {

    private final transient List<FiltreDonneesPersonnelles.Nature> natures;

    public DonneePersonnelleDetecteeException(List<FiltreDonneesPersonnelles.Nature> natures) {
        super("Question refusee : donnee personnelle detectee (" + natures + ")");
        this.natures = List.copyOf(natures);
    }

    public List<FiltreDonneesPersonnelles.Nature> natures() {
        return natures;
    }
}
