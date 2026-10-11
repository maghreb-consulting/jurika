package ma.jurika.ticket.api;

import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.application.referentiel.DelaisGreffe;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lot L4d (E7) : referentiel des delais de depot au greffe par nature d'acte. Lu par les
 * employes et le superviseur ; ajuste par le superviseur seul (decision du cabinet).
 */
@RestController
@RequestMapping("/api/v1/tickets/delais-greffe")
public class DelaisGreffeController {

    private final DelaisGreffe delais;

    public DelaisGreffeController(DelaisGreffe delais) {
        this.delais = delais;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR')")
    public List<DelaisGreffe.Nature> lister(@AuthenticationPrincipal AuthenticatedUser user) {
        TenantContext.set(user.workspaceId());
        return delais.referentiel(user.workspaceId());
    }

    @PutMapping("/{matrice}/{decision}")
    @PreAuthorize("hasAuthority('ROLE_SUPERVISEUR')")
    public ResponseEntity<Void> ajuster(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String matrice,
                                        @PathVariable String decision, @RequestBody DelaisGreffe.Saisie saisie) {
        TenantContext.set(user.workspaceId());
        delais.ajuster(user.workspaceId(), matrice, decision, saisie, user.userId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{matrice}/{decision}")
    @PreAuthorize("hasAuthority('ROLE_SUPERVISEUR')")
    public ResponseEntity<Void> retirer(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String matrice,
                                        @PathVariable String decision) {
        TenantContext.set(user.workspaceId());
        delais.retirer(user.workspaceId(), matrice, decision);
        return ResponseEntity.noContent().build();
    }
}
