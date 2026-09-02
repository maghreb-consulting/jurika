package fr.maghreb.gje.controllers;

import fr.maghreb.gje.dto.fiche.AddEvenementRequest;
import fr.maghreb.gje.dto.fiche.EvenementJuridiqueDTO;
import fr.maghreb.gje.dto.fiche.FicheJuridiqueDTO;
import fr.maghreb.gje.dto.fiche.UpdateFicheJuridiqueRequest;
import fr.maghreb.gje.services.FicheJuridiqueService;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import fr.maghreb.gje.models.User;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dossiers/{dossierId}/fiche-juridique")
@RequiredArgsConstructor
public class FicheJuridiqueController {

    private final FicheJuridiqueService ficheJuridiqueService;

    private boolean isSuperviseur(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_SUPERVISEUR") || role.equals("ROLE_SUPER_ADMIN"));
    }

    private UUID getWorkspaceId(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return user.getWorkspaceId();
    }

    private UUID getUserId(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return user.getId();
    }

    private String getUserName(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return user.getFullName();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERVISEUR', 'EMPLOYE')")
    public ResponseEntity<FicheJuridiqueDTO> getFiche(
            @PathVariable UUID dossierId,
            Authentication authentication) {
        
        UUID workspaceId = getWorkspaceId(authentication);
        boolean isSuperviseur = isSuperviseur(authentication);
        
        FicheJuridiqueDTO result = ficheJuridiqueService.getFiche(dossierId, workspaceId, isSuperviseur);
        return ResponseEntity.ok(result);
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('SUPERVISEUR', 'EMPLOYE')")
    public ResponseEntity<FicheJuridiqueDTO> updateFiche(
            @PathVariable UUID dossierId,
            @RequestBody UpdateFicheJuridiqueRequest request,
            Authentication authentication) {
            
        UUID workspaceId = getWorkspaceId(authentication);
        UUID userId = getUserId(authentication);
        boolean isSuperviseur = isSuperviseur(authentication);

        FicheJuridiqueDTO result = ficheJuridiqueService.updateFiche(dossierId, workspaceId, userId, request, isSuperviseur);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/evenements")
    @PreAuthorize("hasAnyRole('SUPERVISEUR', 'EMPLOYE')")
    public ResponseEntity<List<EvenementJuridiqueDTO>> getEvenements(
            @PathVariable UUID dossierId,
            Authentication authentication) {
        
        UUID workspaceId = getWorkspaceId(authentication);
        return ResponseEntity.ok(ficheJuridiqueService.getTimeline(dossierId, workspaceId));
    }

    @PostMapping("/evenements")
    @PreAuthorize("hasAnyRole('SUPERVISEUR', 'EMPLOYE')")
    public ResponseEntity<EvenementJuridiqueDTO> addEvenement(
            @PathVariable UUID dossierId,
            @RequestBody AddEvenementRequest request,
            Authentication authentication) {
            
        UUID workspaceId = getWorkspaceId(authentication);
        String name = getUserName(authentication);
        
        EvenementJuridiqueDTO result = ficheJuridiqueService.addEvenement(dossierId, workspaceId, name, request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('SUPERVISEUR', 'EMPLOYE')")
    public ResponseEntity<Map<String, Object>> exportFiche(
            @PathVariable UUID dossierId,
            Authentication authentication,
            HttpServletRequest request) {
            
        UUID workspaceId = getWorkspaceId(authentication);
        User user = (User) authentication.getPrincipal();
        boolean isSuperviseur = isSuperviseur(authentication);
        
        Map<String, Object> export = ficheJuridiqueService.exportFiche(
            dossierId, 
            workspaceId, 
            user, 
            request.getRemoteAddr(), 
            isSuperviseur);
        return ResponseEntity.ok(export);
    }
}
