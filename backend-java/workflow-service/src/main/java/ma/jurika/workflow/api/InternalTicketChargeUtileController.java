package ma.jurika.workflow.api;

import ma.jurika.common.security.TenantContext;
import ma.jurika.workflow.application.ChargeUtileServeur;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Lot L3 (P2) : endpoint interne service-to-service -- charge utile de la creation,
 * construite depuis le magasin, pour ai-service. Non expose par la gateway (seuls
 * {@code /api/v1/**} le sont). Le workspace et l'employe sont fournis par ai-service,
 * qui les tire du JWT verifie ; le workspace devient le workspace courant (RLS) et
 * reste filtre en SQL.
 */
@RestController
@RequestMapping("/internal/tickets")
public class InternalTicketChargeUtileController {

    private final ChargeUtileServeur chargeUtile;

    public InternalTicketChargeUtileController(ChargeUtileServeur chargeUtile) {
        this.chargeUtile = chargeUtile;
    }

    @GetMapping("/{ticketId}/charge-utile-creation")
    public Map<String, Object> creation(@PathVariable UUID ticketId, @RequestParam UUID workspaceId,
                                        @RequestParam UUID employeId) {
        TenantContext.set(workspaceId);
        try {
            return chargeUtile.creation(workspaceId, ticketId, employeId);
        } finally {
            TenantContext.clear();
        }
    }
}
