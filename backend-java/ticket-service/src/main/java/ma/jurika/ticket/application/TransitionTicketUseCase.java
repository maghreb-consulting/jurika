package ma.jurika.ticket.application;

import ma.jurika.common.audit.Auditable;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.observability.BusinessMetrics;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketComment;
import ma.jurika.ticket.domain.model.TicketCommentType;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.CommentRepository;
import ma.jurika.ticket.domain.port.TicketEventPublisher;
import ma.jurika.ticket.domain.port.TicketRepository;
import ma.jurika.ticket.domain.state.TicketStateMachine;
import ma.jurika.ticket.domain.state.TicketStatutHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class TransitionTicketUseCase {

    private final TicketRepository ticketRepository;
    private final TicketStateMachine stateMachine;
    private final CommentRepository commentRepository;
    private final TicketEventPublisher eventPublisher;
    private final TicketDeadlineHooks deadlineHooks;
    private final BusinessMetrics businessMetrics;
    private final ma.jurika.ticket.domain.port.RepriseTicket reprise;

    public TransitionTicketUseCase(TicketRepository ticketRepository, TicketStateMachine stateMachine,
                                    CommentRepository commentRepository, TicketEventPublisher eventPublisher,
                                    TicketDeadlineHooks deadlineHooks,
                                    BusinessMetrics businessMetrics,
                                    ma.jurika.ticket.domain.port.RepriseTicket reprise) {
        this.ticketRepository = ticketRepository;
        this.stateMachine = stateMachine;
        this.commentRepository = commentRepository;
        this.eventPublisher = eventPublisher;
        this.deadlineHooks = deadlineHooks;
        this.businessMetrics = businessMetrics;
        this.reprise = reprise;
    }

    public record Command(UUID workspaceId, UUID ticketId, TicketStatut target, String comment, UUID actorId) {}

    @Transactional
    @Auditable(action = "TICKET_TRANSITIONED", resourceType = "ticket", resourceIdExpr = "#cmd.ticketId()")
    public Ticket execute(Command cmd) {
        TenantContext.set(cmd.workspaceId());
        Ticket current = ticketRepository.findById(cmd.workspaceId(), cmd.ticketId())
                .orElseThrow(() -> new NotFoundException("Ticket inconnu"));

        // Fix 2026-06-07 (BUG 2) — Idempotence : double-annulation no-op.
        // Si le ticket est deja dans le statut cible (typiquement ANNULE
        // reemis par un retry front), on retourne tel quel SANS rejouer
        // les hooks/events/audit. Empeche aussi un 409 ConflictException
        // remonte par AnnuleHandler (qui refuse "Ticket deja annule").
        if (current.statut() == cmd.target()) {
            return current;
        }

        // Auto-assignation a l'acteur au demarrage de la production (statut 2
        // « Generation des documents ») si le ticket n'a pas encore de responsable.
        if (cmd.target() == TicketStatut.GENERATION_DOCUMENTS && current.assigneId() == null) {
            ticketRepository.updateAssignment(current.id(), cmd.actorId(),
                    current.priorite(), current.deadline(), current.titre(), current.description());
            current = ticketRepository.findById(cmd.workspaceId(), cmd.ticketId())
                    .orElseThrow(() -> new NotFoundException("Ticket inconnu"));
        }

        // Lot L4a (E4, RG-TKT-05) : un ticket annule ne se reprend qu'a son statut anterieur,
        // et jamais apres un retrait (il faut alors un nouveau ticket).
        boolean estReprise = current.statut() == TicketStatut.ANNULE && cmd.target() != TicketStatut.ANNULE;
        if (estReprise) {
            TicketStatut anterieur = reprise.statutAnterieur(cmd.workspaceId(), current.id())
                    .orElseThrow(() -> new ma.jurika.common.exception.ConflictException(
                            "Statut ant\u00e9rieur introuvable : la reprise est impossible sans lui."));
            if (cmd.target() != anterieur) {
                throw new ma.jurika.common.exception.ConflictException(
                        "Un ticket annul\u00e9 reprend son statut ant\u00e9rieur : " + anterieur.name() + ".");
            }
            if (reprise.retraitEffectue(cmd.workspaceId(), current.id())) {
                throw new ma.jurika.common.exception.ConflictException(
                        "Un retrait a \u00e9t\u00e9 effectu\u00e9 aupr\u00e8s d'une administration : la reprise est "
                                + "impossible ; ouvrez un nouveau ticket.");
            }
        }

        stateMachine.assertCanTransition(current, cmd.target(),
                new TicketStatutHandler.TransitionContext(cmd.comment(), cmd.actorId()));

        TicketStatut previous = current.statut();
        Instant now = Instant.now();
        Instant clotureAt = cmd.target() == TicketStatut.CLOTURE_DOSSIER ? now : current.clotureAt();
        Instant annuleAt = cmd.target() == TicketStatut.ANNULE ? now : current.annuleAt();
        String motif = cmd.target() == TicketStatut.ANNULE ? cmd.comment() : current.annulationMotif();

        Ticket updated = ticketRepository.updateStatut(current.id(), cmd.target(), motif, clotureAt, annuleAt);
        // Lot L4a (E4, RG-TKT-03) : statut atteint et auteur de l'annulation conserves.
        if (cmd.target() == TicketStatut.ANNULE) {
            reprise.enregistrerAnnulation(cmd.workspaceId(), current.id(), previous, cmd.actorId());
        } else if (estReprise) {
            reprise.effacer(cmd.workspaceId(), current.id());
        }

        TicketCommentType commentType = switch (cmd.target()) {
            case ANNULE -> TicketCommentType.ANNULATION;
            case CLOTURE_DOSSIER -> TicketCommentType.CLOTURE;
            default -> TicketCommentType.TRANSITION_STATUT;
        };
        String contenu = cmd.comment() == null || cmd.comment().isBlank()
                ? "Transition vers " + cmd.target()
                : cmd.comment();
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("from", previous.name());
        meta.put("to", cmd.target().name());
        // Lot L4c (E2, RG-CLO-04) : la cloture met a jour la fiche societe (identifiants
        // obtenus pendant le parcours, colonnes vides seulement ; ecarts signales).
        if (cmd.target() == TicketStatut.CLOTURE_DOSSIER && ficheCloture != null) {
            ma.jurika.ticket.domain.port.FicheCloture.Resultat fiche =
                    ficheCloture.mettreAJour(cmd.workspaceId(), current.id());
            meta.put("ficheReprise", fiche.repris());
            meta.put("ficheEcarts", fiche.ecarts());
        }
        commentRepository.create(cmd.workspaceId(), current.id(), cmd.actorId(),
                commentType, contenu, meta);

        eventPublisher.publishTicketStatusChanged(updated, previous);
        deadlineHooks.onTransition(updated, previous, cmd.target());
        businessMetrics.ticketStateTransition(previous.name(), cmd.target().name(), cmd.workspaceId());
        return updated;
    }

    private ma.jurika.ticket.domain.port.FicheCloture ficheCloture;

    /** Lot L4c (E2) : mise a jour de la fiche a la cloture ; absente dans certains tests. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setFicheCloture(ma.jurika.ticket.domain.port.FicheCloture ficheCloture) {
        this.ficheCloture = ficheCloture;
    }

    /** Lot L4a (E4) : ce que l'ecran annonce avant une reprise (statut retrouve, ou pourquoi non). */
    public record EtatReprise(boolean annule, TicketStatut statutAnterieur, boolean possible, String motif) {}

    @Transactional(readOnly = true)
    public EtatReprise etatReprise(UUID workspaceId, UUID ticketId) {
        TenantContext.set(workspaceId);
        Ticket t = ticketRepository.findById(workspaceId, ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket inconnu"));
        if (t.statut() != TicketStatut.ANNULE) return new EtatReprise(false, null, false, null);
        TicketStatut anterieur = reprise.statutAnterieur(workspaceId, ticketId).orElse(null);
        if (anterieur == null) {
            return new EtatReprise(true, null, false, "Statut ant\u00e9rieur introuvable : la reprise est impossible sans lui.");
        }
        if (reprise.retraitEffectue(workspaceId, ticketId)) {
            return new EtatReprise(true, anterieur, false,
                    "Un retrait a \u00e9t\u00e9 effectu\u00e9 aupr\u00e8s d'une administration : ouvrez un nouveau ticket.");
        }
        return new EtatReprise(true, anterieur, true, null);
    }

}
