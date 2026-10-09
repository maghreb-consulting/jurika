package ma.jurika.ai.document.corpus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lot L2 : controles d'integration d'un gabarit (annexe technique, section 2),
 * rejoues a chaque chargement du corpus.
 * <ul>
 *   <li>appariement des boucles (DEBUT / FIN BOUCLE, meme nom, bien imbriquees) et des
 *       conditions (SI ... SINON SI / SINON ... FIN SI) : un defaut rend le gabarit
 *       NON RENDABLE (erreur de structure) ;</li>
 *   <li>variables {@code $NOM} absentes du dictionnaire unique : avertissement ;</li>
 *   <li>marqueurs residuels (restes de syntaxe de gabarit, TODO / XXX en mots
 *       entiers) : avertissement.</li>
 * </ul>
 * Les avertissements se rapportent au cabinet ; ils ne bloquent rien (CLAUDE.md :
 * les incoherences mecaniques sont attendues et se rapportent).
 */
public final class ControlesIntegration {

    /** Marqueurs du moteur (annexe technique, section 2). */
    static final Pattern MARQUEUR = Pattern.compile(
            "\u25bc\\s*D\u00c9BUT BOUCLE\\s*[\u2014-]\\s*([A-Z0-9_]+)"
            + "|\u25b2\\s*FIN BOUCLE\\s*[\u2014-]\\s*([A-Z0-9_]+)"
            + "|\u25c7\\s*SINON SI\\b"
            + "|\u25c7\\s*SINON\\b"
            + "|\u25c7\\s*SI\\b"
            + "|\u25c6\\s*FIN SI\\b");
    static final Pattern VARIABLE = Pattern.compile("\\$[A-Z][A-Z0-9_]*");
    static final Pattern RESIDUEL = Pattern.compile(
            "\\$\\{|\\{\\{|\\}\\}|\\[\\[|\\]\\]|<<|>>|\\bTODO\\b|\\bXXX\\b");

    private ControlesIntegration() {
    }

    /** Resultat des controles d'un gabarit. */
    public record Resultat(Set<String> variables, List<String> erreursStructure, List<String> avertissements) {
    }

    public static Resultat controler(List<String> paragraphes, DictionnaireUnique dictionnaire) {
        Set<String> variables = new LinkedHashSet<>();
        List<String> erreurs = new ArrayList<>();
        List<String> avertissements = new ArrayList<>();
        Deque<String> pile = new ArrayDeque<>();
        int numero = 0;
        for (String p : paragraphes) {
            numero++;
            Matcher m = MARQUEUR.matcher(p);
            while (m.find()) {
                String s = m.group();
                if (m.group(1) != null) {
                    pile.push("BOUCLE " + m.group(1));
                } else if (m.group(2) != null) {
                    String attendu = "BOUCLE " + m.group(2);
                    if (pile.isEmpty() || !pile.peek().equals(attendu)) {
                        erreurs.add("paragraphe " + numero + " : FIN BOUCLE " + m.group(2)
                                + " sans DEBUT correspondant" + (pile.isEmpty() ? "" : " (ouvert : " + pile.peek() + ")"));
                    } else {
                        pile.pop();
                    }
                } else if (s.startsWith("\u25c6")) {
                    if (pile.isEmpty() || !pile.peek().equals("SI")) {
                        erreurs.add("paragraphe " + numero + " : FIN SI sans SI ouvert");
                    } else {
                        pile.pop();
                    }
                } else if (s.matches("\u25c7\\s*SINON.*")) {
                    if (pile.isEmpty() || !pile.peek().equals("SI")) {
                        erreurs.add("paragraphe " + numero + " : " + s.trim() + " hors d'un SI");
                    }
                } else {
                    pile.push("SI");
                }
            }
            Matcher v = VARIABLE.matcher(p);
            while (v.find()) {
                variables.add(v.group());
            }
            Matcher r = RESIDUEL.matcher(p);
            if (r.find()) {
                avertissements.add("paragraphe " + numero + " : marqueur residuel \"" + r.group() + "\"");
            }
        }
        for (String ouvert : pile) {
            erreurs.add(ouvert + " jamais ferme");
        }
        for (String var : variables) {
            if (!dictionnaire.connue(var)) {
                avertissements.add("variable absente du dictionnaire : " + var);
            }
        }
        return new Resultat(variables, erreurs, avertissements);
    }
}
