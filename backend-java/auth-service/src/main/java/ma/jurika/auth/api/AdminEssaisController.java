package ma.jurika.auth.api;

import ma.jurika.auth.application.essai.CycleEssaiService;
import ma.jurika.auth.application.essai.EtatEssai;
import ma.jurika.auth.infrastructure.essai.EssaiJdbc;
import ma.jurika.common.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Lot L7 (E4) : ecran des essais du super-admin.
 *
 * <ul>
 *   <li>RG-ESS-03 : liste des essais avec leurs signalements (meme reseau,
 *       meme appareil) ; la suspension passe par
 *       {@code POST /api/v1/admin/workspaces/{id}/suspend} (et la levee par
 *       {@code .../activate}) ;</li>
 *   <li>RG-ESS-11 : prolonger (une fois) ou mettre fin a un essai.</li>
 * </ul>
 * Les actions portent sur {@code /api/v1/admin/workspaces/{id}/essai/**} : le
 * workspace du chemin devient le workspace courant avant la transaction
 * (ContexteWorkspaceCheminConfig).
 */
@RestController
public class AdminEssaisController {

    private final EssaiJdbc essais;
    private final CycleEssaiService cycle;

    public AdminEssaisController(EssaiJdbc essais, CycleEssaiService cycle) {
        this.essais = essais;
        this.cycle = cycle;
    }

    public record ProlongationBody(Integer jours) {}

    public record FinBody(String motif) {}

    @GetMapping("/api/v1/admin/essais")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public List<EssaiJdbc.EssaiAdmin> liste() {
        return essais.listeEssais();
    }

    @PostMapping("/api/v1/admin/workspaces/{workspaceId}/essai/prolonger")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public EtatEssai prolonger(@PathVariable UUID workspaceId, @RequestBody ProlongationBody body,
                               @AuthenticationPrincipal AuthenticatedUser admin) {
        return cycle.prolonger(workspaceId, body == null ? null : body.jours(), admin == null ? null : admin.userId());
    }

    @PostMapping("/api/v1/admin/workspaces/{workspaceId}/essai/mettre-fin")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public EtatEssai mettreFin(@PathVariable UUID workspaceId, @RequestBody FinBody body,
                               @AuthenticationPrincipal AuthenticatedUser admin) {
        return cycle.mettreFin(workspaceId, body == null ? null : body.motif(), admin == null ? null : admin.userId());
    }

    @PostMapping("/api/v1/admin/workspaces/{workspaceId}/essai/signalement-examine")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> signalementExamine(@PathVariable UUID workspaceId,
                                                   @AuthenticationPrincipal AuthenticatedUser admin) {
        cycle.signalementExamine(workspaceId, admin == null ? null : admin.userId());
        return ResponseEntity.noContent().build();
    }
}
