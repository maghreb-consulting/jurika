package ma.jurika.ai.workflow.mapper;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * Lot L4b (E7) : le type du tribunal se DEDUIT de la ville du greffe (tribunaux de
 * commerce du Royaume), il ne se saisit pas. Ville inconnue ou vide : rien n'est deduit.
 */
final class TribunalDerive {

    static final Set<String> VILLES_TRIBUNAL_COMMERCE = Set.of(
            "beni mellal", "rabat", "casablanca", "fes", "meknes", "oujda",
            "tanger", "marrakech", "agadir");

    private TribunalDerive() {
    }

    static String type(String ville) {
        if (ville == null || ville.isBlank()) return null;
        return VILLES_TRIBUNAL_COMMERCE.contains(norm(ville))
                ? "Tribunal de commerce" : "Tribunal de premi\u00e8re instance";
    }

    private static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).trim();
    }
}
