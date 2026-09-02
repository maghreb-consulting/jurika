package fr.maghreb.gje.controllers;

import fr.maghreb.gje.dto.admin.CreateWorkspaceRequest;
import fr.maghreb.gje.services.AdminWorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import fr.maghreb.gje.dto.admin.WorkspaceDetailDTO;
import org.springframework.context.annotation.Profile;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController {

    private final AdminWorkspaceService adminService;

    @PostMapping("/workspaces")
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody
            CreateWorkspaceRequest request) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(adminService.createWorkspace(request));
    }

    @GetMapping("/workspaces/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(adminService.getStats());
    }

    @GetMapping("/workspaces/{id}")
    public ResponseEntity<WorkspaceDetailDTO> getWorkspaceById(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getWorkspaceDetail(id));
    }

    @GetMapping("/test/second-employee-id")
    @Profile("dev")
    public ResponseEntity<Map<String, String>> getSecondEmployeeId() {
        return ResponseEntity.ok(Map.of(
            "userId", adminService.getSecondEmployeeId().toString()
        ));
    }

    @PatchMapping("/workspaces/{id}")
    public ResponseEntity<Map<String, Object>> toggle(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        boolean active = (boolean) body
            .getOrDefault("is_active", true);
        String raison = (String) body
            .getOrDefault("raison", "");
        return ResponseEntity.ok(
            adminService.toggleWorkspace(
                id, active, raison));
    }
}
