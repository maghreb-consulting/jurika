package ma.jurika.auth.api;

import ma.jurika.auth.application.PreferencesCompteUseCase;
import ma.jurika.auth.domain.port.PreferencesCompteRegistre.Preferences;
import ma.jurika.common.security.AuthenticatedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lot L6b (E2) : preferences d'affichage du COMPTE de l'appelant (tous les roles authentifies :
 * super-admin, superviseur, employe, client), pour lui seul.
 *
 * <ul>
 *   <li>{@code GET /api/v1/users/me/preferences} : {@code {theme, juriMasque}} ;</li>
 *   <li>{@code PUT /api/v1/users/me/preferences} {@code {theme?, juriMasque?}} : enregistre les
 *       cles presentes, rend {@code {theme, juriMasque}} ; valeur invalide ou cle inconnue : 400
 *       nomme.</li>
 * </ul>
 * {@code theme} : {@code SYSTEME} | {@code CLAIR} | {@code SOMBRE}. Ouvert pendant la lecture
 * seule de l'essai, la suspension, l'archivage et le contrat en attente (preference d'affichage).
 */
@RestController
@RequestMapping("/api/v1/users/me/preferences")
@PreAuthorize("isAuthenticated()")
public class PreferencesCompteController {

    private final PreferencesCompteUseCase preferences;

    public PreferencesCompteController(PreferencesCompteUseCase preferences) {
        this.preferences = preferences;
    }

    @GetMapping
    public Preferences lire(@AuthenticationPrincipal AuthenticatedUser user) {
        return preferences.lire(user.userId(), user.workspaceId());
    }

    @PutMapping
    public Preferences modifier(@AuthenticationPrincipal AuthenticatedUser user,
                                @RequestBody Map<String, Object> corps) {
        return preferences.modifier(user.userId(), user.workspaceId(), corps);
    }
}
