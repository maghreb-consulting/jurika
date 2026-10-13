package ma.jurika.ticket.api;

import jakarta.validation.Valid;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.pdf.CabinetIdentity;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.ticket.api.dto.DeboursDto;
import ma.jurika.ticket.api.dto.DeboursRequest;
import ma.jurika.ticket.application.AccesDeboursTicket;
import ma.jurika.ticket.application.DeboursUseCase;
import ma.jurika.ticket.application.EtatDeboursService;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.infrastructure.persistence.WorkspaceViewEntity;
import ma.jurika.ticket.infrastructure.persistence.WorkspaceViewJpaRepository;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Debours d'un ticket (cahier des charges 10.1).
 * <ul>
 *   <li>lecture : superviseur, employe en charge, client du dossier si sa permission de
 *       consultation le prevoit (RG-DEB-03, RG-CLI-01) -- {@link AccesDeboursTicket} ;</li>
 *   <li>ecriture : employe en charge seulement (RG-DOS-01), ticket ni clos ni annule
 *       (RG-TKT-11), chaque geste trace (P7) -- {@link DeboursUseCase} ;</li>
 *   <li>etat des debours au format PDF a la charte JURIKA (RG-DEB-02, RG-FIC-08 ; lot L5b E3 :
 *       plus de sortie Word), memes droits que la lecture, et pour le client la permission de
 *       telecharger.</li>
 * </ul>
 * Ticket d'un autre workspace ou d'un autre dossier : 404.
 */
@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/debours")
public class DeboursController {

    private static final ZoneId CASABLANCA = ZoneId.of("Africa/Casablanca");

    private final DeboursUseCase deboursUseCase;
    private final EtatDeboursService etatDebours;
    private final WorkspaceViewJpaRepository workspaces;
    private final AccesDeboursTicket acces;
    private final Clock clock;

    public DeboursController(DeboursUseCase deboursUseCase, EtatDeboursService etatDebours,
                             WorkspaceViewJpaRepository workspaces, AccesDeboursTicket acces, Clock clock) {
        this.deboursUseCase = deboursUseCase;
        this.etatDebours = etatDebours;
        this.workspaces = workspaces;
        this.acces = acces;
        this.clock = clock;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYE','SUPERVISEUR','CLIENT') and !hasRole('SUPER_ADMIN')")
    public Map<String, Object> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @PathVariable UUID ticketId) {
        acces.lecture(actor, ticketId);
        var summary = deboursUseCase.listForTicket(actor.workspaceId(), ticketId);
        List<DeboursDto> items = summary.items().stream().map(DeboursDto::from).toList();
        return Map.of("items", items, "total", summary.total());
    }

    // SUPERVISEUR = observation : lecture et etat des debours, aucune ecriture (403).
    @PostMapping
    @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
    public ResponseEntity<DeboursDto> create(@AuthenticationPrincipal AuthenticatedUser actor,
                                             @PathVariable UUID ticketId,
                                             @Valid @RequestBody DeboursRequest req) {
        acces.ecriture(actor, ticketId); // RG-DOS-01
        var d = deboursUseCase.ajouter(actor.workspaceId(), actor.userId(), ticketId, req.saisie());
        return ResponseEntity.status(HttpStatus.CREATED).body(DeboursDto.from(d));
    }

    @PatchMapping("/{deboursId}")
    @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
    public DeboursDto update(@AuthenticationPrincipal AuthenticatedUser actor,
                             @PathVariable UUID ticketId,
                             @PathVariable UUID deboursId,
                             @Valid @RequestBody DeboursRequest req) {
        acces.ecriture(actor, ticketId); // RG-DOS-01
        return DeboursDto.from(deboursUseCase.modifier(actor.workspaceId(), actor.userId(), ticketId, deboursId,
                req.saisie()));
    }

    @DeleteMapping("/{deboursId}")
    @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor,
                                       @PathVariable UUID ticketId,
                                       @PathVariable UUID deboursId) {
        acces.ecriture(actor, ticketId); // RG-DOS-01
        deboursUseCase.supprimer(actor.workspaceId(), actor.userId(), ticketId, deboursId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lot L5b (E3, RG-DEB-02, RG-FIC-08) : etat des debours du ticket au format PDF a la charte
     * JURIKA, regenere a chaque demande a partir des lignes en base. Lectures en une transaction
     * en lecture seule (RLS).
     */
    @Transactional(readOnly = true)
    @GetMapping("/etat-pdf")
    @PreAuthorize("hasAnyRole('EMPLOYE','SUPERVISEUR','CLIENT') and !hasRole('SUPER_ADMIN')")
    public ResponseEntity<byte[]> etatPdf(@AuthenticationPrincipal AuthenticatedUser actor,
                                          @PathVariable UUID ticketId) {
        Ticket ticket = acces.telechargement(actor, ticketId);
        CabinetIdentity cabinet = workspaces.findById(actor.workspaceId())
                .map(DeboursController::cabinet)
                .orElseThrow(() -> new NotFoundException("Cabinet inconnu"));
        byte[] pdf = etatDebours.etablir(actor.workspaceId(), ticket, cabinet, LocalDate.now(clock.withZone(CASABLANCA)));
        String filename = "Etat_des_debours_" + ticket.reference() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /** En-tete du cabinet : donnees du workspace (nom affiche sur les documents, sinon denomination). */
    private static CabinetIdentity cabinet(WorkspaceViewEntity w) {
        String nom = (w.getNomAfficheDocuments() == null || w.getNomAfficheDocuments().isBlank())
                ? w.getName() : w.getNomAfficheDocuments().trim();
        return new CabinetIdentity(nom,
                w.getLogoBytes() == null || w.getLogoBytes().length == 0 ? null : w.getLogoBytes(),
                blanc(w.getLogoContentType()), blanc(w.getAdresse()), blanc(w.getTelephone()),
                blanc(w.getContactEmail()), blanc(w.getSiteWeb()), blanc(w.getIce()), blanc(w.getRcNumber()),
                blanc(w.getIfFiscal()));
    }

    private static String blanc(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
