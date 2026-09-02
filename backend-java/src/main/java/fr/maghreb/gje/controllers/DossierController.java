package fr.maghreb.gje.controllers;

import fr.maghreb.gje.dto.dossier.*;
import fr.maghreb.gje.models.*;
import fr.maghreb.gje.services.DossierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;

@RestController
@RequestMapping("/api/v1/dossiers")
@RequiredArgsConstructor
public class DossierController {

    private final DossierService dossierService;

    @PostMapping
    @PreAuthorize("hasAnyRole('EMPLOYE', 'SUPERVISEUR')")
    public ResponseEntity<EntrepriseDossier> create(
            @Valid @RequestBody DossierCreateRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(dossierService.create(
                req,
                user.getId(),
                user.getWorkspaceId()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYE', 'SUPERVISEUR')")
    public ResponseEntity<List<EntrepriseDossier>> getAll(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
            dossierService.getAll(
                user.getWorkspaceId(),
                user.getId(),
                user.getRole()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYE', 'SUPERVISEUR')")
    public ResponseEntity<EntrepriseDossier> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
            dossierService.getById(
                id, user.getWorkspaceId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYE', 'SUPERVISEUR')")
    public ResponseEntity<?> update(
            @PathVariable UUID id,
            @RequestBody DossierUpdateRequest req,
            @AuthenticationPrincipal User user) {
        try {
            return ResponseEntity.ok(
                dossierService.update(
                    id, req,
                    user.getId(),
                    user.getWorkspaceId()));
        } catch (RuntimeException e) {
            if (e.getMessage().contains("archivé")) {
                return ResponseEntity.status(403)
                    .body(Map.of("error", e.getMessage()));
            }
            if (e.getMessage().contains("introuvable")) {
                return ResponseEntity.status(404)
                    .body(Map.of("error", e.getMessage()));
            }
            return ResponseEntity.status(400)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/transferer")
    @PreAuthorize("hasRole('SUPERVISEUR')")
    public ResponseEntity<Map<String, Object>> transferer(
            @PathVariable UUID id,
            @Valid @RequestBody TransfertRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
            dossierService.transferer(
                id, req,
                user.getId(),
                user.getWorkspaceId()));
    }

    @PostMapping("/{id}/archiver")
    @PreAuthorize("hasRole('SUPERVISEUR')")
    public ResponseEntity<Map<String, Object>> archiver(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
            dossierService.archiver(
                id,
                user.getId(),
                user.getWorkspaceId()));
    }
}
