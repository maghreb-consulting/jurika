package ma.jurika.auth.api;

import ma.jurika.auth.application.essai.AcceptationEssaiLecture;
import ma.jurika.common.exception.UnauthorizedException;
import ma.jurika.common.security.AuthenticatedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lot L7 (E13) : version et date d'acceptation des conditions de l'essai gratuit du cabinet de
 * l'utilisateur connecte, lues par billing-service (jeton du superviseur relaye) a l'acceptation
 * du contrat d'abonnement. Aucun parametre : le cabinet est celui du jeton, un cabinet ne lit
 * que son propre essai. Superviseur seul (lui seul accepte le contrat), super-admin exclu (il
 * herite de SUPERVISEUR par la hierarchie des roles).
 */
@RestController
public class EssaiAcceptationController {

    private final AcceptationEssaiLecture lecture;

    public EssaiAcceptationController(AcceptationEssaiLecture lecture) {
        this.lecture = lecture;
    }

    @GetMapping("/api/v1/workspace/essai/acceptation")
    @PreAuthorize("hasRole('SUPERVISEUR') and !hasRole('SUPER_ADMIN')")
    public AcceptationEssaiLecture.Resultat acceptation(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null || user.workspaceId() == null) {
            throw new UnauthorizedException("Utilisateur introuvable.");
        }
        return lecture.lire(user.workspaceId());
    }
}
