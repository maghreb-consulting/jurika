package ma.jurika.auth.api;

import ma.jurika.auth.application.essai.CycleEssaiService;
import ma.jurika.auth.application.essai.EtatEssai;
import ma.jurika.common.exception.UnauthorizedException;
import ma.jurika.common.security.AuthenticatedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lot L7 (E4) : etat de l'essai du cabinet de l'utilisateur connecte (bandeau
 * de l'essai, rappels J-3 et J-1, lecture seule, date de suppression,
 * limites). Lecture seule : reste servi pendant la lecture seule.
 */
@RestController
@RequestMapping("/api/v1/workspace/essai")
public class EssaiController {

    private final CycleEssaiService cycle;

    public EssaiController(CycleEssaiService cycle) {
        this.cycle = cycle;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERVISEUR','EMPLOYE','CLIENT')")
    public EtatEssai etat(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null || user.workspaceId() == null) {
            throw new UnauthorizedException("Utilisateur introuvable.");
        }
        return cycle.etat(user.workspaceId());
    }
}
