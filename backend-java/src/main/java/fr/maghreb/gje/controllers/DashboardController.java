package fr.maghreb.gje.controllers;

import fr.maghreb.gje.dto.dashboard.*;
import fr.maghreb.gje.models.User;
import fr.maghreb.gje.services.DashboardService;
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
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Tableau de Bord KPIs", description = "Module 15 - Pilotage et Performances (Superviseur)")
@PreAuthorize("hasRole('SUPERVISEUR')")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/kpis")
    @Operation(summary = "Récupérer les KPIs globaux du workspace")
    public ResponseEntity<DashboardKpiDTO> getKpis(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(dashboardService.getGlobalKpis(user.getWorkspaceId()));
    }

    @GetMapping("/performances-employes")
    @Operation(summary = "Récupérer la performance par employé")
    public ResponseEntity<List<EmployeePerformanceDTO>> getEmployeePerformances(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(dashboardService.getEmployeePerformances(user.getWorkspaceId()));
    }

    @GetMapping("/activite-mensuelle")
    @Operation(summary = "Récupérer l'activité sur les 12 derniers mois")
    public ResponseEntity<List<MonthlyActivityDTO>> getMonthlyActivity(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(dashboardService.getMonthlyActivity(user.getWorkspaceId()));
    }

    @GetMapping("/alertes")
    @Operation(summary = "Récupérer les alertes de dossiers en retard et tickets bloqués")
    public ResponseEntity<List<DashboardAlerteDTO>> getAlertes(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(dashboardService.getAlertes(user.getWorkspaceId()));
    }
}
