package ma.jurika.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import ma.jurika.auth.application.SetDroitSuppressionDataroomUseCase;
import ma.jurika.auth.domain.port.DroitSuppressionDataroomRepository;
import ma.jurika.common.security.AuthenticatedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Lot L1, etape E8 (CDC 3.2, RG-DR-06) : le superviseur accorde ou retire a un
 * employe de son cabinet le droit de suppression en Data Room. Le superviseur seul :
 * la hierarchie SUPER_ADMIN > SUPERVISEUR ouvrirait l'acte a l'equipe JURIKA, exclue.
 */
@RestController
public class DroitSuppressionDataroomController {

    private static final String CHEMIN = "/api/v1/auth/users/{userId}/droit-suppression-dataroom";

    private final SetDroitSuppressionDataroomUseCase useCase;
    private final DroitSuppressionDataroomRepository droits;

    public DroitSuppressionDataroomController(SetDroitSuppressionDataroomUseCase useCase,
                                              DroitSuppressionDataroomRepository droits) {
        this.useCase = useCase;
        this.droits = droits;
    }

    public record DroitSuppressionRequest(@NotNull Boolean accorde) {}

    @PutMapping(CHEMIN)
    @PreAuthorize("hasRole('SUPERVISEUR') and !hasRole('SUPER_ADMIN')")
    public SetDroitSuppressionDataroomUseCase.Result definir(@AuthenticationPrincipal AuthenticatedUser user,
                                                             @PathVariable UUID userId,
                                                             @Valid @RequestBody DroitSuppressionRequest req,
                                                             HttpServletRequest http) {
        return useCase.execute(new SetDroitSuppressionDataroomUseCase.Command(
                user.workspaceId(), user.userId(), userId, req.accorde(),
                http.getRemoteAddr(), http.getHeader("User-Agent")));
    }

    @GetMapping(CHEMIN)
    @PreAuthorize("hasRole('SUPERVISEUR') and !hasRole('SUPER_ADMIN')")
    public Map<String, Object> lire(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID userId) {
        return Map.of("userId", userId.toString(),
                "droitSuppressionDataroom", droits.lire(user.workspaceId(), userId));
    }
}
