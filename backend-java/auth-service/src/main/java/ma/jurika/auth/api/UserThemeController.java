package ma.jurika.auth.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import ma.jurika.auth.application.UpdateThemeUseCase;
import ma.jurika.common.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 12.5 / Sprint Beta (pricing-deploy) — TASK 5.
 *
 * <p>Endpoint pour persister la preference de theme (light/dark) du
 * workspace de l'utilisateur authentifie. V22 a deja ajoute la colonne
 * {@code workspaces.preferred_theme} avec CHECK constraint et defaut
 * 'light' ; ce controller branche le frontend qui appelait jusqu'ici
 * un 404 silencieux (cf. PLAN_SPRINT_12-5_THEME_ALIGNMENT.md).
 *
 * <p>Decision archi : preference par WORKSPACE (cabinet) plutot que par
 * utilisateur — coherent avec la position editoriale "un cabinet, un
 * theme" validee 2026-06-01. Si on veut un theme per-user a l'avenir,
 * il faudra une colonne dediee sur la table users.
 */
@RestController
@RequestMapping("/api/v1/users/me/theme")
public class UserThemeController {

    private static final Logger log = LoggerFactory.getLogger(UserThemeController.class);

    private final UpdateThemeUseCase updateThemeUseCase;

    public UserThemeController(UpdateThemeUseCase updateThemeUseCase) {
        this.updateThemeUseCase = updateThemeUseCase;
    }

    /**
     * Payload : { "theme": "dark" | "light" }. Pattern strict pour
     * eviter les contournements de la CHECK constraint cote DB.
     */
    public record ThemeUpdateRequest(
            @NotBlank
            @Pattern(regexp = "^(dark|light)$", message = "theme doit etre 'dark' ou 'light'")
            String theme
    ) {}

    public record ThemeResponse(String theme) {}

    @PostMapping
    public ResponseEntity<ThemeResponse> updateTheme(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ThemeUpdateRequest req) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        log.debug("Theme update workspace={} user={} theme={}",
                user.workspaceId(), user.userId(), req.theme());
        String persisted = updateThemeUseCase.execute(
                user.workspaceId(), user.userId(), req.theme());
        return ResponseEntity.ok(new ThemeResponse(persisted));
    }

    @GetMapping
    public ResponseEntity<ThemeResponse> getTheme(
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(new ThemeResponse(
                updateThemeUseCase.getCurrentTheme(user.workspaceId())));
    }
}
