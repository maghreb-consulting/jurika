package ma.jurika.ai.document.format;

import java.util.Locale;

/**
 * Harmonisation de la <b>casse</b> d'une valeur injectée en position d'en-tête ou de
 * titre (2026-08-17).
 *
 * <h2>Le problème</h2>
 * Les en-têtes et titres des modèles directeur sont écrits <b>entièrement en
 * majuscules</b> — littéralement, sans style {@code w:caps} : la casse rendue est celle
 * du texte. Les valeurs injectées, elles, arrivent en minuscules, parce qu'elles servent
 * AUSSI le corps du texte où les minuscules sont correctes. D'où :
 * <pre>
 *   GmbH DE DROIT allemand                         ← en-tête
 *   PROCÈS-VERBAL DES DÉCISIONS DU gérant unique   ← titre
 *   … le gérant unique de la société MEDITERRANEA GMBH, GmbH de droit allemand …  ← corps (correct)
 * </pre>
 *
 * <p>C'est exactement la classe de problème que {@link FrenchContraction} : une même
 * variable sert deux positions typographiques, et seule la couche de substitution
 * connaît les deux. La correction vit donc au même endroit.
 *
 * <h2>La règle, et sa garde</h2>
 * On met la valeur en majuscules quand DEUX conditions sont réunies :
 * <ol>
 *   <li>le <b>texte littéral du modèle</b> autour de la variable (placeholders retirés)
 *       est entièrement en majuscules et porte au moins {@value #LETTRES_MINIMUM}
 *       lettres — en dessous, on ne peut rien conclure (un paragraphe réduit au seul
 *       placeholder n'est pas un en-tête) ;</li>
 *   <li>la valeur est <b>entièrement en minuscules</b>.</li>
 * </ol>
 *
 * <p>La seconde condition est ce qui protège les <b>noms propres et sigles</b>. Sans
 * elle, « GmbH » deviendrait « GMBH » et « Handelsregister München » perdrait sa casse
 * allemande — dans le même paragraphe d'en-tête que « allemand ». Une valeur qui porte
 * déjà une majuscule a été casée par quelqu'un : on n'y touche pas. Une valeur
 * intégralement minuscule, elle, n'a jamais été casée pour un titre.
 */
public final class CasseEnTete {

    /** En deçà, le texte littéral est trop court pour trancher (sigle, ponctuation…). */
    private static final int LETTRES_MINIMUM = 3;

    /** En deçà, la valeur n'est pas un mot (initiale, repère de liste) : on n'y touche pas. */
    private static final int LETTRES_VALEUR_MINIMUM = 2;

    private CasseEnTete() {}

    /**
     * Le texte littéral d'un paragraphe le désigne-t-il comme en-tête / titre ?
     *
     * @param texteLitteral texte du modèle <b>placeholders retirés</b>
     */
    public static boolean estEnTete(String texteLitteral) {
        if (texteLitteral == null) return false;
        // On raisonne par MOTS, pas lettre à lettre. Raison : quand plusieurs variables
        // se suivent dans un même paragraphe, celles déjà substituées font partie du
        // texte examiné pour les suivantes. Le titre « $SOCIETE_MERE_FORME DE DROIT
        // $SOCIETE_MERE_PAYS » présente ainsi « GmbH DE DROIT » au moment de résoudre le
        // pays : une lecture lettre à lettre y voit les minuscules de « GmbH » et conclut
        // à tort qu'il ne s'agit pas d'un en-tête.
        //
        // Les mots à casse MIXTE (sigles, noms propres) ne comptent donc dans aucun camp ;
        // seule la domination des mots tout en majuscules décide.
        int majuscules = 0;
        int minuscules = 0;
        int lettresMajuscules = 0;
        for (String mot : texteLitteral.split("[^\\p{L}]+")) {
            if (mot.length() < 2) continue;   // « à », « n », initiales : neutres
            boolean aMaj = false;
            boolean aMin = false;
            for (int i = 0; i < mot.length(); i++) {
                if (Character.isUpperCase(mot.charAt(i))) aMaj = true;
                else if (Character.isLowerCase(mot.charAt(i))) aMin = true;
            }
            if (aMaj && !aMin) {
                majuscules++;
                lettresMajuscules += mot.length();
            } else if (aMin && !aMaj) {
                minuscules++;
            }
        }
        return minuscules == 0 && majuscules > 0 && lettresMajuscules >= LETTRES_MINIMUM;
    }

    /**
     * Valeur à injecter, mise en majuscules si (et seulement si) la position est un
     * en-tête ET que la valeur est intégralement en minuscules.
     *
     * @param valeur valeur résolue du placeholder
     * @param enTete résultat de {@link #estEnTete(String)} pour le paragraphe courant
     */
    public static String harmoniser(String valeur, boolean enTete) {
        if (!enTete || valeur == null || valeur.isBlank()) return valeur;
        int lettres = 0;
        for (int i = 0; i < valeur.length(); i++) {
            char c = valeur.charAt(i);
            if (!Character.isLetter(c)) continue;
            // Une majuscule déjà présente = casse voulue (sigle, nom propre étranger).
            if (Character.isUpperCase(c)) return valeur;
            lettres++;
        }
        // Une valeur d'UNE seule lettre n'est pas un mot de titre : c'est un repère, un
        // indice de liste, une initiale. La capitaliser dénaturerait la donnée sans rien
        // apporter — et casserait les valeurs symboliques d'un gabarit.
        return lettres >= LETTRES_VALEUR_MINIMUM ? valeur.toUpperCase(Locale.FRENCH) : valeur;
    }
}
