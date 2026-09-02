package fr.maghreb.gje.controllers;

import fr.maghreb.gje.dto.admin.WorkspaceQuotaStatusDTO;
import fr.maghreb.gje.models.User;
import fr.maghreb.gje.services.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspace")
@RequiredArgsConstructor
public class WorkspaceController {

    private final QuotaService quotaService;

    @GetMapping("/quota-status")
    @PreAuthorize("hasAnyRole('SUPERVISEUR', 'SUPER_ADMIN')")
    public ResponseEntity<WorkspaceQuotaStatusDTO> getQuotaStatus(
            @RequestParam(required = false) UUID workspaceId,
            @AuthenticationPrincipal User user) {
        
        UUID targetWorkspaceId = workspaceId;
        
        // Logical routing based on roles
        if (user.getRole() == User.Role.SUPERVISEUR) {
            // Supervisor only sees their own workspace
            targetWorkspaceId = user.getWorkspaceId();
        } else if (user.getRole() == User.Role.SUPER_ADMIN) {
            // Super Admin must specify the workspace or default to a system check (not required here)
            if (targetWorkspaceId == null) {
                throw new RuntimeException("workspaceId est requis pour le rôle Super Admin");
            }
        } else {
            throw new RuntimeException("Accès refusé");
        }

        return ResponseEntity.ok(quotaService.getDetailedQuotaStatus(targetWorkspaceId));
    }
}
