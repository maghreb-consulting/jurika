package fr.maghreb.gje.controllers;

import fr.maghreb.gje.dto.document.*;
import fr.maghreb.gje.models.*;
import fr.maghreb.gje.services.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping("/{id}/download")
    public ResponseEntity<Document> download(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user,
            HttpServletRequest request) {
        return ResponseEntity.ok(
            documentService.download(
                id,
                user,
                request.getRemoteAddr()));
    }

    @GetMapping("/dossier/{dossierId}")
    public ResponseEntity<List<Document>> getByDossier(
            @PathVariable UUID dossierId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
            documentService.getByDossier(
                dossierId,
                user.getWorkspaceId()));
    }

    @PostMapping("/upload")
    public ResponseEntity<Document> upload(
            @Valid @RequestBody
            DocumentUploadRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(documentService.upload(
                request,
                user.getId(),
                user.getWorkspaceId()));
    }

    @PostMapping("/generate-ia")
    public ResponseEntity<Map<String, Object>> generateIA(
            @Valid @RequestBody
            DocumentGenerateRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
            documentService.generateIA(
                request,
                user.getId(),
                user.getWorkspaceId()));
    }

    @PostMapping("/{id}/valider")
    public ResponseEntity<Document> valider(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
            documentService.valider(
                id,
                user.getId(),
                user.getWorkspaceId()));
    }

    @PutMapping("/{id}/editer")
    public ResponseEntity<Document> editer(
            @PathVariable UUID id,
            @Valid @RequestBody
            DocumentEditRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
            documentService.editer(
                id, request,
                user.getId(),
                user.getWorkspaceId()));
    }

    @PostMapping("/dossier/{dossierId}/zip")
    public ResponseEntity<Map<String, Object>> zip(
            @PathVariable UUID dossierId,
            @AuthenticationPrincipal User user,
            HttpServletRequest request) {
        return ResponseEntity.ok(
            documentService.generateZip(
                dossierId,
                user,
                request.getRemoteAddr()));
    }
}
