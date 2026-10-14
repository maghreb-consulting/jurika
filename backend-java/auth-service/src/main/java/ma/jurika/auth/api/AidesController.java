package ma.jurika.auth.api;

import ma.jurika.auth.application.AidesMasqueesUseCase;
import ma.jurika.common.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Lot L6 (E6, CDC 15.4) : aides masquees de l'appelant (tous les roles).
 * L'identifiant et le workspace viennent du jeton, jamais de la requete.
 *
 * <ul>
 *   <li>{@code GET /me/aides} : cles des aides masquees ;</li>
 *   <li>{@code PUT /me/aides/{cle}} : masquer une aide ;</li>
 *   <li>{@code DELETE /me/aides/{cle}} : la reafficher ;</li>
 *   <li>{@code DELETE /me/aides} : reafficher toutes les aides.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth/me/aides")
@PreAuthorize("isAuthenticated()")
public class AidesController {

    private final AidesMasqueesUseCase aides;

    public AidesController(AidesMasqueesUseCase aides) {
        this.aides = aides;
    }

    @GetMapping
    public Map<String, List<String>> lister(@AuthenticationPrincipal AuthenticatedUser user) {
        return Map.of("cles", aides.cles(user.userId(), user.workspaceId()));
    }

    @PutMapping("/{cle}")
    public ResponseEntity<Void> masquer(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String cle) {
        aides.masquer(user.userId(), user.workspaceId(), cle);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{cle}")
    public ResponseEntity<Void> reafficher(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String cle) {
        aides.reafficher(user.userId(), user.workspaceId(), cle);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> reafficherTout(@AuthenticationPrincipal AuthenticatedUser user) {
        aides.reafficherTout(user.userId(), user.workspaceId());
        return ResponseEntity.noContent().build();
    }
}
