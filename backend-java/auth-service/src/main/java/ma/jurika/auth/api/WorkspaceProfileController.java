package ma.jurika.auth.api;

import jakarta.validation.Valid;
import ma.jurika.auth.api.dto.UpdateWorkspaceProfileRequest;
import ma.jurika.auth.application.WorkspaceProfileService;
import ma.jurika.common.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Simplification inscription (2026-07-13) — informations legales du cabinet
 * (workspace courant) apres inscription.
 *
 * <p>GET : lecture (tout membre authentifie) — sert au bandeau de rappel
 * "ICE manquant" et a l'onglet Cabinet des parametres.
 * <p>PATCH : mise a jour de l'ICE / de la ville — SUPERVISEUR uniquement
 * (RG-U02 : admin du workspace). L'ICE laisse vide au signup se complete ici.
 */
@RestController
@RequestMapping("/api/v1/workspace/profile")
public class WorkspaceProfileController {

    private final WorkspaceProfileService service;

    public WorkspaceProfileController(WorkspaceProfileService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> get(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(toMap(service.get(user.workspaceId())));
    }

    @PatchMapping
    @PreAuthorize("hasAuthority('ROLE_SUPERVISEUR')")
    public ResponseEntity<Map<String, Object>> update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateWorkspaceProfileRequest body) {
        var cmd = new WorkspaceProfileService.UpdateCommand(
                body.getIce(), body.getCity(), body.getNomAfficheDocuments(),
                body.getAdresse(), body.getTelephone(), body.getSiteWeb(),
                body.getRcNumber(), body.getIfFiscal());
        return ResponseEntity.ok(toMap(service.update(user.workspaceId(), cmd)));
    }

    private static Map<String, Object> toMap(WorkspaceProfileService.View v) {
        // HashMap car Map.of refuse les valeurs null (ice / city / professionalType peuvent l'etre).
        Map<String, Object> m = new HashMap<>();
        m.put("name", v.name());
        m.put("professionalType", v.professionalType());
        m.put("ice", v.ice());
        m.put("city", v.city());
        m.put("contactEmail", v.contactEmail());
        m.put("iceMissing", v.iceMissing());
        m.put("nomAfficheDocuments", v.nomAfficheDocuments());
        m.put("documentDisplayName", v.documentDisplayName());
        m.put("adresse", v.adresse());
        m.put("telephone", v.telephone());
        m.put("siteWeb", v.siteWeb());
        m.put("rcNumber", v.rcNumber());
        m.put("ifFiscal", v.ifFiscal());
        m.put("hasLogo", v.hasLogo());
        return m;
    }
}
