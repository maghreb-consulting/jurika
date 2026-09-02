package fr.maghreb.gje.services;

import fr.maghreb.gje.config.TenantContextHelper;
import fr.maghreb.gje.dto.ticket.*;
import fr.maghreb.gje.models.*;
import fr.maghreb.gje.models.Ticket.*;
import fr.maghreb.gje.repositories.*;
import fr.maghreb.gje.services.ticket.TicketStatutHandler;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final HistoriqueRepository historiqueRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final DossierRepository dossierRepository;
    /** OCP: Map of TicketStatutHandlers injected by Spring — handler_BLOQUE, handler_EN_COURS, etc. */
    private final Map<String, TicketStatutHandler> statutHandlers;

    @PersistenceContext
    private EntityManager entityManager;

    /** DRY: delegate to shared helper instead of duplicating 3 lines everywhere */
    private void setTenant(UUID workspaceId) {
        TenantContextHelper.setTenant(workspaceId, entityManager);
    }

    @Transactional
    public List<Ticket> getAll(
            UUID workspaceId,
            UUID userId,
            User.Role role) {
        setTenant(workspaceId);
        if (role == User.Role.EMPLOYE) {
            return ticketRepository
                .findByWorkspaceIdAndAssignedTo(
                    workspaceId, userId);
        }
        return ticketRepository
            .findByWorkspaceId(workspaceId);
    }

    @Transactional
    public Ticket getById(UUID id, UUID workspaceId) {
        setTenant(workspaceId);
        return ticketRepository.findById(id)
            .filter(t -> t.getWorkspaceId()
                .equals(workspaceId))
            .orElseThrow(() ->
                new RuntimeException("Ticket introuvable"));
    }

    @Transactional
    public Ticket updateStatut(
            UUID id,
            TicketUpdateStatutRequest request,
            UUID userId,
            UUID workspaceId,
            User.Role role) {

        setTenant(workspaceId);

        Ticket ticket = getById(id, workspaceId);
        TicketStatut ancienStatut = ticket.getStatut();
        TicketStatut nouveauStatut = TicketStatut.valueOf(
            request.getStatut().toUpperCase());

        // Validate transitions
        validateTransition(
            ancienStatut, nouveauStatut, role);

        ticket.setStatut(nouveauStatut);
        ticket.setUpdatedAt(LocalDateTime.now());

        // OCP: State Pattern — dispatch to the correct handler for this new status.
        // To add a new status behavior, create a new TicketStatutHandler @Component.
        // No modification of THIS class is ever needed.
        if (nouveauStatut == TicketStatut.BLOQUE) {
            ticket.setBlockedReason(request.getBlockedReason());
        }
        TicketStatutHandler handler = statutHandlers.get("handler_" + nouveauStatut.name());
        if (handler != null) {
            handler.handle(ticket, userId, role, workspaceId, userRepository, notificationRepository);
        }

        ticketRepository.save(ticket);

        // Log historique
        historiqueRepository.save(Historique.builder()
            .workspaceId(workspaceId)
            .dossierId(ticket.getDossierId())
            .userId(userId)
            .action("TICKET_STATUT_CHANGE")
            .ancienneValeur(ancienStatut.name())
            .nouvelleValeur(nouveauStatut.name())
            .build());

        // Notify assigned user (if changed by someone else)
        if (!ticket.getAssignedTo().equals(userId)) {
            createNotification(
                workspaceId,
                ticket.getAssignedTo(),
                "TICKET_UPDATED",
                "Statut ticket #" + id.toString().substring(0, 8) + " mis à jour : "
                    + nouveauStatut.name());
        }

        return ticket;
    }

    @Transactional
    public Map<String, Object> addComment(
            UUID id,
            TicketCommentRequest request,
            UUID userId,
            UUID workspaceId) {

        setTenant(workspaceId);

        Ticket ticket = getById(id, workspaceId);

        // Save comment in historique
        historiqueRepository.save(Historique.builder()
            .workspaceId(workspaceId)
            .dossierId(ticket.getDossierId())
            .userId(userId)
            .action("TICKET_COMMENTAIRE")
            .nouvelleValeur(request.getComment())
            .build());

        // Notify the other party
        UUID notifyUser = ticket.getAssignedTo()
            .equals(userId)
            ? ticket.getAuteurId()
            : ticket.getAssignedTo();

        createNotification(
            workspaceId,
            notifyUser,
            "TICKET_COMMENT",
            "Nouveau commentaire sur ticket #"
                + id.toString().substring(0, 8));

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Commentaire ajouté");
        result.put("ticket_id", id.toString());
        result.put("comment", request.getComment());
        return result;
    }

    @Transactional
    public List<Map<String, Object>> getKanban(
            UUID workspaceId,
            UUID userId,
            User.Role role) {

        setTenant(workspaceId);

        List<Ticket> tickets;
        if (role == User.Role.EMPLOYE) {
            tickets = ticketRepository
                .findByWorkspaceIdAndAssignedTo(
                    workspaceId, userId);
        } else {
            tickets = ticketRepository
                .findByWorkspaceId(workspaceId);
        }

        Map<String, List<Ticket>> kanban = new HashMap<>();
        for (TicketStatut statut : TicketStatut.values()) {
            kanban.put(statut.name(), new ArrayList<>());
        }

        for (Ticket t : tickets) {
            kanban.get(t.getStatut().name()).add(t);
        }

        List<Map<String, Object>> result =
            new ArrayList<>();
        for (Map.Entry<String, List<Ticket>> entry :
                kanban.entrySet()) {
            Map<String, Object> column = new HashMap<>();
            column.put("statut", entry.getKey());
            column.put("tickets", entry.getValue());
            column.put("count", entry.getValue().size());
            result.add(column);
        }

        return result;
    }

    private void validateTransition(
            TicketStatut ancien,
            TicketStatut nouveau,
            User.Role role) {

        // Matrice de transitions (CDC Page 7)
        if (role == User.Role.EMPLOYE) {
            boolean allowed = switch (ancien) {
                case OUVERT -> nouveau == TicketStatut.EN_COURS;
                case EN_COURS -> EnumSet.of(TicketStatut.EN_REVISION, TicketStatut.TERMINE, TicketStatut.BLOQUE).contains(nouveau);
                case EN_REVISION -> nouveau == TicketStatut.TERMINE; // FIX : EN_REVISION -> TERMINE est autorisé
                case BLOQUE -> nouveau == TicketStatut.EN_COURS;
                case TERMINE -> false;
            };
            
            if (!allowed) {
                throw new RuntimeException("Action non autorisée pour votre rôle (transition " + ancien + " -> " + nouveau + ")");
            }
        } else if (role == User.Role.SUPERVISEUR || role == User.Role.SUPER_ADMIN) {
            // Supervisors have broader rights
            boolean allowed = switch (ancien) {
                case OUVERT -> nouveau == TicketStatut.EN_COURS;
                case EN_COURS -> EnumSet.of(TicketStatut.EN_REVISION, TicketStatut.TERMINE, TicketStatut.BLOQUE).contains(nouveau);
                case EN_REVISION -> EnumSet.of(TicketStatut.EN_COURS, TicketStatut.TERMINE).contains(nouveau);
                case BLOQUE -> EnumSet.of(TicketStatut.EN_COURS, TicketStatut.TERMINE).contains(nouveau);
                case TERMINE -> false;
            };

            if (!allowed) {
                throw new RuntimeException("Transition invalide : " + ancien + " -> " + nouveau);
            }
        }
    }

    private void notifySupervisors(
            UUID workspaceId,
            String type,
            String message) {

        List<User> superviseurs = userRepository
            .findAllByWorkspaceId(workspaceId)
            .stream()
            .filter(u -> u.getRole() ==
                User.Role.SUPERVISEUR)
            .toList();

        for (User sup : superviseurs) {
            createNotification(
                workspaceId,
                sup.getId(),
                type,
                message);
        }
    }

    private void createNotification(
            UUID workspaceId,
            UUID userId,
            String type,
            String message) {
        notificationRepository.save(
            Notification.builder()
                .workspaceId(workspaceId)
                .userId(userId)
                .type(type)
                .message(message)
                .isRead(false)
                .build());
    }
}
