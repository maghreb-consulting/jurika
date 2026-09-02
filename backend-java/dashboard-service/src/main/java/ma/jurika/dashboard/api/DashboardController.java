package ma.jurika.dashboard.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dashboard.api.dto.DashboardDtos.*;
import ma.jurika.dashboard.application.DashboardQueryService;
import ma.jurika.dashboard.domain.DashboardScope;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Sprint 10 -- 6 endpoints REST (4 dashboards + invalidate + export CSV).
 *
 * RBAC RG-DASH-01 :
 *  - /super-admin -> ROLE_SUPER_ADMIN seul
 *  - /superviseur -> ROLE_SUPERVISEUR
 *  - /employe     -> ROLE_EMPLOYE
 *  - /client      -> ROLE_CLIENT
 */
@RestController
@RequestMapping("/api/v1/dashboards")
@Tag(name = "Dashboards",
        description = "Sprint 10 -- agregats KPIs multi-roles (SuperAdmin, Superviseur, Employe, Client) avec cache Redis 60s + invalidation event-driven RabbitMQ + RLS multi-tenant.")
public class DashboardController {

    private final DashboardQueryService service;

    public DashboardController(DashboardQueryService service) {
        this.service = service;
    }

    @GetMapping("/super-admin")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Vue SUPER_ADMIN cross-workspace (RG-DASH-02 audit log)")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "Role insuffisant")
    public SuperAdminDashboardDto superAdmin() {
        return service.loadSuperAdmin();
    }

    @GetMapping("/superviseur")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')")
    @Operation(summary = "Vue SUPERVISEUR pour le workspace courant (RG-DASH-01)")
    public SuperviseurDashboardDto superviseur() {
        return service.loadSuperviseur();
    }

    @GetMapping("/employe")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')")
    @Operation(summary = "Vue EMPLOYE pour l utilisateur courant (sa charge, ses tickets)")
    public EmployeDashboardDto employe(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.loadEmploye(user.userId());
    }

    @GetMapping("/client")
    @PreAuthorize("hasAnyAuthority('ROLE_CLIENT','ROLE_SUPER_ADMIN')")
    @Operation(summary = "Vue CLIENT pour l utilisateur courant (ses dossiers, RG-DASH-09)")
    public ClientDashboardDto client(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.loadClient(user.userId());
    }

    @PostMapping("/invalidate")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Force la purge du cache pour un workspace (debug / hotfix)")
    public ResponseEntity<Long> invalidate(@RequestParam(required = false) UUID workspaceId,
                                            @RequestParam(required = false) DashboardScope scope,
                                            @RequestParam(required = false) UUID actorId) {
        if (scope != null) {
            UUID ws = workspaceId != null ? workspaceId : TenantContext.get();
            return ResponseEntity.ok(service.invalidate(scope, ws, actorId));
        }
        UUID ws = workspaceId != null ? workspaceId : TenantContext.get();
        return ResponseEntity.ok(service.invalidateWorkspace(ws));
    }

    /**
     * RG-DASH-10 : export CSV des series temporelles (SUPERVISEUR ou SUPER_ADMIN).
     */
    @GetMapping(value = "/export/csv", produces = "text/csv")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')")
    @Operation(summary = "Export CSV des series temporelles (tickets 30j, signups 30j)")
    public ResponseEntity<String> exportCsv(@RequestParam(defaultValue = "evolution-tickets-30j") String metric) {
        StringBuilder sb = new StringBuilder("day,count\n");
        if ("evolution-tickets-30j".equals(metric)) {
            var dto = service.loadSuperviseur();
            for (var d : dto.evolutionTickets30j()) {
                sb.append(d.day()).append(',').append(d.count()).append('\n');
            }
        } else if ("signups-30j".equals(metric)) {
            var dto = service.loadSuperAdmin();
            for (var d : dto.signups30d()) {
                sb.append(d.day()).append(',').append(d.count()).append('\n');
            }
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + metric + ".csv\"")
                .contentType(MediaType.valueOf("text/csv"))
                .body(sb.toString());
    }
}
