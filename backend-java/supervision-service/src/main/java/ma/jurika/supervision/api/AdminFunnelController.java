package ma.jurika.supervision.api;

import ma.jurika.supervision.infrastructure.persistence.BusinessEventJpaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 11 TASK 6 — Endpoint admin funnel commercial.
 *
 * GET /api/v1/admin/funnel?days=30
 * Reserve SUPER_ADMIN. Retourne counts par event_type sur la fenetre demandee
 * (defaut 30 jours, max 365).
 *
 * Le calcul de conversion rate par etape (PAGE_VIEWED -> SIGNUP_STARTED ->
 * SIGNUP_COMPLETED -> FIRST_LOGIN -> FIRST_TICKET_CREATED) se fait cote
 * frontend FunnelDashboard (TASK 6 frontend, differe a TASK 8 si pas le temps).
 */
@RestController
@RequestMapping("/api/v1/admin/funnel")
public class AdminFunnelController {

    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 365;

    private final BusinessEventJpaRepository eventRepo;

    public AdminFunnelController(BusinessEventJpaRepository eventRepo) {
        this.eventRepo = eventRepo;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> funnel(@RequestParam(defaultValue = "30") int days) {
        if (days < 1) days = 1;
        if (days > MAX_DAYS) days = MAX_DAYS;
        Instant now = Instant.now();
        Instant from = now.minus(Duration.ofDays(days));

        List<Object[]> rows = eventRepo.funnelByEventType(from, now);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            counts.put((String) row[0], ((Number) row[1]).longValue());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("from", from.toString());
        result.put("to", now.toString());
        result.put("days", days);
        result.put("counts", counts);
        return result;
    }
}
