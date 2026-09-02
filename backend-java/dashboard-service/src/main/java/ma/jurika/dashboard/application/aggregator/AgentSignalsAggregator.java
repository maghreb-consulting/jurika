package ma.jurika.dashboard.application.aggregator;

import ma.jurika.dashboard.api.dto.AgentDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Lot IA-1 — Agrege les 3 signaux de l'agent copilote pour un couple
 * {@code (workspaceId, employeeId)}, en <b>lecture seule</b> (SELECT only).
 *
 * <p>Modele : {@code EmployeDashboardAggregator} (JdbcTemplate, joins scopes par
 * {@code entreprise_dossiers.responsable_id}). Le jurika_user de dashboard-service
 * est BYPASSRLS -> chaque requete filtre <b>explicitement</b> {@code workspace_id = ?}
 * (defense multi-tenant, cf. dashboard-bypassrls-fix) EN PLUS du scoping employe.
 *
 * <p>Ses dossiers = {@code entreprise_dossiers.responsable_id = employeeId} (Lot S).
 */
@Component
public class AgentSignalsAggregator {

    /**
     * Borne basse du retard « a ne pas manquer » cote Copilote : au-dela, une echeance
     * ratee n'est plus reclamee par l'agent (elle harcelerait sur du backlog fossile).
     * Elle reste visible ET traitable dans le panneau Echeances du dossier (retard
     * complet), pas ici. Le futur reste borne a J+7 (fenetre courte, cf. requetes).
     */
    private static final int RETARD_MAX_JOURS = 45;

    private final JdbcTemplate jdbc;

    public AgentSignalsAggregator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AgentSignals aggregate(UUID workspaceId, UUID employeeId) {
        List<EcheanceSignal> echeances = collectEcheances(workspaceId, employeeId);
        List<TacheSignal> resteAFaire = collectResteAFaire(workspaceId, employeeId);

        int depassees = (int) echeances.stream().filter(EcheanceSignal::depassee).count();
        long tickets = resteAFaire.stream().filter(t -> "TICKET".equals(t.type())).count();
        long demandes = resteAFaire.stream().filter(t -> "DEMANDE".equals(t.type())).count();

        AgentCounts counts = new AgentCounts(
                echeances.size(), depassees, (int) tickets, (int) demandes);

        return new AgentSignals(echeances, resteAFaire, counts);
    }

    // ---------------------------------------------------------------------
    // 1. Echeances : deadlines (ticket-service) + alertes fiscales (dataroom)
    //    ouvertes, dues <= J+7 OU deja depassees, sur les dossiers de l'employe.
    // ---------------------------------------------------------------------
    private List<EcheanceSignal> collectEcheances(UUID ws, UUID emp) {
        List<EcheanceSignal> out = new ArrayList<>();

        // (a) deadlines auto-calculees (killer feature ticket-service)
        out.addAll(jdbc.query("""
                SELECT d.id, d.title, d.due_at, d.severity, d.ticket_id,
                       dos.id AS dossier_id, dos.raison_sociale
                FROM deadlines d
                LEFT JOIN tickets t
                       ON t.id = d.ticket_id AND t.workspace_id = d.workspace_id
                JOIN entreprise_dossiers dos
                       ON dos.id = COALESCE(d.dossier_id, t.dossier_id)
                      AND dos.workspace_id = d.workspace_id
                      AND dos.responsable_id = ?
                WHERE d.workspace_id = ?
                  AND d.statut = 'OUVERTE'
                  AND d.due_at <= NOW() + INTERVAL '7 day'
                  AND d.due_at >= NOW() - INTERVAL '%d day'
                ORDER BY d.due_at ASC
                LIMIT 30
                """.formatted(RETARD_MAX_JOURS), (rs, rn) -> {
                    LocalDate date = rs.getTimestamp("due_at").toInstant()
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                    UUID ticketId = (UUID) rs.getObject("ticket_id");
                    UUID dossierId = (UUID) rs.getObject("dossier_id");
                    long jours = ChronoUnit.DAYS.between(LocalDate.now(), date);
                    String lien = ticketId != null
                            ? "/workflows/" + ticketId
                            : "/data-rooms?dossier=" + dossierId;
                    return new EcheanceSignal(
                            "DEADLINE", (UUID) rs.getObject("id"), rs.getString("title"),
                            date, rs.getString("severity"), jours < 0, jours,
                            dossierId, rs.getString("raison_sociale"), lien);
                }, emp, ws));

        // (b) alertes d'echeances fiscales (dataroom-service)
        out.addAll(jdbc.query("""
                SELECT a.id, a.type_echeance, a.date_echeance,
                       dos.id AS dossier_id, dos.raison_sociale
                FROM dataroom_alertes_echeances a
                JOIN entreprise_dossiers dos
                       ON dos.id = a.dossier_id
                      AND dos.workspace_id = a.workspace_id
                      AND dos.responsable_id = ?
                WHERE a.workspace_id = ?
                  AND a.statut IN ('PLANIFIEE','ENVOYEE')
                  AND a.date_echeance <= CURRENT_DATE + 7
                  AND a.date_echeance >= CURRENT_DATE - INTERVAL '%d day'
                ORDER BY a.date_echeance ASC
                LIMIT 30
                """.formatted(RETARD_MAX_JOURS), (rs, rn) -> {
                    LocalDate date = rs.getDate("date_echeance").toLocalDate();
                    long jours = ChronoUnit.DAYS.between(LocalDate.now(), date);
                    String severite = jours < 0 ? "CRITICAL" : (jours <= 3 ? "WARNING" : "INFO");
                    UUID dossierId = (UUID) rs.getObject("dossier_id");
                    return new EcheanceSignal(
                            "ALERTE_FISCALE", (UUID) rs.getObject("id"), rs.getString("type_echeance"),
                            date, severite, jours < 0, jours,
                            dossierId, rs.getString("raison_sociale"),
                            "/data-rooms?dossier=" + dossierId + "&tab=fiscal");
                }, emp, ws));

        out.sort(Comparator.comparing(EcheanceSignal::date));
        return out;
    }

