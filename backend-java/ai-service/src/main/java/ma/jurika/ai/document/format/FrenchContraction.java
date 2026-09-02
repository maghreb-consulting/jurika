package ma.jurika.ai.document.format;

import java.util.Locale;

/**
 * Contraction française entre la <b>préposition du modèle</b> et l'<b>article de la
 * valeur injectée</b> (lot « grammaire d'assemblage », 2026-08-17).
 *
 * <h2>Le problème</h2>
 * Les modèles directeur écrivent la préposition <b>en dur</b>, juste avant le
 * placeholder : {@code « Fait à $ASSEMBLEE_LIEU »},
 * {@code « PROCÈS-VERBAL DES DÉCISIONS DE $ORGANE_COMPETENT »}. La valeur injectée,
 * elle, porte naturellement son propre article — {@code « au siège social »},
 * {@code « le gérant unique »} — parce qu'elle sert AUSSI de sujet ailleurs dans le
 * même acte ({@code « $ORGANE_COMPETENT est appelé(e) à statuer… »}, où l'article est
 * grammaticalement obligatoire). La simple concaténation produisait donc :
 * <pre>
 *   « Fait à au siège social, le 20/09/2026 »
 *   « PROCÈS-VERBAL DES DÉCISIONS DE le gérant unique »
 * </pre>
 *
 * <h2>Pourquoi le corriger ICI et pas dans la valeur</h2>
 * Une même variable occupe des positions grammaticales contradictoires : cinq positions
 * SUJET (article obligatoire) et une position après {@code DE} (article à contracter).
 * Aucune valeur unique ne satisfait les deux par concaténation. Et le contenu des
 * modèles est intouchable. La seule couche qui connaît <b>à la fois</b> la préposition
 * et la valeur est donc le moment de la substitution — c'est là que la fusion se fait.
 *
 * <h2>La règle, unique et générale</h2>
 * Quand un placeholder est <b>immédiatement précédé</b> d'une préposition
 * ({@code de} / {@code à}), on fusionne cette préposition avec l'article de tête de la
 * valeur, selon la grammaire française :
 * <pre>
 *   de + le …   → du …          à + le …   → au …
 *   de + les …  → des …         à + les …  → aux …
 *   de + la …   → de la …       à + la …   → à la …   (inchangé)
 *   de + l'…    → de l'…        à + l'…    → à l'…    (inchangé)
 *   de + du/des → du/des …      à + au/aux → au/aux … (la valeur était déjà contractée :
 *                                                      on retire la préposition en double)
 * </pre>
 * La <b>casse de la préposition du modèle</b> est conservée : {@code « DE »} donne
 * {@code « DU »}, {@code « de »} donne {@code « du »}.
 *
 * <p><b>Hors de portée volontairement</b> : {@code « de droit $PAYS »}. Le mot qui
 * précède le placeholder y est {@code droit}, pas une préposition — aucune contraction
 * n'est due. Ce cas relève de {@link NationaliteFrancaise} (pays → adjectif).
 *
 * <p>Classe pure et sans état : testable isolément, réutilisable par tout moteur de
 * rendu.
 */
public final class FrenchContraction {

    private FrenchContraction() {}

    /**
     * Résultat d'une fusion.
     *
     * @param caracteresARetirer nombre de caractères à retirer À LA FIN du texte déjà
     *                           produit (la préposition absorbée) ; {@code 0} si rien
     *                           ne change
     * @param valeur             valeur à écrire à la place du placeholder
     */
    public record Fusion(int caracteresARetirer, String valeur) {
        /** Aucune contraction : on écrit la valeur telle quelle. */
        static Fusion inchangee(String valeur) {
            return new Fusion(0, valeur);
        }
    }

