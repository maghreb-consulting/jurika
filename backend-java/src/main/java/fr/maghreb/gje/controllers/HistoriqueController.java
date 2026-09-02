package fr.maghreb.gje.controllers;

import fr.maghreb.gje.models.*;
import fr.maghreb.gje.services.HistoriqueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HistoriqueController {

    private final HistoriqueService historiqueService;

    @GetMapping("/dossiers/{id}/historique")
    @PreAuthorize("hasAnyRole('EMPLOYE', 'SUPERVISEUR')")
    public ResponseEntity<List<Historique>>
            getByDossier(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
            historiqueService.getByDossier(
                id, user.getWorkspaceId()));
    }

    @GetMapping("/workspace/historique")
    @PreAuthorize("hasAnyRole('EMPLOYE', 'SUPERVISEUR')")
    public ResponseEntity<List<Historique>>
            getByWorkspace(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
            historiqueService.getByWorkspace(
                user.getWorkspaceId()));
    }
}
