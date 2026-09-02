package fr.maghreb.gje.controllers;

import fr.maghreb.gje.dto.procedure.DissolutionRequest;
import fr.maghreb.gje.dto.procedure.LiquidationRequest;
import fr.maghreb.gje.models.User;
import fr.maghreb.gje.services.ProcedureService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dossiers/{id}/procedures")
@RequiredArgsConstructor
@Tag(name = "Procédures Légales", description = "Module 5 - Dissolution et Liquidation")
public class ProcedureController {

    private final ProcedureService procedureService;

    @PostMapping("/dissolution")
    @Operation(summary = "Déclencher la procédure de dissolution")
    @PreAuthorize("hasAnyRole('EMPLOYE', 'SUPERVISEUR')")
    public ResponseEntity<Void> startDissolution(
            @PathVariable UUID id,
            @RequestBody DissolutionRequest request,
            @AuthenticationPrincipal User user) {
        
        procedureService.startDissolution(id, request, user.getId(), user.getWorkspaceId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/liquidation")
    @Operation(summary = "Déclencher la procédure de liquidation")
    @PreAuthorize("hasAnyRole('EMPLOYE', 'SUPERVISEUR')")
    public ResponseEntity<Void> startLiquidation(
            @PathVariable UUID id,
            @RequestBody LiquidationRequest request,
            @AuthenticationPrincipal User user) {
        
        procedureService.startLiquidation(id, request, user.getId(), user.getWorkspaceId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/generate/{type}")
    @Operation(summary = "Générer un acte de dissolution ou rapport de liquidation")
    public ResponseEntity<byte[]> generateDocument(
            @PathVariable UUID id,
            @PathVariable String type) {
        
        return ResponseEntity.ok(procedureService.generateDocument(type, id));
    }
}