    /**
     * Fusionne la préposition éventuellement présente en fin de {@code texteAvant} avec
     * l'article de tête de {@code valeur}.
     *
     * @param texteAvant texte déjà rendu, juste avant le placeholder
     * @param valeur     valeur résolue du placeholder
     */
    public static Fusion fusionner(CharSequence texteAvant, String valeur) {
        if (valeur == null || valeur.isBlank() || texteAvant == null) {
            return Fusion.inchangee(valeur);
        }
        Preposition prep = prepositionFinale(texteAvant);
        if (prep == null) return Fusion.inchangee(valeur);

        String v = valeur.stripLeading();
        String tete = premierMot(v);
        String reste = v.substring(tete.length()).stripLeading();
        String teteMin = tete.toLowerCase(Locale.FRANCE);

        // (1) La valeur porte DÉJÀ la forme contractée de cette même préposition :
        //     la préposition du modèle fait doublon, on la retire.
        //     « Fait à » + « au siège social » → « Fait au siège social ».
        if (prep.formesContractees.contains(teteMin)) {
            return new Fusion(prep.longueur, v);
        }
        // (2) Article défini masculin / pluriel : contraction obligatoire.
        //     « DE » + « le gérant unique » → « DU gérant unique ».
        if (teteMin.equals("le")) {
            return new Fusion(prep.longueur, casse(prep.contracteLe, prep.majuscule) + " " + reste);
        }
        if (teteMin.equals("les")) {
            return new Fusion(prep.longueur, casse(prep.contracteLes, prep.majuscule) + " " + reste);
        }
        // (3) « la », « l'», article indéfini, nom propre… : aucune contraction due.
        return Fusion.inchangee(valeur);
    }

    // ------------------------------------------------------------------
    // Interne
    // ------------------------------------------------------------------

    /** Préposition reconnue en fin de texte, avec sa longueur (préposition + espace). */
    private record Preposition(int longueur, boolean majuscule, String contracteLe,
                               String contracteLes, java.util.Set<String> formesContractees) {}

    private static Preposition prepositionFinale(CharSequence texteAvant) {
        int n = texteAvant.length();
        // Le placeholder doit être collé à la préposition par UN espace au moins.
        int i = n;
        while (i > 0 && estEspace(texteAvant.charAt(i - 1))) i--;
        if (i == n) return null;              // pas d'espace : ce n'est pas « de $VAR »
        int finMot = i;
        while (i > 0 && estLettre(texteAvant.charAt(i - 1))) i--;
        String mot = texteAvant.subSequence(i, finMot).toString();
        if (mot.isEmpty()) return null;
        // Le mot doit être précédé d'une frontière (début de texte, espace, ponctuation)
        // pour ne pas confondre un suffixe (« grande » se termine par « de »).
        if (i > 0 && estLettre(texteAvant.charAt(i - 1))) return null;

        boolean maj = mot.equals(mot.toUpperCase(Locale.FRANCE)) && mot.length() > 1;
        int longueur = n - i;                 // préposition + espaces intermédiaires
        String motMin = normaliser(mot);
        return switch (motMin) {
            case "de" -> new Preposition(longueur, maj, "du", "des",
                    java.util.Set.of("du", "des"));
            case "a" -> new Preposition(longueur, maj, "au", "aux",
                    java.util.Set.of("au", "aux"));
            default -> null;
        };
    }

    /** Minuscule sans accent : « À » et « à » désignent la même préposition. */
    private static String normaliser(String mot) {
        String n = java.text.Normalizer.normalize(mot, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n.toLowerCase(Locale.FRANCE);
    }

    private static String casse(String valeur, boolean majuscule) {
        return majuscule ? valeur.toUpperCase(Locale.FRANCE) : valeur;
    }

    /** Premier mot, apostrophe comprise : « l'associé » est un seul jeton d'article. */
    private static String premierMot(String v) {
        int i = 0;
        while (i < v.length() && (estLettre(v.charAt(i)) || v.charAt(i) == '\'' || v.charAt(i) == '’')) {
            i++;
            if (i > 0 && (v.charAt(i - 1) == '\'' || v.charAt(i - 1) == '’')) break;
        }
        return v.substring(0, i);
    }

    private static boolean estLettre(char c) {
        return Character.isLetter(c);
    }

    private static boolean estEspace(char c) {
        return c == ' ' || c == ' ' || c == ' ' || c == '\t';
    }
}
