package ma.jurika.ai.document.corpus;

import java.util.Map;
import java.util.Set;

/**
 * Lot L2 : dictionnaire unique des variables du corpus et ses alias.
 *
 * @param variables noms des variables (avec le {@code $}), alias compris
 * @param alias     alias -> nom canonique (onglet "Alias") : le moteur recoit la
 *                  meme valeur sous les deux noms (00_LISEZ_MOI.md, Conventions)
 * @param libelles  lot L3 : nom -> libelle du champ a l'ecran (colonne "Libelle du champ a
 *                  l'ecran"), pour nommer une donnee manquante a l'utilisateur
 */
public record DictionnaireUnique(Set<String> variables, Map<String, String> alias, Map<String, String> libelles) {

    public DictionnaireUnique {
        variables = Set.copyOf(variables);
        alias = Map.copyOf(alias);
        libelles = Map.copyOf(libelles);
    }

    public DictionnaireUnique(Set<String> variables, Map<String, String> alias) {
        this(variables, alias, Map.of());
    }

    /** Nom canonique d'une variable (avec le {@code $}), alias resolu. */
    public String canonique(String variable) {
        String v = variable.startsWith("$") ? variable : "$" + variable;
        return alias.getOrDefault(v, v);
    }

    /**
     * Lot L3 : libelle a l'ecran de la variable (alias resolu), ou {@code null} si le
     * dictionnaire n'en donne pas : l'appelant affiche alors le nom de la variable.
     */
    public String libelle(String variable) {
        String l = libelles.get(canonique(variable));
        return l == null || l.isBlank() ? null : l;
    }

    /** Variable connue du dictionnaire, comme nom ou comme alias. */
    public boolean connue(String variable) {
        return variables.contains(variable) || alias.containsKey(variable);
    }
}