    // ---------------------------------------------------------------------
    // 2. Reste-a-faire : tickets NOUVEAU/EN_COURS + demandes NON_TRAITEE
    //    des dossiers de l'employe.
    // ---------------------------------------------------------------------
    private List<TacheSignal> collectResteAFaire(UUID ws, UUID emp) {
        List<TacheSignal> out = new ArrayList<>();

        out.addAll(jdbc.query("""
                SELECT t.id, t.reference, t.titre, t.statut, t.created_at,
                       dos.id AS dossier_id, dos.raison_sociale
                FROM tickets t
                JOIN entreprise_dossiers dos
                       ON dos.id = t.dossier_id
                      AND dos.workspace_id = t.workspace_id
                      AND dos.responsable_id = ?
                WHERE t.workspace_id = ?
                  AND t.statut IN ('NOUVEAU','EN_COURS')
                ORDER BY t.created_at ASC
                LIMIT 50
                """, (rs, rn) -> new TacheSignal(
                        "TICKET", (UUID) rs.getObject("id"), rs.getString("reference"),
                        rs.getString("titre"), rs.getString("statut"),
                        ageDays(rs.getTimestamp("created_at")),
                        (UUID) rs.getObject("dossier_id"), rs.getString("raison_sociale"),
                        "/workflows/" + rs.getObject("id")),
                emp, ws));

        out.addAll(jdbc.query("""
                SELECT dc.id, dc.sujet, dc.statut, dc.created_at,
                       dos.id AS dossier_id, dos.raison_sociale
                FROM dataroom_demandes_client dc
                JOIN entreprise_dossiers dos
                       ON dos.id = dc.dossier_id
                      AND dos.workspace_id = dc.workspace_id
                      AND dos.responsable_id = ?
                WHERE dc.workspace_id = ?
                  AND dc.statut = 'NON_TRAITEE'
                ORDER BY dc.created_at ASC
                LIMIT 50
                """, (rs, rn) -> {
                    UUID dossierId = (UUID) rs.getObject("dossier_id");
                    return new TacheSignal(
                            "DEMANDE", (UUID) rs.getObject("id"), null,
                            rs.getString("sujet"), rs.getString("statut"),
                            ageDays(rs.getTimestamp("created_at")),
                            dossierId, rs.getString("raison_sociale"),
                            "/data-rooms?dossier=" + dossierId + "&tab=demandes");
                }, emp, ws));

        out.sort(Comparator.comparingLong(TacheSignal::ancienneteJours).reversed());
        return out;
    }

    private static long ageDays(Timestamp ts) {
        if (ts == null) return 0;
        return ChronoUnit.DAYS.between(ts.toInstant(), Instant.now());
    }
}
