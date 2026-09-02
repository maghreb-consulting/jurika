package fr.maghreb.gje.controllers;

import fr.maghreb.gje.dto.succursale.SuccursaleRequest;
import fr.maghreb.gje.models.Succursale;
import fr.maghreb.gje.models.User;
import fr.maghreb.gje.services.SuccursaleService;
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
@RequestMapping("/api/v1/dossiers/{id}/succursales")
@RequiredArgsConstructor
@Tag(name = "Succursales", description = "Module 6 - Gestion des établissements secondaires")
public class SuccursaleController {

    private final SuccursaleService succursaleService;

    @PostMapping
    @Operation(summary = "Rattacher une succursale à un dossier")
    @PreAuthorize("hasAnyRole('EMPLOYE', 'SUPERVISEUR')")
    public ResponseEntity<Succursale> attachSuccursale(
            @PathVariable UUID id,
            @RequestBody SuccursaleRequest request,
            @AuthenticationPrincipal User user) {
        
        return ResponseEntity.ok(succursaleService.attachSuccursale(id, request, user.getId(), user.getWorkspaceId()));
    }

    @GetMapping
    @Operation(summary = "Lister les succursales d'un dossier")
    public ResponseEntity<List<Succursale>> getSuccursales(@PathVariable UUID id) {
        return ResponseEntity.ok(succursaleService.getByDossierId(id));
    }
}
