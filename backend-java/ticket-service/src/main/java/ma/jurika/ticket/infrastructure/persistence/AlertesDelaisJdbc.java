package ma.jurika.ticket.infrastructure.persistence;

import ma.jurika.ticket.domain.port.AlertesDelaisRegistre;
import ma.jurika.ticket.domain.port.SuperviseursDuCabinet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lot L5 (E9, RG-COM-08) : registre des echeances notifiees (ticket V43) et
 * superviseurs actifs d'un cabinet.
 *
 * <p>Les ecritures et lectures par cabinet passent par une transaction : le
 * gestionnaire de transactions pose le workspace courant pour la RLS, et chaque
 * requete filtre AUSSI explicitement sur {@code workspace_id} (les deux, toujours).
 */
@Repository
public class AlertesDelaisJdbc implements AlertesDelaisRegistre, SuperviseursDuCabinet {

    private final JdbcTemplate jdbc;

    public AlertesDelaisJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketASurveiller> ticketsASurveiller() {
        return jdbc.query("SELECT workspace_id, ticket_id FROM ticket_tickets_pour_alertes_delais()",
                (rs, i) -> new TicketASurveiller(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)));
    }

    @Override
    @Transactional
    public boolean retenirSiNouveau(UUID workspaceId, UUID ticketId, int ordre,
                                    LocalDate echeance, String severite) {
        int n = jdbc.update("""
                INSERT INTO alertes_delais_notifiees (workspace_id, ticket_id, ordre, echeance, severite)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, workspaceId, ticketId, ordre, Date.valueOf(echeance), severite);
        return n == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> actifs(UUID workspaceId) {
        return jdbc.queryForList("""
                SELECT id FROM users
                WHERE workspace_id = ? AND role = 'SUPERVISEUR' AND status = 'ACTIVE'
                ORDER BY created_at
                """, UUID.class, workspaceId);
    }
}
