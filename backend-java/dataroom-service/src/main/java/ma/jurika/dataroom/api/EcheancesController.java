package ma.jurika.dataroom.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.dataroom.api.dto.DataroomDtos.EcheanceSummary;
import ma.jurika.dataroom.api.dto.DataroomDtos.MarquerEcheanceTraitee;
import ma.jurika.dataroom.application.EcheancesService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dataroom")
@Tag(name = "Echeances Fiscales", description = "RG-DF20 -- alertes echeances DGI Maroc (10 types).")
public class EcheancesController {

    private final EcheancesService service;

    public EcheancesController(EcheancesService service) {
        this.service = service;
    }

    @GetMapping("/dossiers/{dossierId}/echeances")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_SUPERVISEUR','ROLE_EMPLOYE','ROLE_CLIENT')")
    @Operation(summary = "Liste echeances d'un dossier (filtre periode ou statut)")
    public List<EcheanceSummary> list(@PathVariable UUID dossierId,
                                        @RequestParam(required = false) String from,
                                        @RequestParam(required = false) String to,
                                        @RequestParam(required = false) String statut) {
        LocalDate fromD = (from != null && !from.isBlank()) ? LocalDate.parse(from) : null;
        LocalDate toD = (to != null && !to.isBlank()) ? LocalDate.parse(to) : null;
        return service.listForDossier(dossierId, fromD, toD, statut);
    }

    @PatchMapping("/echeances/{echeanceId}/marquer-traite")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    @Operation(summary = "Marquer une echeance comme traitee (lien optionnel vers document fiscal)")
    public EcheanceSummary marquerTraitee(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable UUID echeanceId,
                                            @Valid @RequestBody(required = false) MarquerEcheanceTraitee req) {
        UUID docId = req != null ? req.documentId() : null;
        String note = req != null ? req.note() : null;
        return service.marquerTraitee(echeanceId, docId, note, user.userId());
    }
}
