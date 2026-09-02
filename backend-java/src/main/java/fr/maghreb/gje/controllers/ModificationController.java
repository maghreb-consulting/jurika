package fr.maghreb.gje.controllers;

import fr.maghreb.gje.dto.modification.ModificationRequest;
import fr.maghreb.gje.models.ModificationDossier;
import fr.maghreb.gje.models.User;
import fr.maghreb.gje.services.ModificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dossiers/{id}/modifications")
@RequiredArgsConstructor
@Tag(name = "Modification d'Entreprise", description = "Module 4 - Gestion des changements juridiques")
public class ModificationController {

    private final ModificationService modificationService;

    @PostMapping
    @Operation(summary = "Créer une nouvelle modification pour un dossier")
    @PreAuthorize("hasAnyRole('EMPLOYE', 'SUPERVISEUR')")
    public ResponseEntity<ModificationDossier> createModification(
            @PathVariable UUID id,
            @RequestBody ModificationRequest request,
            @AuthenticationPrincipal User user) {
        
        return ResponseEntity.ok(modificationService.createModification(id, request, user.getId(), user.getWorkspaceId()));
    }

    @GetMapping
    @Operation(summary = "Récupérer l'historique des modifications d'un dossier")
    public ResponseEntity<List<ModificationDossier>> getHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(modificationService.getHistory(id));
    }

    @PostMapping("/{modifId}/generate-pv")
    @Operation(summary = "Générer le PV de modification via IA")
    @PreAuthorize("hasAnyRole('EMPLOYE', 'SUPERVISEUR')")
    public ResponseEntity<byte[]> generatePV(@PathVariable UUID modifId) {
        return ResponseEntity.ok(modificationService.generatePV(modifId));
    }
}
