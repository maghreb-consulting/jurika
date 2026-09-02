package ma.jurika.supervision.api;

import ma.jurika.supervision.application.SupervisionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/supervision")
public class SupervisionController {

    private final SupervisionService service;

    public SupervisionController(SupervisionService service) {
        this.service = service;
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE')")
    public Map<String, Object> kpis() {
        return service.workspaceKpis();
    }

    @GetMapping("/tickets-per-type")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE')")
    public List<Map<String, Object>> ticketsPerType() {
        return service.ticketsPerType();
    }

    @GetMapping("/tickets-per-statut")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE')")
    public List<Map<String, Object>> ticketsPerStatut() {
        return service.ticketsPerStatut();
    }

    @GetMapping("/tickets-per-employe")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR')")
    public List<Map<String, Object>> ticketsPerEmploye() {
        return service.ticketsPerEmploye();
    }

    @GetMapping("/tickets-last-30-days")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE')")
    public List<Map<String, Object>> ticketsLast30Days() {
        return service.ticketsLast30Days();
    }

    @GetMapping("/platform")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public Map<String, Object> platform() {
        return service.platformKpis();
    }
}
