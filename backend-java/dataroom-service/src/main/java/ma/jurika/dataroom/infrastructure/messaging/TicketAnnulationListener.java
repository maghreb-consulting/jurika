package ma.jurika.dataroom.infrastructure.messaging;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.application.DeleteDataroomUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fix 2026-06-07 (BUG 2) — Listener Rabbit qui supprime
 * automatiquement le dataroom quand le ticket d'origine
 * (CREATION / IMPORT) passe a ANNULE.
 *
 * <p>Source : exchange {@code jurika.events}, routing key
 * {@code ticket.status_changed}. Publie par
 * {@code RabbitTicketEventPublisher} cote ticket-service.
 *
 * <p>Garde-fous (3 conditions cumulatives, sinon NO-OP) :
 * <ol>
 *   <li>Statut cible = {@code ANNULE}</li>
 *   <li>Type ticket ∈ {CREATION, IMPORT}</li>
 *   <li>Dossier porte {@code created_by_ticket_id == ticketId} ET
 *       {@code statut = EN_CONSTITUTION} ET aucun autre ticket actif
 *       (NOUVEAU/EN_COURS) ne le reference.</li>
 * </ol>
 *
 * <p>Cas critique non couvert (volontaire) : IMPORT qui REUTILISE un
 * dossier ACTIVE preexistant via {@code DossierIdempotenceLookup}.
 * Dans ce cas {@code created_by_ticket_id IS NULL} (statut != EN_CONSTITUTION
 * au moment du markCreatedByTicket dans CreateTicketUseCase) -> NO-OP, le
 * dataroom est CONSERVE.
 *
 * <p>Best-effort : toute exception est loggee mais l'annulation du ticket
 * reste committee cote ticket-service. Idempotent : si le dataroom est
 * deja supprime, DeleteDataroomUseCase retourne alreadyDeleted=true.
 */
@Component
@ConditionalOnBean(ConnectionFactory.class)
public class TicketAnnulationListener {

    private static final Logger log = LoggerFactory.getLogger(TicketAnnulationListener.class);

    public static final String QUEUE = "dataroom.ticket-annulation";
    public static final String EXCHANGE = "jurika.events";
    public static final String ROUTING_KEY = "ticket.status_changed";

    private final DeleteDataroomUseCase deleteDataroom;

    @PersistenceContext
    private EntityManager em;

    public TicketAnnulationListener(DeleteDataroomUseCase deleteDataroom) {
        this.deleteDataroom = deleteDataroom;
    }

    @Bean
    public Queue ticketAnnulationQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public TopicExchange ticketAnnulationExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Binding ticketAnnulationBinding(Queue ticketAnnulationQueue,
                                            TopicExchange ticketAnnulationExchange) {
        return BindingBuilder.bind(ticketAnnulationQueue)
                .to(ticketAnnulationExchange).with(ROUTING_KEY);
    }

    @RabbitListener(queues = QUEUE)
    public void onTicketStatusChanged(Map<String, Object> event) {
        try {
            String statut = asString(event.get("statut"));
            if (!"ANNULE".equals(statut)) return;

            String type = asString(event.get("type"));
            if (!"CREATION".equals(type) && !"IMPORT".equals(type)) return;

            UUID workspaceId = asUuid(event.get("workspaceId"));
            UUID ticketId = asUuid(event.get("ticketId"));
            UUID dossierId = asUuid(event.get("dossierId"));
            if (workspaceId == null || ticketId == null || dossierId == null) {
                log.debug("BUG2 listener : event ignore (champs manquants) : {}", event);
                return;
            }

            // Garde-fou DB : verifier que le dossier a bien ete autocreate par
            // CE ticket et qu'il est encore EN_CONSTITUTION. Necessite RLS off
            // ou tenant context positionne.
            TenantContext.set(workspaceId);

            @SuppressWarnings("unchecked")
            List<Object[]> rows = (List<Object[]>) em.createNativeQuery("""
                    SELECT created_by_ticket_id, statut FROM entreprise_dossiers
                     WHERE id = ?1 AND workspace_id = ?2
                    """)
                    .setParameter(1, dossierId)
                    .setParameter(2, workspaceId)
                    .getResultList();
            if (rows.isEmpty()) {
                log.debug("BUG2 listener : dossier {} introuvable workspace {}",
                        dossierId, workspaceId);
                return;
            }
            Object[] row = rows.get(0);
            Object createdByObj = row[0];
            String dossierStatut = row[1] == null ? null : row[1].toString();

            if (createdByObj == null) {
                log.info("BUG2 listener : dossier {} non autocreate (created_by_ticket_id NULL) -> dataroom conserve",
                        dossierId);
                return;
            }
            UUID createdBy = (UUID) createdByObj;
            if (!createdBy.equals(ticketId)) {
                log.info("BUG2 listener : dossier {} autocreate par ticket {} != ticket annule {} -> dataroom conserve",
                        dossierId, createdBy, ticketId);
                return;
            }
            if (!"EN_CONSTITUTION".equals(dossierStatut)) {
                log.info("BUG2 listener : dossier {} statut={} (pas EN_CONSTITUTION) -> dataroom conserve",
                        dossierId, dossierStatut);
                return;
            }

            // Aucun autre ticket actif (NOUVEAU/EN_COURS) ne reference le dossier ?
            Number activeOthers = (Number) em.createNativeQuery("""
                    SELECT COUNT(*) FROM tickets
                     WHERE workspace_id = ?1 AND dossier_id = ?2
                       AND id <> ?3 AND statut IN ('CREATION_TICKET','GENERATION_DOCUMENTS','DEROULEMENT_DEMARCHE')
                    """)
                    .setParameter(1, workspaceId)
                    .setParameter(2, dossierId)
                    .setParameter(3, ticketId)
                    .getSingleResult();
            if (activeOthers.intValue() > 0) {
                log.info("BUG2 listener : dossier {} : {} autre(s) ticket(s) actif(s) -> dataroom conserve",
                        dossierId, activeOthers);
                return;
            }

            // Toutes les conditions remplies : on supprime le dataroom.
            // Audit : action DATAROOM_DELETED, metadata implicite (le motif
            // peut etre retrace par l'enchainement TICKET_TRANSITIONED -> ANNULE).
            DeleteDataroomUseCase.Result result = deleteDataroom.execute(
                    new DeleteDataroomUseCase.Command(
                            workspaceId, dossierId,
                            /* deletedBy = */ null,  // system, pas d'acteur user
                            /* ipAddress = */ "system:ticket-annulation",
                            /* userAgent = */ "TicketAnnulationListener/1.0"));
            log.info("BUG2 listener : dataroom dossier {} supprime suite annulation ticket {} (deleted={}, radied={})",
                    dossierId, ticketId, result.dossierDeleted(), result.dossierRadied());

        } catch (Exception ex) {
            // Best-effort : on log mais on ne propage pas (sinon Rabbit requeue
            // a l'infini un message qu'on ne pourra jamais traiter).
            log.error("BUG2 listener : echec traitement annulation ticket (event={}) : {}",
                    event, ex.getMessage(), ex);
        }
    }

    private static String asString(Object v) {
        return v == null ? null : v.toString();
    }

    private static UUID asUuid(Object v) {
        if (v == null) return null;
        try {
            return UUID.fromString(v.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
