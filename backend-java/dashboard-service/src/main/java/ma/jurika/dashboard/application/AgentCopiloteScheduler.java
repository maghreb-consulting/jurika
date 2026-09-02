package ma.jurika.dashboard.application;

import ma.jurika.common.notification.NotificationPublisher;
import ma.jurika.dashboard.api.dto.AgentDtos.AgentBriefing;
import ma.jurika.dashboard.api.dto.AgentDtos.AgentCounts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lot IA-1 — Declenchement planifie du briefing copilote.
 *
 * <p>Chaque matin (08:00 Africa/Casablanca), pour chaque EMPLOYE actif, on
 * genere le briefing du jour puis on pousse une notification "AGENT_BRIEFING".
 * Best-effort par employe (un echec n'interrompt pas les autres).
 *
 * <p>L'enumeration des employes se fait en SQL direct (users est global ; le
 * jurika_user dashboard est BYPASSRLS) — l'agent reste en lecture seule.
 */
@Component
public class AgentCopiloteScheduler {

    private static final Logger log = LoggerFactory.getLogger(AgentCopiloteScheduler.class);

    private final JdbcTemplate jdbc;
    private final AgentCopiloteService copilote;
    private final NotificationPublisher notifier;

    public AgentCopiloteScheduler(JdbcTemplate jdbc,
                                  AgentCopiloteService copilote,
                                  NotificationPublisher notifier) {
        this.jdbc = jdbc;
        this.copilote = copilote;
        this.notifier = notifier;
    }

    /** Briefing quotidien 08:00 (heure du Maroc). */
    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Casablanca")
    public void runDaily() {
        List<UUID[]> employes = listActiveEmployes();
        log.info("Agent copilote : generation du briefing pour {} employe(s) actif(s)", employes.size());
        int ok = 0;
        for (UUID[] pair : employes) {
            if (generateAndNotify(pair[0], pair[1])) ok++;
        }
        log.info("Agent copilote : {}/{} briefings notifies", ok, employes.size());
    }

    /** Genere + notifie un employe. Retourne false et log en cas d'echec (best-effort). */
    public boolean generateAndNotify(UUID workspaceId, UUID employeeId) {
        try {
            AgentBriefing briefing = copilote.generateBriefing(workspaceId, employeeId);
            AgentCounts c = briefing.signaux().counts();
            Map<String, Object> meta = new HashMap<>();
            meta.put("echeances", c.echeances());
            meta.put("depassees", c.echeancesDepassees());
            meta.put("tickets", c.tickets());
            meta.put("demandes", c.demandes());
            notifier.notifyUser(employeeId, workspaceId, "AGENT_BRIEFING",
                    "Votre briefing du jour", briefing.summary(), "/dashboard#copilote", meta);
            return true;
        } catch (Exception ex) {
            log.warn("Agent copilote : briefing echoue (ws={}, emp={}) : {}",
                    workspaceId, employeeId, ex.getMessage());
            return false;
        }
    }

    private List<UUID[]> listActiveEmployes() {
        return jdbc.query("""
                SELECT workspace_id, id
                FROM users
                WHERE role = 'EMPLOYE' AND status = 'ACTIVE'
                """, (rs, rn) -> new UUID[]{
                        (UUID) rs.getObject("workspace_id"), (UUID) rs.getObject("id")});
    }
}
