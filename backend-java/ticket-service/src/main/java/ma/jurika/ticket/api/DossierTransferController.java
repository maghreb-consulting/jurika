package ma.jurika.ticket.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.ticket.domain.model.DossierReaffectation;
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
 *   <li>Lot L1 : reaffectation d'office (SUPERVISEUR seul, RG-DOS-03) et historique des
 *       changements de responsable.</li>
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

    // -------- Lot L1 : reaffectation d'office par le superviseur (RG-DOS-03) --------

    /** Corps de la reaffectation d'office : nouveau responsable (EMPLOYE) et motif obligatoire. */
    public record ReaffectationPayload(@NotNull UUID nouveauResponsableId, @NotBlank String motif) {}

    @PostMapping("/api/v1/dossiers/{dossierId}/reaffectation")
    // La hierarchie SUPER_ADMIN > SUPERVISEUR ouvrirait l'acte a l'equipe JURIKA : exclue.
    @PreAuthorize("hasRole('SUPERVISEUR') and !hasRole('SUPER_ADMIN')")
    public ResponseEntity<DossierReaffectation> reaffecter(@AuthenticationPrincipal AuthenticatedUser actor,
                                                           @PathVariable UUID dossierId,
                                                           @Valid @RequestBody ReaffectationPayload req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.reaffecterDOffice(
                actor.workspaceId(), actor.userId(), dossierId, req.nouveauResponsableId(), req.motif()));
    }

    /** Historique des changements de responsable (superviseur, ou employe responsable). */
    @GetMapping("/api/v1/dossiers/{dossierId}/reaffectations")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPERVISEUR','ROLE_EMPLOYE')")
    public List<DossierReaffectation> historique(@AuthenticationPrincipal AuthenticatedUser actor,
                                                 @PathVariable UUID dossierId) {
        return service.historique(actor.workspaceId(), actor.userId(),
                actor.role() == Role.SUPERVISEUR, dossierId);
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
