package ma.jurika.dashboard.application.aggregator;

import ma.jurika.dashboard.api.dto.DashboardDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class ClientDashboardAggregator {

    private final JdbcTemplate jdbc;

    public ClientDashboardAggregator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ClientDashboardDto aggregate(UUID workspaceId, UUID clientUserId) {
        // RG-DASH-09 : un CLIENT ne voit QUE ses propres dossiers (client_id)
        // Le filtrage exact depend de la liaison user->dossier en place.
        // Fallback : on filtre par client_id stocke sur le dossier
        List<DossierClientLite> dossiers = jdbc.query("""
                SELECT d.id, d.raison_sociale, d.statut,
                       COALESCE((SELECT MAX(created_at) FROM dataroom_documents
                                 WHERE dossier_id = d.id), d.created_at) AS last_event_at,
                       'Dernier upload' AS last_event_label
                FROM entreprise_dossiers d
                WHERE d.workspace_id = ? AND d.client_id = ?
                ORDER BY last_event_at DESC LIMIT 20
                """, (rs, rn) -> new DossierClientLite(
                        (UUID) rs.getObject(1), rs.getString(2), rs.getString(3),
                        rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toInstant(),
                        rs.getString(5)),
                workspaceId, clientUserId);

        List<TicketLite> tickets = jdbc.query("""
                SELECT t.id, t.reference, t.titre, t.statut, t.priorite, t.created_at
                FROM tickets t
                JOIN entreprise_dossiers d ON d.id = t.dossier_id
                WHERE t.workspace_id = ? AND d.client_id = ?
                  AND t.statut IN ('CREATION_TICKET','GENERATION_DOCUMENTS','DEROULEMENT_DEMARCHE')
                ORDER BY t.created_at DESC LIMIT 10
                """, (rs, rn) -> new TicketLite(
                        (UUID) rs.getObject(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5),
                        rs.getTimestamp(6).toInstant()),
                workspaceId, clientUserId);

        List<DocumentLite> documents = jdbc.query("""
                SELECT dd.id, dd.title, dd.filename, dd.created_at
                FROM dataroom_documents dd
                JOIN entreprise_dossiers d ON d.id = dd.dossier_id
                WHERE dd.workspace_id = ? AND d.client_id = ?
                  AND dd.is_current = true
                ORDER BY dd.created_at DESC LIMIT 10
                """, (rs, rn) -> new DocumentLite(
                        (UUID) rs.getObject(1), rs.getString(2), rs.getString(3),
                        rs.getTimestamp(4).toInstant()),
                workspaceId, clientUserId);

        // Lot 1 (2026-09-04) -- les echeances viennent desormais des DEMARCHES du
        // parcours (delais legaux du guide) et non plus des alertes fiscales,
        // supprimees avec le dossier fiscal.
        List<EcheanceItem> echeances = jdbc.query(
                DemarcheEcheancesSql.BASE + DemarcheEcheancesSql.SCOPE_CLIENT
                        + DemarcheEcheancesSql.ORDER,
                (rs, rn) -> new EcheanceItem(
                        (UUID) rs.getObject(1), (UUID) rs.getObject(2),
                        rs.getString(3),
                        rs.getDate(4) == null ? null : rs.getDate(4).toLocalDate(),
                        rs.getString(5)),
                workspaceId, clientUserId);

        return new ClientDashboardDto(dossiers, tickets, documents, echeances, Instant.now());
    }
}
