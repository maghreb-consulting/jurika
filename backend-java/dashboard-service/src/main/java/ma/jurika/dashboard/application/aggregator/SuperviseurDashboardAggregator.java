package ma.jurika.dashboard.application.aggregator;

import ma.jurika.dashboard.api.dto.DashboardDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Sprint 10 -- aggregator SUPERVISEUR (1 workspace).
 * Requetes alignees sur le schema reel post-audit Sprint 10 TASK 0 :
 *   - tickets.statut IN ('NOUVEAU','EN_COURS','CLOTURE','ANNULE')
 *   - tickets.cloture_at (PAS closed_at)
 *   - dataroom_alertes_echeances.date_echeance + statut IN ('PLANIFIEE','ENVOYEE')
 */
@Component
public class SuperviseurDashboardAggregator {

    private final JdbcTemplate jdbc;

    public SuperviseurDashboardAggregator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SuperviseurDashboardDto aggregate(UUID workspaceId) {
        // Tickets ouverts vs clos 30j
        Long ticketsOuverts = jdbc.queryForObject("""
                SELECT COUNT(*) FROM tickets
                WHERE workspace_id = ? AND statut IN ('NOUVEAU','EN_COURS')
                """, Long.class, workspaceId);
        Long ticketsClos30d = jdbc.queryForObject("""
                SELECT COUNT(*) FROM tickets
                WHERE workspace_id = ? AND statut = 'CLOTURE'
                  AND cloture_at >= NOW() - INTERVAL '30 days'
                """, Long.class, workspaceId);

        // Temps moyen cloture (heures)
        Double moyenneH = jdbc.queryForObject("""
                SELECT COALESCE(AVG(EXTRACT(EPOCH FROM (cloture_at - created_at))/3600), 0)
                FROM tickets
                WHERE workspace_id = ? AND statut='CLOTURE'
                  AND cloture_at >= NOW() - INTERVAL '30 days'
                """, Double.class, workspaceId);

        // Evolution 30j (group by jour)
        List<DailyCount> evolution = jdbc.query("""
                SELECT date_trunc('day', created_at)::date AS d, COUNT(*)
                FROM tickets
                WHERE workspace_id = ? AND created_at >= NOW() - INTERVAL '30 days'
                GROUP BY 1 ORDER BY 1
                """, (rs, rn) -> new DailyCount(rs.getDate(1).toLocalDate(), rs.getLong(2)),
                workspaceId);

        // Top workflows (par type)
        List<CategoryCount> topWorkflows = jdbc.query("""
                SELECT type, COUNT(*) AS n
                FROM tickets WHERE workspace_id = ?
                GROUP BY type ORDER BY n DESC LIMIT 5
                """, (rs, rn) -> new CategoryCount(rs.getString(1), rs.getLong(2)),
                workspaceId);

        // Charge par employe
        List<EmployeeLoad> charge = jdbc.query("""
                SELECT t.assigne_id, COALESCE(u.email, t.assigne_id::text) AS email,
                       COUNT(*) FILTER (WHERE t.statut IN ('NOUVEAU','EN_COURS')) AS tickets_ouverts,
                       0 AS echeances_assignees
                FROM tickets t
                LEFT JOIN users u ON u.id = t.assigne_id
                WHERE t.workspace_id = ? AND t.assigne_id IS NOT NULL
                GROUP BY t.assigne_id, u.email
                ORDER BY tickets_ouverts DESC LIMIT 10
                """, (rs, rn) -> new EmployeeLoad(
                        (UUID) rs.getObject(1), rs.getString(2), rs.getLong(3), rs.getLong(4)),
                workspaceId);

        // Echeances J-30 / J-15 / J-3
        List<EcheanceItem> j30 = listEcheances(workspaceId, 0, 30);
        List<EcheanceItem> j15 = listEcheances(workspaceId, 0, 15);
        List<EcheanceItem> j3  = listEcheances(workspaceId, 0, 3);
        List<EcheanceBucket> buckets = List.of(
                new EcheanceBucket("J-30", j30),
                new EcheanceBucket("J-15", j15),
                new EcheanceBucket("J-3",  j3));

        // Dossiers a risque (echeances depassees OU exercice VERROUILLE non clos)
        List<DossierRisk> risques = jdbc.query("""
                SELECT DISTINCT d.id, d.raison_sociale,
                       'Echeance depassee' AS motif
                FROM dataroom_alertes_echeances a
                JOIN entreprise_dossiers d ON d.id = a.dossier_id
                WHERE a.workspace_id = ? AND a.statut IN ('PLANIFIEE','ENVOYEE')
                  AND a.date_echeance < CURRENT_DATE
                UNION
                SELECT d.id, d.raison_sociale,
                       'Exercice VERROUILLE (controle fiscal)' AS motif
                FROM dataroom_exercices_fiscaux ex
                JOIN entreprise_dossiers d ON d.id = ex.dossier_id
                WHERE ex.workspace_id = ? AND ex.statut = 'VERROUILLE'
                LIMIT 20
                """, (rs, rn) -> new DossierRisk(
                        (UUID) rs.getObject(1), rs.getString(2), rs.getString(3)),
                workspaceId, workspaceId);

        // Tickets par statut (workspace entier) -- pour donut de repartition
        List<CategoryCount> ticketsParStatut = jdbc.query("""
                SELECT statut, COUNT(*) AS n
                FROM tickets WHERE workspace_id = ?
                GROUP BY statut ORDER BY n DESC
                """, (rs, rn) -> new CategoryCount(rs.getString(1), rs.getLong(2)),
                workspaceId);

        // Evolution mensuelle (6 derniers mois) : tickets crees vs clotures
        List<MonthlyFlow> evolutionMensuelle = jdbc.query("""
                SELECT to_char(m.mois, 'YYYY-MM') AS mois,
                       COALESCE(c.crees, 0)     AS crees,
                       COALESCE(cl.clotures, 0) AS clotures
                FROM generate_series(
                        date_trunc('month', CURRENT_DATE) - INTERVAL '5 month',
                        date_trunc('month', CURRENT_DATE),
                        INTERVAL '1 month') AS m(mois)
                LEFT JOIN (
                    SELECT date_trunc('month', created_at) AS mois, COUNT(*) AS crees
                    FROM tickets
                    WHERE workspace_id = ?
                      AND created_at >= date_trunc('month', CURRENT_DATE) - INTERVAL '5 month'
                    GROUP BY 1
                ) c ON c.mois = m.mois
                LEFT JOIN (
                    SELECT date_trunc('month', cloture_at) AS mois, COUNT(*) AS clotures
                    FROM tickets
                    WHERE workspace_id = ? AND statut = 'CLOTURE'
                      AND cloture_at >= date_trunc('month', CURRENT_DATE) - INTERVAL '5 month'
                    GROUP BY 1
                ) cl ON cl.mois = m.mois
                ORDER BY m.mois
                """, (rs, rn) -> new MonthlyFlow(rs.getString(1), rs.getLong(2), rs.getLong(3)),
                workspaceId, workspaceId);

        // Demandes clients par statut (Non traitee / En cours / Traitee)
        List<CategoryCount> demandesParStatut = jdbc.query("""
                SELECT statut, COUNT(*) AS n
                FROM dataroom_demandes_client WHERE workspace_id = ?
                GROUP BY statut ORDER BY n DESC
                """, (rs, rn) -> new CategoryCount(rs.getString(1), rs.getLong(2)),
                workspaceId);

        return new SuperviseurDashboardDto(
                ticketsOuverts == null ? 0 : ticketsOuverts,
                ticketsClos30d == null ? 0 : ticketsClos30d,
                moyenneH == null ? 0d : moyenneH,
                evolution, topWorkflows, charge, buckets, risques,
                ticketsParStatut, evolutionMensuelle, demandesParStatut,
                Instant.now());
    }

    private List<EcheanceItem> listEcheances(UUID ws, int fromDays, int toDays) {
        return jdbc.query("""
                SELECT id, dossier_id, type_echeance, date_echeance, statut
                FROM dataroom_alertes_echeances
                WHERE workspace_id = ?
                  AND statut IN ('PLANIFIEE','ENVOYEE')
                  AND date_echeance BETWEEN CURRENT_DATE + (? || ' day')::interval
                                        AND CURRENT_DATE + (? || ' day')::interval
                ORDER BY date_echeance ASC LIMIT 30
                """,
                (rs, rn) -> new EcheanceItem(
                        (UUID) rs.getObject(1), (UUID) rs.getObject(2),
                        rs.getString(3),
                        rs.getDate(4) == null ? null : rs.getDate(4).toLocalDate(),
                        rs.getString(5)),
                ws, fromDays, toDays);
    }
}
