package ma.jurika.ai.document.corpus;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Lot L2 : un gabarit du corpus tel que charge.
 *
 * @param modele           ligne de l'index
 * @param fichier          chemin absolu du gabarit Word (lecture seule)
 * @param empreinte        SHA-256 (hexadecimal) du fichier au chargement ; verifiee a chaque rendu
 * @param variables        variables {@code $NOM} employees
 * @param erreursStructure defauts d'appariement : le gabarit n'est pas rendable
 * @param avertissements   variables hors dictionnaire, marqueurs residuels (a rapporter)
 */
public record GabaritCorpus(ModeleCorpus modele, Path fichier, String empreinte, Set<String> variables,
                            List<String> erreursStructure, List<String> avertissements) {

    public GabaritCorpus {
        variables = Set.copyOf(variables);
        erreursStructure = List.copyOf(erreursStructure);
        avertissements = List.copyOf(avertissements);
    }

    public boolean rendable() {
        return erreursStructure.isEmpty();
    }
}
