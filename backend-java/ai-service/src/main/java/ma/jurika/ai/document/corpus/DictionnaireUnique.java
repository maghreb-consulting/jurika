package ma.jurika.ai.document.corpus;

import java.util.Map;
import java.util.Set;

/**
 * Lot L2 : dictionnaire unique des variables du corpus et ses alias.
 *
 * @param variables noms des variables (avec le {@code $}), alias compris
 * @param alias     alias -> nom canonique (onglet "Alias") : le moteur recoit la
 *                  meme valeur sous les deux noms (00_LISEZ_MOI.md, Conventions)
 */
public record DictionnaireUnique(Set<String> variables, Map<String, String> alias) {

    public DictionnaireUnique {
        variables = Set.copyOf(variables);
        alias = Map.copyOf(alias);
    }

    /** Variable connue du dictionnaire, comme nom ou comme alias. */
    public boolean connue(String variable) {
        return variables.contains(variable) || alias.containsKey(variable);
    }
}
