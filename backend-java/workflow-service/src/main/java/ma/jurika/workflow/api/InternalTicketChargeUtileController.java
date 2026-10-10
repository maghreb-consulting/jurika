package ma.jurika.workflow.api;

import ma.jurika.common.security.TenantContext;
import ma.jurika.workflow.application.ChargeUtileServeur;
import ma.jurika.workflow.application.DonneesAttenduesService;
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
    private final DonneesAttenduesService donneesAttendues;
    private final ma.jurika.workflow.application.ClausesLibresService clausesLibres;

    public InternalTicketChargeUtileController(ChargeUtileServeur chargeUtile,
                                               DonneesAttenduesService donneesAttendues,
                                               ma.jurika.workflow.application.ClausesLibresService clausesLibres) {
        this.chargeUtile = chargeUtile;
        this.donneesAttendues = donneesAttendues;
        this.clausesLibres = clausesLibres;
    }

    /** Lot L3 (RG-GEN-06) : clauses libres du ticket, relues a chaque generation. */
    @GetMapping("/{ticketId}/clauses-libres")
    public java.util.List<ma.jurika.workflow.application.ClausesLibresService.Clause> clausesLibres(
            @PathVariable UUID ticketId, @RequestParam UUID workspaceId) {
        TenantContext.set(workspaceId);
        try {
            return clausesLibres.lister(workspaceId, ticketId);
        } finally {
            TenantContext.clear();
        }
    }

    /** Lot L3 : donnees externes manquantes d'un document qui vient d'etre genere. */
    public record DonneesAttenduesRequete(String workflowCode, String templateCode,
                                          java.util.List<DonneesAttenduesService.Donnee> donnees) {}

    @org.springframework.web.bind.annotation.PostMapping("/{ticketId}/donnees-attendues")
    public void donneesAttendues(@PathVariable UUID ticketId, @RequestParam UUID workspaceId,
                                 @org.springframework.web.bind.annotation.RequestBody DonneesAttenduesRequete req) {
        TenantContext.set(workspaceId);
        try {
            donneesAttendues.enregistrer(workspaceId, ticketId, req.workflowCode(), req.templateCode(),
                    req.donnees() == null ? java.util.List.of() : req.donnees());
        } finally {
            TenantContext.clear();
        }
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
