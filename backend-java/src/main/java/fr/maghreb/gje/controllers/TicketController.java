package fr.maghreb.gje.controllers;

import fr.maghreb.gje.dto.ticket.*;
import fr.maghreb.gje.models.*;
import fr.maghreb.gje.services.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation
    .AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping
    public ResponseEntity<List<Ticket>> getAll(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
            ticketService.getAll(
                user.getWorkspaceId(),
                user.getId(),
                user.getRole()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Ticket> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
            ticketService.getById(
                id, user.getWorkspaceId()));
    }

    @PatchMapping("/{id}/statut")
    public ResponseEntity<?> updateStatut(
            @PathVariable UUID id,
            @Valid @RequestBody
            TicketUpdateStatutRequest request,
            @AuthenticationPrincipal User user) {
        try {
            return ResponseEntity.ok(
                ticketService.updateStatut(
                    id, request,
                    user.getId(),
                    user.getWorkspaceId(),
                    user.getRole()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(400)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/comment")
    public ResponseEntity<Map<String, Object>> comment(
            @PathVariable UUID id,
            @Valid @RequestBody
            TicketCommentRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
            ticketService.addComment(
                id, request,
                user.getId(),
                user.getWorkspaceId()));
    }

    @GetMapping("/kanban")
    public ResponseEntity<List<Map<String, Object>>>
            kanban(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
            ticketService.getKanban(
                user.getWorkspaceId(),
                user.getId(),
                user.getRole()));
    }
}
