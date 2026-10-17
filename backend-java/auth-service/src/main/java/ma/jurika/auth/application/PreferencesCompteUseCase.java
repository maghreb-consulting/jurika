package ma.jurika.auth.application;

import ma.jurika.auth.domain.port.PreferencesCompteRegistre;
import ma.jurika.auth.domain.port.PreferencesCompteRegistre.Preferences;
import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lot L6b (E2) : preferences d'affichage du COMPTE de l'appelant (theme, assistant Juri masque),
 * pour tous les roles. L'identifiant et le workspace viennent du jeton, jamais de la requete.
 *
 * <p>Ecriture partielle : seules les cles presentes changent. Une cle inconnue, un theme hors
 * liste ou un {@code juriMasque} non booleen est refuse (400, donnee nommee) : rien n'est ignore
 * en silence. Pas de trace d'audit : preference d'affichage personnelle, sans effet sur les
 * donnees du cabinet.
 */
@Service
public class PreferencesCompteUseCase {

    public static final String THEME = "theme";
    public static final String JURI_MASQUE = "juriMasque";

    /** Meme liste que la contrainte users_theme_check de V47. */
    static final Set<String> THEMES = Set.of("SYSTEME", "CLAIR", "SOMBRE");

    private final PreferencesCompteRegistre registre;

    public PreferencesCompteUseCase(PreferencesCompteRegistre registre) {
        this.registre = registre;
    }

    @Transactional(readOnly = true)
    public Preferences lire(UUID userId, UUID workspaceId) {
        TenantContext.set(workspaceId);
        return registre.lire(userId, workspaceId).orElseThrow(PreferencesCompteUseCase::introuvable);
    }

    @Transactional
    public Preferences modifier(UUID userId, UUID workspaceId, Map<String, Object> corps) {
        if (corps == null) {
            throw new BusinessException("PREFERENCES_ABSENTES",
                    "Aucune pr\u00e9f\u00e9rence re\u00e7ue : indiquez le th\u00e8me ou l\u2019affichage de Juri.");
        }
        for (String cle : corps.keySet()) {
            if (!THEME.equals(cle) && !JURI_MASQUE.equals(cle)) {
                throw new BusinessException("PREFERENCE_INCONNUE",
                        "Pr\u00e9f\u00e9rence inconnue : \u00ab " + cle + " \u00bb. Pr\u00e9f\u00e9rences accept\u00e9es : "
                                + THEME + ", " + JURI_MASQUE + ".");
            }
        }
        String theme = null;
        if (corps.containsKey(THEME)) {
            Object t = corps.get(THEME);
            if (!(t instanceof String s) || !THEMES.contains(s)) {
                throw new BusinessException("PREFERENCE_THEME_INVALIDE",
                        "Th\u00e8me inconnu : choisissez SYSTEME (le th\u00e8me de votre poste), CLAIR ou SOMBRE.");
            }
            theme = s;
        }
        Boolean juriMasque = null;
        if (corps.containsKey(JURI_MASQUE)) {
            if (!(corps.get(JURI_MASQUE) instanceof Boolean b)) {
                throw new BusinessException("PREFERENCE_JURI_INVALIDE",
                        "L\u2019affichage de Juri s\u2019indique par vrai ou faux (juriMasque : true ou false).");
            }
            juriMasque = b;
        }
        TenantContext.set(workspaceId);
        if (theme == null && juriMasque == null) {
            return registre.lire(userId, workspaceId).orElseThrow(PreferencesCompteUseCase::introuvable);
        }
        return registre.modifier(userId, workspaceId, theme, juriMasque)
                .orElseThrow(PreferencesCompteUseCase::introuvable);
    }

    private static NotFoundException introuvable() {
        return new NotFoundException("Compte introuvable : reconnectez-vous puis r\u00e9essayez.");
    }
}
