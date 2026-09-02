package ma.jurika.ticket.application;

import ma.jurika.ticket.domain.model.Deadline;
import ma.jurika.ticket.domain.model.DeadlineRule;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Observer : applique les regles d'auto-calcul des deadlines en fonction des evenements ticket.
 * <p>
 * Branche par {@link TransitionTicketUseCase} apres la transition de statut.
 * Conforme au pattern Observer (Plan_Developpement_V2 SOLID) + Strategy ({@link DeadlineRule}).
 */
@Component
public class TicketDeadlineHooks {

    private static final Logger log = LoggerFactory.getLogger(TicketDeadlineHooks.class);

    private final DeadlineUseCase deadlineUseCase;

    public TicketDeadlineHooks(DeadlineUseCase deadlineUseCase) {
        this.deadlineUseCase = deadlineUseCase;
    }

    /**
     * Hook declenche apres une transition de statut ticket. Cable les regles d'echeance
     * pertinentes selon le type de ticket (best-effort : ne casse jamais la transition).
     *
     * <p>Mapping actuel (au passage {@code NOUVEAU -> EN_COURS}) :
     * <ul>
     *   <li><b>CREATION</b> : {@link DeadlineRule#CN_EXPIRY_90D} — le Certificat Negatif est
     *       valide 90 jours et doit servir au depot au RC avant expiration. C'est l'echeance
     *       la plus pertinente en debut de dossier de constitution. Ancree, faute de mieux, sur
     *       la date de creation du ticket (la date de delivrance du CN n'est pas capturee dans
     *       ticket-service — voir la note "evenement manquant" du livrable).</li>
     *   <li><b>LIQUIDATION</b> : {@link DeadlineRule#LIQUIDATION_PUBLI_16J} (delai indicatif 16 j).</li>
     * </ul>
     *
     * <p>L'echeance CNSS ({@link DeadlineRule#CNSS_DECL_30D}) est cablee separement, a
     * l'obtention du RC (saisie des identifiants post-immatriculation), dans
     * {@code DossierIdentifiantsService}. {@link DeadlineRule#RC_DEPOT_3M} (redondant avec le
     * CN 90 j) et {@link DeadlineRule#STEP_STALE_7D} (alerte planifiee) restent des perspectives.
     *
     * <p>Idempotence : {@link DeadlineUseCase#computeAuto} deduplique par (ticket, regle), donc
     * un re-passage en EN_COURS (retry) rafraichit l'echeance existante sans la dupliquer.
     */
    public void onTransition(Ticket ticket, TicketStatut from, TicketStatut to) {
        if (to != TicketStatut.EN_COURS) {
            return;
        }
        Map<String, Object> meta = Map.of(
                "from", from.name(), "to", to.name(), "trigger", "transition_en_cours");
        switch (ticket.type()) {
            case CREATION -> computeAuto(ticket, DeadlineRule.CN_EXPIRY_90D, ticket.createdAt(), meta);
            case LIQUIDATION -> computeAuto(ticket, DeadlineRule.LIQUIDATION_PUBLI_16J, ticket.createdAt(), meta);
            default -> { /* aucun cablage d'echeance pour les autres types a ce stade */ }
        }
    }

    /**
     * Hook generique pour les services workflow (workflow-service appellera via Feign Phase 5+).
     * Pour le moment, expose une API interne reutilisable.
     */
    public Deadline computeAuto(Ticket ticket, DeadlineRule rule, Instant anchor, Map<String, Object> metadata) {
        try {
            return deadlineUseCase.computeAuto(new DeadlineUseCase.AutoComputeCommand(
                    ticket.workspaceId(), ticket.id(),
                    ticket.dossierId(),
                    rule, anchor, ticket.creeParId(), metadata));
        } catch (Exception ex) {
            // Les deadlines auto sont best-effort : on ne casse pas la transition pour autant
            log.warn("Echec calcul deadline auto {} pour ticket {}: {}", rule, ticket.id(), ex.getMessage());
            return null;
        }
    }
}
