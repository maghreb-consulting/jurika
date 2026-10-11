package ma.jurika.ticket.infrastructure.persistence;

import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.RepriseTicket;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lot L4a (E4) : annulation et reprise, lues et ecrites sur le ticket (filtre workspace + RLS). */
@Component
public class RepriseTicketJdbc implements RepriseTicket {

    private final JdbcTemplate jdbc;

    public RepriseTicketJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enregistrerAnnulation(UUID workspaceId, UUID ticketId, TicketStatut statutAnterieur, UUID auteur) {
        jdbc.update("UPDATE tickets SET statut_anterieur = ?, annule_par_id = ? WHERE id = ? AND workspace_id = ?",
                statutAnterieur.name(), auteur, ticketId, workspaceId);
    }

    @Override
    public Optional<TicketStatut> statutAnterieur(UUID workspaceId, UUID ticketId) {
        List<String> colonne = jdbc.queryForList(
                "SELECT statut_anterieur FROM tickets WHERE id = ? AND workspace_id = ?",
                String.class, ticketId, workspaceId);
        String s = colonne.isEmpty() ? null : colonne.get(0);
        if (s == null) {
            List<String> trace = jdbc.queryForList("""
                    SELECT metadata ->> 'from' FROM ticket_comments
                    WHERE ticket_id = ? AND workspace_id = ? AND type = 'ANNULATION'
                    ORDER BY created_at DESC LIMIT 1
                    """, String.class, ticketId, workspaceId);
            s = trace.isEmpty() ? null : trace.get(0);
        }
        if (s == null || s.isBlank()) return Optional.empty();
        try {
            TicketStatut t = TicketStatut.valueOf(s);
            return t == TicketStatut.ANNULE ? Optional.empty() : Optional.of(t);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean retraitEffectue(UUID workspaceId, UUID ticketId) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM ticket_demarches td
                JOIN demarches_referentiel r ON r.id = td.demarche_id
                WHERE td.ticket_id = ? AND td.workspace_id = ?
                  AND r.formalite_volet = 'RETRAIT' AND td.etat = 'COCHEE'
                """, Integer.class, ticketId, workspaceId);
        return n != null && n > 0;
    }

    @Override
    public void effacer(UUID workspaceId, UUID ticketId) {
        jdbc.update("UPDATE tickets SET statut_anterieur = NULL WHERE id = ? AND workspace_id = ?",
                ticketId, workspaceId);
    }
}
