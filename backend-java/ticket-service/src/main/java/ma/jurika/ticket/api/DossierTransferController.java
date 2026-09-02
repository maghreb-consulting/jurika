package ma.jurika.ticket.api;

import jakarta.validation.Valid;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.ticket.api.dto.DossierTransferDto;
import ma.jurika.ticket.api.dto.TransferRequestPayload;
import ma.jurika.ticket.application.DossierTransferService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Transfert de dossier entre employes (V9).
 *
 * <p>Le transfert est une operation de PRODUCTION : reserve aux EMPLOYES. Le
 * SUPERVISEUR (oversight only) garde uniquement la lecture des listes (inbox/outbox).
 * La hierarchie des roles (SUPERVISEUR &gt; EMPLOYE) autoriserait sinon le superviseur
 * sur les ecritures -> denial explicite {@code and !hasRole('SUPERVISEUR')}.
 *
 * <ul>
 *   <li>VOIE A — demande/acceptation (EMPLOYE) : {@code POST /dossiers/{id}/transfer-requests},
 *       puis {@code accept|reject} (cible) / {@code cancel} (initiateur).</li>
 *   <li>Listes inbox/outbox pour le panneau "Transferts en attente" (EMPLOYE + SUPERVISEUR).</li>
 * </ul>
 */
@RestController
public class DossierTransferController {

    private final DossierTransferService service;

    public DossierTransferController(DossierTransferService service) {
        this.service = service;
    }

    // -------- VOIE A : demande / acceptation --------

    @PostMapping("/api/v1/dossiers/{dossierId}/transfer-requests")
    @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
    public ResponseEntity<DossierTransferDto> request(@AuthenticationPrincipal AuthenticatedUser actor,
                                                      @PathVariable UUID dossierId,
                                                      @Valid @RequestBody TransferRequestPayload req) {
        var saved = service.requestTransfer(actor.workspaceId(), actor.userId(),
                dossierId, req.toUserId(), req.motif());
        return ResponseEntity.status(HttpStatus.CREATED).body(DossierTransferDto.from(saved));
    }

    @PostMapping("/api/v1/dossier-transfer-requests/{id}/accept")
    @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
    public DossierTransferDto accept(@AuthenticationPrincipal AuthenticatedUser actor,
                                     @PathVariable UUID id) {
        return DossierTransferDto.from(service.acceptTransfer(actor.workspaceId(), actor.userId(), id));
    }

    @PostMapping("/api/v1/dossier-transfer-requests/{id}/reject")
    @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
    public DossierTransferDto reject(@AuthenticationPrincipal AuthenticatedUser actor,
                                     @PathVariable UUID id) {
        return DossierTransferDto.from(service.rejectTransfer(actor.workspaceId(), actor.userId(), id));
    }

    @PostMapping("/api/v1/dossier-transfer-requests/{id}/cancel")
    @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
    public DossierTransferDto cancel(@AuthenticationPrincipal AuthenticatedUser actor,
                                     @PathVariable UUID id) {
        return DossierTransferDto.from(service.cancelTransfer(actor.workspaceId(), actor.userId(), id));
    }

    // -------- Listes (panneau "Transferts en attente") --------

    @GetMapping("/api/v1/dossier-transfer-requests")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPERVISEUR','ROLE_EMPLOYE')")
    public List<DossierTransferDto> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                         @RequestParam(defaultValue = "inbox") String box) {
        var views = "outbox".equalsIgnoreCase(box)
                ? service.listOutbox(actor.workspaceId(), actor.userId())
                : service.listInbox(actor.workspaceId(), actor.userId());
        return views.stream().map(DossierTransferDto::from).toList();
    }
}
