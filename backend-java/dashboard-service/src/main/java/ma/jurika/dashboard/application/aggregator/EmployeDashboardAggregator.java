package ma.jurika.dashboard.application.aggregator;

import ma.jurika.dashboard.api.dto.DashboardDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class EmployeDashboardAggregator {

    private final JdbcTemplate jdbc;

    public EmployeDashboardAggregator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public EmployeDashboardDto aggregate(UUID workspaceId, UUID userId) {
        Long mesTickets = jdbc.queryForObject("""
                SELECT COUNT(*) FROM tickets
                WHERE workspace_id = ? AND assigne_id = ? AND statut IN ('CREATION_TICKET','GENERATION_DOCUMENTS','DEROULEMENT_DEMARCHE')
                """, Long.class, workspaceId, userId);

        List<TicketLite> derniers = jdbc.query("""
                SELECT id, reference, titre, statut, priorite, created_at
                FROM tickets
                WHERE workspace_id = ? AND assigne_id = ?
                ORDER BY created_at DESC LIMIT 10
                """, (rs, rn) -> new TicketLite(
                        (UUID) rs.getObject(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5),
                        rs.getTimestamp(6).toInstant()),
                workspaceId, userId);

        // Charge semaine : lun -> ven (5 jours) -- tickets crees + echeances dues.
        // Les echeances sont les delais legaux des DEMARCHES restant a accomplir,
        // calcules dans l unite du guide (jours OU mois calendaires).
        List<DayLoad> charge = jdbc.query(DemarcheEcheancesSql.resoudre("""
                SELECT d.day::date, COALESCE(t.cnt,0) + COALESCE(e.cnt,0) AS charge
                FROM generate_series(CURRENT_DATE, CURRENT_DATE + INTERVAL '4 day',
                                     INTERVAL '1 day') AS d(day)
                LEFT JOIN (
                  SELECT date_trunc('day', created_at)::date AS day, COUNT(*) AS cnt
                  FROM tickets
                  WHERE workspace_id = ? AND assigne_id = ?
                    AND created_at >= CURRENT_DATE
                    AND created_at < CURRENT_DATE + INTERVAL '5 day'
                  GROUP BY 1
                ) t ON t.day = d.day::date
                LEFT JOIN (
                  SELECT {{ECHEANCE}} AS day, COUNT(*) AS cnt
                  FROM tickets t
                  JOIN entreprise_dossiers dos
                        ON dos.id = t.dossier_id AND dos.responsable_id = ?
                {{JOINTURES}}
                {{FILTRE}}
                    AND {{ECHEANCE}} >= CURRENT_DATE
                    AND {{ECHEANCE}} < CURRENT_DATE + INTERVAL '5 day'
                  GROUP BY 1
                ) e ON e.day = d.day::date
                ORDER BY d.day
                """),
                (rs, rn) -> new DayLoad(rs.getDate(1).toLocalDate(), rs.getLong(2)),
                workspaceId, userId, userId, workspaceId);
        // Scoping EMPLOYE (2026-07-03) — l'employe ne voit que les echeances des
        // dossiers dont il est responsable (JOIN entreprise_dossiers.responsable_id).
        // Le SUPERVISEUR passe par SuperviseurDashboardAggregator (vue globale).
        List<EcheanceItem> mesEcheances = jdbc.query(
                DemarcheEcheancesSql.BASE + DemarcheEcheancesSql.SCOPE_RESPONSABLE
                        + DemarcheEcheancesSql.ORDER,
                (rs, rn) -> new EcheanceItem(
                        (UUID) rs.getObject(1), (UUID) rs.getObject(2),
                        rs.getString(3),
                        rs.getDate(4) == null ? null : rs.getDate(4).toLocalDate(),
                        rs.getString(5)),
                workspaceId, userId);

        // Dernieres operations dataroom (audit log)
        // 2026-06-24 — Alignement sur les actions REELLEMENT ecrites a l'upload :
        //   - juridique  -> DOCUMENT_UPLOADED (DataroomJuridiqueService.@Auditable)
        // Les actions COMPTABLE_UPLOADED / FISCAL_UPLOADED ne sont plus emises
        // (dossiers supprimes), mais restent lues : les lignes d'audit
        // historiques doivent continuer de s'afficher.
        // Avant ce fix, la liste cherchait 'JURIDIQUE_UPLOADED' (jamais ecrit) et
        // 'COMPTABLE_UPLOADED' (jamais ecrit) -> l'activite Data Room restait vide.
        List<OperationLite> derniersOps = jdbc.query("""
                SELECT action, entity_type, created_at
                FROM audit_log
                WHERE workspace_id = ? AND user_id = ?
                  AND action IN ('DOCUMENT_UPLOADED','COMPTABLE_UPLOADED','FISCAL_UPLOADED',
                                 'DOCUMENT_PREVIEWED','DOCUMENTS_DELETED_BULK')
                ORDER BY created_at DESC LIMIT 10
                """, (rs, rn) -> new OperationLite(
                        rs.getString(1), rs.getString(2),
                        rs.getTimestamp(3).toInstant()),
                workspaceId, userId);

        return new EmployeDashboardDto(
                mesTickets == null ? 0 : mesTickets,
                derniers, charge, mesEcheances, derniersOps,
                Instant.now());
    }
}
