package ma.jurika.common.security;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.function.IntPredicate;

/**
 * BUG 7 (2026-06-08) — Genere l'identifiant de connexion {@code prenom.nom@jurika.ma}
 * a partir d'un prenom + nom + nom de domaine. Sanitization : accents
 * normalises, espaces et caracteres non alphanumeriques remplaces par '.', en
 * minuscules ; segments vides ignores. Si firstName ET lastName sont vides,
 * fallback sur "user".
 *
 * <p>La <b>collision intra-workspace</b> est resolue par l'appelant via
 * {@link #withSuffix(String, int)} (ex prenom.nom2@jurika.ma) : ce generateur
 * est volontairement sans etat ni DB lookup, le caller a la responsabilite de
 * tester l'unicite (UserRepository.findByWorkspaceAndLoginEmail) en boucle
 * jusqu'au premier libre.
 *
 * <p>Reference : decision metier 2026-06-07 Oussama — identifiant stable
 * @ jurika.ma vs email perso = contact (notifications).
 */
@Component
public class LoginEmailGenerator {

    /** Domaine fixe pour V1. Tous les workspaces partagent {@code @jurika.ma}. */
    public static final String DOMAIN = "jurika.ma";

    /** Base sans suffixe ni domaine : {@code "prenom.nom"} pret a appender. */
    public String base(String firstName, String lastName) {
        String first = normalize(firstName);
        String last = normalize(lastName);
        String joined;
        if (!first.isEmpty() && !last.isEmpty()) {
            joined = first + "." + last;
        } else if (!first.isEmpty()) {
            joined = first;
        } else if (!last.isEmpty()) {
            joined = last;
        } else {
            joined = "user";
        }
        return joined;
    }

    /** Compose l'identifiant complet sans suffixe : {@code prenom.nom@jurika.ma}. */
    public String build(String firstName, String lastName) {
        return base(firstName, lastName) + "@" + DOMAIN;
    }

    /** Compose avec suffixe numerique pour resoudre une collision : {@code prenom.nom2@jurika.ma}. */
    public String withSuffix(String base, int suffix) {
        if (suffix <= 1) return base + "@" + DOMAIN;
        return base + suffix + "@" + DOMAIN;
    }

    /**
     * Normalise un segment de nom (prenom OU nom, jamais le couple) :
     * <ul>
     *   <li>NFD pour decomposer les accents puis suppression des combining marks
     *       ("Idrïssi" -> "Idrissi", "Benoît" -> "Benoit").</li>
     *   <li>Drop des caracteres non ASCII alphanumeriques : apostrophes,
     *       tirets, espaces, ponctuation. "L'Hassan" -> "lhassan",
     *       "El-Idrïssi" -> "elidrissi", "Marie Therese" -> "marietherese".</li>
     *   <li>Downcase.</li>
     * </ul>
     * Le dot separateur prenom.nom est ajoute par {@link #base(String, String)},
     * pas ici, pour eviter d'introduire des dots intra-segment qui rendraient
     * les identifiants confus ("l.hassan.el.idrissi" vs "lhassan.elidrissi").
     */
    private String normalize(String s) {
        if (s == null) return "";
        String stripped = Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        StringBuilder out = new StringBuilder(stripped.length());
        IntPredicate isOk = c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
        for (int i = 0; i < stripped.length(); i++) {
            int c = stripped.charAt(i);
            if (isOk.test(c)) {
                out.append((char) Character.toLowerCase(c));
            }
            // Tout le reste (espaces, apostrophes, tirets, ponctuation) est
            // silencieusement ignore — pas de dot intra-segment.
        }
        return out.toString();
    }
}
