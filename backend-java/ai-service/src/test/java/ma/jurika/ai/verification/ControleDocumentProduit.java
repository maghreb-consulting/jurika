package ma.jurika.ai.verification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lot L4a (E1) : controle du TEXTE d'un document produit.
 *
 * <p>Trois defauts, chacun nomme :
 * <ul>
 *   <li>un marqueur "VALEUR MANQUANTE" (donnee interne absente : l'acte n'aurait pas du sortir) ;</li>
 *   <li>un marqueur "A OBTENIR" d'une donnee externe que la fixture n'annonce pas comme attendue ;</li>
 *   <li>un blanc dans une phrase : mot de liaison suivi d'une ponctuation ("de ,", "a ,"),
 *       double virgule, parentheses vides. C'est la trace d'une valeur vide imprimee en silence.</li>
 * </ul>
 */
final class ControleDocumentProduit {

    static final Pattern VALEUR_MANQUANTE = Pattern.compile("VALEUR MANQUANTE : ([A-Z0-9_]+)");
    static final Pattern A_OBTENIR = Pattern.compile("\u00c0 OBTENIR : ([A-Z0-9_]+)");
    /** Mot de liaison immediatement suivi d'une ponctuation : la valeur entre les deux est vide. */
    static final Pattern BLANC_APRES_MOT = Pattern.compile(
            "(?iu)(?<![\\p{L}'\u2019])(de|du|des|d'|\u00e0|a|au|aux|le|la|les|l'|en|par|pour|sur|n\u00b0|num\u00e9ro)\\s+[,.;)]");
    static final Pattern DOUBLE_VIRGULE = Pattern.compile(",\\s*,");
    static final Pattern PARENTHESES_VIDES = Pattern.compile("\\(\\s*\\)");
    /** Un drapeau "oui" / "non" imprime seul sur sa ligne : une variable pilote a pris la place d'un texte. */
    static final Pattern DRAPEAU_IMPRIME = Pattern.compile("(?m)^\\s*(oui|non)\\s*$");
    /**
     * Lot L4b (E7) : section de documentation d'un modele, "non publiee" par definition,
     * imprimee dans le document produit (vu dans trois annonces du corpus).
     */
    static final Pattern DOCUMENTATION_IMPRIMEE = Pattern.compile("(?m)^\\s*(NOTES D.EMPLOI|DICTIONNAIRE DES VARIABLES)\\b");

    private ControleDocumentProduit() {
    }

    /** Les defauts du texte ; vide si le document est conforme. */
    static List<String> defauts(String texte, Set<String> externesAttendues) {
        List<String> out = new ArrayList<>();
        Matcher m = VALEUR_MANQUANTE.matcher(texte);
        while (m.find()) out.add("donnee manquante : " + m.group(1) + " -- " + contexte(texte, m.start()));
        m = A_OBTENIR.matcher(texte);
        while (m.find()) {
            if (!externesAttendues.contains(m.group(1))) {
                out.add("a obtenir non attendu : " + m.group(1) + " -- " + contexte(texte, m.start()));
            }
        }
        for (Pattern p : List.of(BLANC_APRES_MOT, DOUBLE_VIRGULE, PARENTHESES_VIDES)) {
            m = p.matcher(texte);
            while (m.find()) out.add("blanc : \"" + contexte(texte, m.start()) + "\"");
        }
        m = DRAPEAU_IMPRIME.matcher(texte);
        while (m.find()) out.add("drapeau imprime : \"" + contexte(texte, m.start()) + "\"");
        m = DOCUMENTATION_IMPRIMEE.matcher(texte);
        while (m.find()) out.add("documentation du modele imprimee : " + m.group(1));
        return out;
    }

    static String contexte(String texte, int pos) {
        int debut = Math.max(0, pos - 50);
        int fin = Math.min(texte.length(), pos + 30);
        return texte.substring(debut, fin).replace('\n', ' ').trim();
    }
}
