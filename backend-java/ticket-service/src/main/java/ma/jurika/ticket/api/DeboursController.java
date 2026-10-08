package ma.jurika.ticket.api;

import jakarta.validation.Valid;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.ticket.api.dto.DeboursDto;
import ma.jurika.ticket.api.dto.DeboursRequest;
import ma.jurika.ticket.application.DeboursUseCase;
import ma.jurika.common.pdf.CabinetIdentity;
import ma.jurika.ticket.domain.port.TicketRepository;
import ma.jurika.ticket.infrastructure.pdf.DeboursPdfGenerator;
import ma.jurika.ticket.infrastructure.persistence.WorkspaceViewJpaRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/debours")
public class DeboursController {

    private final DeboursUseCase deboursUseCase;
    private final TicketRepository ticketRepository;
    private final DeboursPdfGenerator pdfGenerator;
    private final WorkspaceViewJpaRepository workspaces;

    public DeboursController(DeboursUseCase deboursUseCase, TicketRepository ticketRepository,
                              DeboursPdfGenerator pdfGenerator,
                              WorkspaceViewJpaRepository workspaces) {
        this.deboursUseCase = deboursUseCase;
        this.ticketRepository = ticketRepository;
        this.pdfGenerator = pdfGenerator;
        this.workspaces = workspaces;
    }

    // Lot L0 (E22, arb. 8) : lecture reservee aux roles du cabinet (EMPLOYE,
    // SUPERVISEUR), dans leur workspace ; aucun acces client en L0 (RG-DEB-03 :
    // lot L1). Ticket d'un autre workspace : 404 (et non une liste vide).
    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYE','SUPERVISEUR')")
    public Map<String, Object> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                     @PathVariable UUID ticketId) {
        ticketRepository.findById(actor.workspaceId(), ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket inconnu"));
        var summary = deboursUseCase.listForTicket(actor.workspaceId(), ticketId);
        List<DeboursDto> items = summary.items().stream().map(DeboursDto::from).toList();
        BigDecimal total = summary.total();
        return Map.of("items", items, "total", total);
    }

    @PostMapping
    // SUPERVISEUR = oversight only : lecture/export PDF des debours OK, mais aucune
    // ecriture. Denial explicite -> 403 (super_admin exclu aussi).
    @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
    public ResponseEntity<DeboursDto> create(@AuthenticationPrincipal AuthenticatedUser actor,
                                              @PathVariable UUID ticketId,
                                              @Valid @RequestBody DeboursRequest req) {
        var d = deboursUseCase.create(new DeboursUseCase.CreateCommand(
                actor.workspaceId(), ticketId, req.libelle(), req.categorie(),
                req.montant(), req.dateEngagement(), req.pieceJointeUrl(),
                req.pieceJointeFilename(), req.notes(), actor.userId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(DeboursDto.from(d));
    }

    @PatchMapping("/{deboursId}")
    @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
    public DeboursDto update(@AuthenticationPrincipal AuthenticatedUser actor,
                              @PathVariable UUID ticketId,
                              @PathVariable UUID deboursId,
                              @Valid @RequestBody DeboursRequest req) {
        var d = deboursUseCase.update(new DeboursUseCase.UpdateCommand(
                actor.workspaceId(), deboursId, req.libelle(), req.categorie(),
                req.montant(), req.dateEngagement(), req.notes()));
        return DeboursDto.from(d);
    }

    @DeleteMapping("/{deboursId}")
    @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor,
                                        @PathVariable UUID ticketId,
                                        @PathVariable UUID deboursId) {
        deboursUseCase.delete(actor.workspaceId(), deboursId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Genere l'etat des debours du ticket en PDF (RG-T14).
     * Telechargeable + imprimable.
     */
    // Lot L0 (E14, inventaire T2) : lectures directes des depots (ticket,
    // commentaires, dossiers, responsables) en UNE transaction en lecture seule,
    // pour que le workspace courant atteigne la RLS sous jurika_app.
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @GetMapping(value = "/export-pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    // Lot L0 (E22) : comme la liste, roles du cabinet seulement.
    @PreAuthorize("hasAnyRole('EMPLOYE','SUPERVISEUR')")
    public ResponseEntity<byte[]> exportPdf(@AuthenticationPrincipal AuthenticatedUser actor,
                                              @PathVariable UUID ticketId) {
        var ticket = ticketRepository.findById(actor.workspaceId(), ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket inconnu"));
        var summary = deboursUseCase.listForTicket(actor.workspaceId(), ticketId);
        // Papier a en-tete du cabinet (nom + logo + coordonnees + mentions).
        // Resolution unique via CabinetIdentity.resolve.
        CabinetIdentity cabinet = workspaces.findById(actor.workspaceId())
                .map(w -> CabinetIdentity.resolve(
                        w.getNomAfficheDocuments(), w.getName(),
                        w.getLogoBytes(), w.getLogoContentType(),
                        w.getAdresse(), w.getTelephone(), w.getContactEmail(), w.getSiteWeb(),
                        w.getIce(), w.getRcNumber(), w.getIfFiscal()))
                .orElse(CabinetIdentity.ofName(null, null));
        byte[] pdf = pdfGenerator.generate(ticket, summary.items(), null, cabinet);
        String filename = "Etat_debours_" + ticket.reference() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
