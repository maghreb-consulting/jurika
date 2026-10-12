package ma.jurika.ticket.application;

import ma.jurika.common.notification.NotificationPublisher;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.application.DemarcheUseCases.PointAttention;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.port.AlertesDelaisRegistre;
import ma.jurika.ticket.domain.port.AlertesDelaisRegistre.TicketASurveiller;
import ma.jurika.ticket.domain.port.DossierRepository;
import ma.jurika.ticket.domain.port.SuperviseursDuCabinet;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lot L5 (E9, RG-COM-08) : notification, dans l'application, des delais de
 * demarches a echeance.
 *
 * <p>Source unique : les points d'attention du deroulement
 * ({@link DemarcheUseCases#vue}), c'est-a-dire les echeances legales
 * CALCULEES (point de depart pose, ligne non traitee) -- jamais une date
 * fabriquee (principe P4). Paliers : approche (J-15), critique (J-3), depasse.
 *
 * <p>Destinataires : l'employe responsable du dossier (RG-DOS-01) a chaque
 * palier ; les superviseurs actifs du cabinet en plus pour un delai depasse
 * (delai a risque, vue d'observation). Jamais un autre cabinet : chaque ticket
 * est traite dans son workspace, et le realtime-service refuse un destinataire
 * hors cabinet.
 *
 * <p>Chaque palier d'une echeance n'est notifie qu'une fois (registre V43).
 */
@Service
public class AlertesDelaisService {

    private static final Logger log = LoggerFactory.getLogger(AlertesDelaisService.class);

    /** Type de notification connu du realtime-service. */
    public static final String TYPE = "DEADLINE_DUE";

    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AlertesDelaisRegistre registre;
    private final DemarcheUseCases demarches;
    private final TicketRepository tickets;
    private final DossierRepository dossiers;
    private final SuperviseursDuCabinet superviseurs;
    private final NotificationPublisher notifier;

    public AlertesDelaisService(AlertesDelaisRegistre registre, DemarcheUseCases demarches,
                                TicketRepository tickets, DossierRepository dossiers,
                                SuperviseursDuCabinet superviseurs, NotificationPublisher notifier) {
        this.registre = registre;
        this.demarches = demarches;
        this.tickets = tickets;
        this.dossiers = dossiers;
        this.superviseurs = superviseurs;
        this.notifier = notifier;
    }

    /** Bilan d'un passage : tickets parcourus, notifications emises, tickets en erreur. */
    public record Bilan(int tickets, int notifications, int erreurs) {}

    public Bilan detecter() {
        int parcourus = 0;
        int notifications = 0;
        int erreurs = 0;
        for (TicketASurveiller cible : registre.ticketsASurveiller()) {
            parcourus++;
            try {
                TenantContext.set(cible.workspaceId());
                notifications += traiter(cible);
            } catch (Exception ex) {
                // Un ticket en erreur n'arrete pas la detection des autres ; il est journalise.
                erreurs++;
                log.error("Alertes de delais : ticket {} (cabinet {}) non traite : {}",
                        cible.ticketId(), cible.workspaceId(), ex.getMessage(), ex);
            } finally {
                TenantContext.clear();
            }
        }
        if (erreurs > 0 || notifications > 0) {
            log.info("Alertes de delais : {} ticket(s), {} notification(s), {} erreur(s)",
                    parcourus, notifications, erreurs);
        }
        return new Bilan(parcourus, notifications, erreurs);
    }

    private int traiter(TicketASurveiller cible) {
        UUID ws = cible.workspaceId();
        List<PointAttention> points = demarches.vue(ws, cible.ticketId()).avancement().pointsAttention();
        if (points == null || points.isEmpty()) return 0;

        Ticket ticket = tickets.findById(ws, cible.ticketId()).orElse(null);
        if (ticket == null) return 0;
        EntrepriseDossier dossier = ticket.dossierId() == null ? null
                : dossiers.findById(ws, ticket.dossierId()).orElse(null);
        UUID responsable = dossier == null ? null : dossier.responsableId();
        String raisonSociale = dossier == null ? null : dossier.raisonSociale();

        int emises = 0;
        for (PointAttention p : points) {
            Set<UUID> destinataires = new LinkedHashSet<>();
            if (responsable != null) destinataires.add(responsable);
            if ("DEPASSE".equals(p.severite())) destinataires.addAll(superviseurs.actifs(ws));
            if (destinataires.isEmpty()) {
                log.warn("Alertes de delais : ticket {} ligne {} sans destinataire (dossier sans responsable)",
                        ticket.id(), p.ordre());
                continue;
            }
            if (!registre.retenirSiNouveau(ws, ticket.id(), p.ordre(), p.echeance(), p.severite())) continue;
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("ticketId", ticket.id().toString());
            if (ticket.dossierId() != null) meta.put("dossierId", ticket.dossierId().toString());
            meta.put("ordre", p.ordre());
            meta.put("echeance", p.echeance().toString());
            meta.put("severite", p.severite());
            for (UUID d : destinataires) {
                notifier.notifyUser(d, ws, TYPE, titre(p), message(p, ticket, raisonSociale),
                        "/tickets/" + ticket.id(), meta);
                emises++;
            }
        }
        return emises;
    }

    static String titre(PointAttention p) {
        return switch (p.severite()) {
            case "DEPASSE" -> "Délai dépassé";
            case "CRITIQUE" -> "Échéance imminente";
            default -> "Échéance proche";
        };
    }

    static String message(PointAttention p, Ticket ticket, String raisonSociale) {
        StringBuilder sb = new StringBuilder();
        sb.append("« ").append(p.libelle()).append(" »");
        if (raisonSociale != null && !raisonSociale.isBlank()) sb.append(" — dossier ").append(raisonSociale);
        if (ticket.reference() != null) sb.append(" (ticket ").append(ticket.reference()).append(")");
        sb.append(" : échéance le ").append(p.echeance().format(DATE_FR));
        long j = p.joursRestants();
        if (j < 0) {
            sb.append(", dépassée de ").append(-j).append(-j > 1 ? " jours." : " jour.");
        } else if (j == 0) {
            sb.append(", aujourd’hui.");
        } else {
            sb.append(", dans ").append(j).append(j > 1 ? " jours." : " jour.");
        }
        return sb.toString();
    }
}
