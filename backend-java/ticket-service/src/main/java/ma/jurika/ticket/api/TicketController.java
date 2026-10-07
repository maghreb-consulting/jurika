package ma.jurika.ticket.api;

import jakarta.validation.Valid;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.ticket.api.dto.CreateTicketRequest;
import ma.jurika.ticket.api.dto.PageResponse;
import ma.jurika.ticket.api.dto.TicketDto;
import ma.jurika.ticket.api.dto.TransitionRequest;
import ma.jurika.ticket.api.dto.UpdateTicketRequest;
import ma.jurika.ticket.application.CreateTicketUseCase;
import ma.jurika.ticket.application.SearchTicketsUseCase;
import ma.jurika.ticket.application.TransitionTicketUseCase;
import ma.jurika.ticket.application.UpdateTicketUseCase;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;
import ma.jurika.ticket.domain.model.TicketComment;
import ma.jurika.ticket.domain.port.CommentRepository;
import ma.jurika.ticket.domain.port.DossierRepository;
import ma.jurika.ticket.domain.port.TicketFilter;
import ma.jurika.ticket.domain.port.TicketRepository;
import ma.jurika.ticket.infrastructure.persistence.DossierJpaRepository;
import ma.jurika.ticket.infrastructure.persistence.UserViewEntity;
import ma.jurika.ticket.infrastructure.persistence.UserViewJpaRepository;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final CreateTicketUseCase createTicketUseCase;
    private final UpdateTicketUseCase updateTicketUseCase;
    private final TransitionTicketUseCase transitionTicketUseCase;
    private final SearchTicketsUseCase searchTicketsUseCase;
    private final TicketRepository ticketRepository;
    private final DossierRepository dossierRepository;
    private final CommentRepository commentRepository;
    private final UserViewJpaRepository userDirectory;
    private final DossierJpaRepository dossierDirectory;

    public TicketController(CreateTicketUseCase createTicketUseCase,
                            UpdateTicketUseCase updateTicketUseCase,
                            TransitionTicketUseCase transitionTicketUseCase,
                            SearchTicketsUseCase searchTicketsUseCase,
                            TicketRepository ticketRepository,
                            DossierRepository dossierRepository,
                            CommentRepository commentRepository,
                            UserViewJpaRepository userDirectory,
                            DossierJpaRepository dossierDirectory) {
        this.createTicketUseCase = createTicketUseCase;
        this.updateTicketUseCase = updateTicketUseCase;
        this.transitionTicketUseCase = transitionTicketUseCase;
        this.searchTicketsUseCase = searchTicketsUseCase;
        this.ticketRepository = ticketRepository;
        this.dossierRepository = dossierRepository;
        this.commentRepository = commentRepository;
        this.userDirectory = userDirectory;
        this.dossierDirectory = dossierDirectory;
    }

    @PostMapping
    // SUPERVISEUR = oversight only : la hierarchie des roles l'autoriserait sinon
    // (SUPERVISEUR > EMPLOYE). Denial explicite -> 403 (super_admin exclu aussi).
    @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
    public ResponseEntity<TicketDto> create(@AuthenticationPrincipal AuthenticatedUser actor,
                                             @Valid @RequestBody CreateTicketRequest req) {
        CreateTicketUseCase.CompanyInfo info = req.companyInfo() == null
                ? null
                : new CreateTicketUseCase.CompanyInfo(req.companyInfo().raisonSociale(),
                        req.companyInfo().formeJuridique());
        Ticket t = createTicketUseCase.execute(new CreateTicketUseCase.Command(
                actor.workspaceId(), req.titre(), req.type(), req.priorite(),
                req.dossierId(), req.assigneId(), actor.userId(),
                req.description(), req.deadline(), info));
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketDto.from(t));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
    public TicketDto update(@AuthenticationPrincipal AuthenticatedUser actor,
                             @PathVariable UUID id,
                             @Valid @RequestBody UpdateTicketRequest req) {
        Ticket t = updateTicketUseCase.execute(new UpdateTicketUseCase.Command(
                actor.workspaceId(), id, req.titre(), req.description(),
                req.priorite(), req.assigneId(), req.deadline(), actor.userId()));
        return TicketDto.from(t);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
    public TicketDto transition(@AuthenticationPrincipal AuthenticatedUser actor,
                                 @PathVariable UUID id,
                                 @Valid @RequestBody TransitionRequest req) {
        Ticket t = transitionTicketUseCase.execute(new TransitionTicketUseCase.Command(
                actor.workspaceId(), id, req.target(), req.comment(), actor.userId()));
        return TicketDto.from(t);
    }

    // Lot L0 (E14, inventaire T2) : lectures directes des depots (ticket,
    // commentaires, dossiers, responsables) en UNE transaction en lecture seule,
    // pour que le workspace courant atteigne la RLS sous jurika_app.
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPERVISEUR','ROLE_EMPLOYE')")
    public TicketDto get(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        Ticket t = ticketRepository.findById(actor.workspaceId(), id)
                .orElseThrow(() -> new NotFoundException("Ticket inconnu"));
        // RG-U08 : EMPLOYE accede a ses tickets (assigne OU createur) OU a ceux des
        // dossiers dont il est responsable (coherence avec la recherche : un ticket
        // clos d'un dossier transfere reste ouvrable par le nouvel employe).
        if (actor.role() == ma.jurika.common.security.Role.EMPLOYE
                && !actor.userId().equals(t.assigneId())
                && !actor.userId().equals(t.creeParId())
                && !isResponsableOfTicketDossier(actor, t)) {
            throw new NotFoundException("Ticket inconnu");
        }
        // Vue detail : expose le « motif de reprise » (transition ANNULE -> EN_COURS
        // la plus recente) pour un ticket qui a ete repris. Inutile de le calculer si
        // le ticket est actuellement ANNULE : le front affiche alors le motif
        // d'annulation, pas celui de reprise.
        String repriseMotif = t.statut() == TicketStatut.ANNULE
                ? null
                : latestRepriseMotif(actor.workspaceId(), t.id());
        return TicketDto.from(t, null, null, repriseMotif);
    }

    /**
     * Contenu du commentaire de la transition de reprise la plus recente
     * ({@code from=ANNULE}), ou {@code null} si le ticket n'a jamais ete repris.
     * Les commentaires sont deja tries du plus recent au plus ancien : le premier
     * match est donc la reprise la plus recente.
     *
     * <p>Depuis les cinq statuts du guide, une reprise peut viser n'importe lequel
     * des quatre statuts du parcours : on ne teste donc plus la destination, mais
     * seulement le fait de quitter ANNULE.
     */
    private String latestRepriseMotif(UUID workspaceId, UUID ticketId) {
        return commentRepository.findByTicket(workspaceId, ticketId).stream()
                .filter(c -> c.metadata() != null
                        && "ANNULE".equals(String.valueOf(c.metadata().get("from")))
                        && !"ANNULE".equals(String.valueOf(c.metadata().get("to"))))
                .map(TicketComment::contenu)
                .findFirst()
                .orElse(null);
    }

    /** True si l'acteur est le responsable durable du dossier porteur du ticket. */
    private boolean isResponsableOfTicketDossier(AuthenticatedUser actor, Ticket t) {
        if (t.dossierId() == null) return false;
        return dossierRepository.findById(actor.workspaceId(), t.dossierId())
                .map(d -> actor.userId().equals(d.responsableId()))
                .orElse(false);
    }

    // Lot L0 (E14, inventaire T2) : lectures directes des depots (ticket,
    // commentaires, dossiers, responsables) en UNE transaction en lecture seule,
    // pour que le workspace courant atteigne la RLS sous jurika_app.
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_SUPERVISEUR','ROLE_EMPLOYE')")
    public PageResponse<TicketDto> search(@AuthenticationPrincipal AuthenticatedUser actor,
                                           @RequestParam(required = false) List<TicketStatut> statuts,
                                           @RequestParam(required = false) List<TicketType> types,
                                           @RequestParam(required = false) List<TicketPriorite> priorites,
                                           @RequestParam(required = false) UUID assigneId,
                                           @RequestParam(required = false) UUID dossierId,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(defaultValue = "createdAt") String sortBy,
                                           @RequestParam(defaultValue = "true") boolean desc,
                                           @RequestParam(defaultValue = "20") int limit,
                                           @RequestParam(defaultValue = "0") int offset) {
        // RG-U08 : EMPLOYE ne voit que SES tickets — assigne_id OU cree_par_id == userId,
        //          OU tickets des dossiers dont il est responsable (responsable_id == userId).
        //          Ce 3e critere fait apparaitre l'historique CLOTURE/ANNULE d'un dossier
        //          transfere sans reecrire l'assigne (qui a traite reste preserve).
        // RG-U04 : SUPERVISEUR voit tous les tickets de son workspace.
        boolean isEmploye = actor.role() == ma.jurika.common.security.Role.EMPLOYE;
        UUID effectiveAssigneId = isEmploye ? null : assigneId;
        UUID assigneOrCreeParId = isEmploye ? actor.userId() : null;
        UUID responsableId = isEmploye ? actor.userId() : null;
        TicketFilter filter = new TicketFilter(
                actor.workspaceId(),
                statuts == null ? Collections.emptyList() : statuts,
                types == null ? Collections.emptyList() : types,
                priorites == null ? Collections.emptyList() : priorites,
                effectiveAssigneId, assigneOrCreeParId, responsableId, dossierId, q, sortBy, desc);
        var result = searchTicketsUseCase.execute(filter, limit, offset);
        return new PageResponse<>(
                enrichWithResponsables(actor.workspaceId(), result.items()),
                result.total(), limit, offset);
    }

    /**
     * Resout le « responsable » de chaque ticket de la page en « Prenom Nom »,
     * en 2 requetes batch au plus (dossiers + users) — jamais de N+1.
     *
     * <p>Responsable effectif, par ordre de priorite :
     * <ol>
     *   <li>{@code responsable_id} du dossier porteur — owner durable et
     *       <b>transfert-aware</b> (V9) : apres un transfert, le NOUVEL employe
     *       responsable s'affiche a la place de l'ancien, meme si le ticket garde
     *       son ancien {@code assigne_id} (non reassigne, cf. Lot Y) ;</li>
     *   <li>sinon {@code assigne_id} — l'employe qui a pris le ticket en charge ;</li>
     *   <li>sinon {@code cree_par_id} — l'employe createur (les tickets NOUVEAU
     *       sont ainsi attribues a leur createur).</li>
     * </ol>
     * Id introuvable (compte purge) -> nom null -> "Non assigne" cote UI (jamais
     * l'UUID).
     */
    private List<TicketDto> enrichWithResponsables(UUID workspaceId, List<Ticket> tickets) {
        if (tickets.isEmpty()) {
            return List.of();
        }
        // 1) Responsable durable (transfert-aware) de TOUS les dossiers de la page.
        List<UUID> dossierIds = tickets.stream()
                .map(Ticket::dossierId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, UUID> dossierResponsable = dossierIds.isEmpty()
                ? Collections.emptyMap()
                : dossierDirectory.findResponsablesByWorkspaceAndIdIn(workspaceId, dossierIds).stream()
                        .filter(v -> v.getResponsableId() != null)
                        .collect(Collectors.toMap(
                                DossierJpaRepository.DossierResponsableView::getId,
                                DossierJpaRepository.DossierResponsableView::getResponsableId));

        // 2) Id effectif du responsable par ticket (dossier -> assigne -> createur).
        Map<UUID, UUID> effectiveByTicket = new HashMap<>();
        for (Ticket t : tickets) {
            UUID dossierResp = t.dossierId() == null ? null : dossierResponsable.get(t.dossierId());
            UUID effective = dossierResp != null ? dossierResp
                    : t.assigneId() != null ? t.assigneId()
                    : t.creeParId();
            if (effective != null) {
                effectiveByTicket.put(t.id(), effective);
            }
        }
        List<UUID> userIds = effectiveByTicket.values().stream().distinct().toList();
        Map<UUID, UserViewEntity> users = userIds.isEmpty()
                ? Collections.emptyMap()
                : userDirectory.findAllByWorkspaceIdAndIdIn(workspaceId, userIds).stream()
                        .collect(Collectors.toMap(UserViewEntity::getId, Function.identity()));

        return tickets.stream()
                .map(t -> {
                    UUID uid = effectiveByTicket.get(t.id());
                    UserViewEntity u = uid == null ? null : users.get(uid);
                    return u == null
                            ? TicketDto.from(t)
                            : TicketDto.from(t, u.getFirstName(), u.getLastName());
                })
                .toList();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> hardDelete(@PathVariable UUID id) {
        throw new UnsupportedOperationException("Suppression interdite — utilisez l'annulation");
    }
}
