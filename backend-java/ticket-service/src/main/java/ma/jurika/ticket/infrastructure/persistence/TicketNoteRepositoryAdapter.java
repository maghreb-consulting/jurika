package ma.jurika.ticket.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.ticket.domain.model.TicketNote;
import ma.jurika.ticket.domain.port.TicketNoteRepository;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Lot L1 : note de ticket (V29), filtre workspace explicite en plus de la RLS. */
@Repository
public class TicketNoteRepositoryAdapter implements TicketNoteRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<TicketNote> find(UUID workspaceId, UUID ticketId) {
        return em.createNativeQuery("""
                SELECT contenu, modifie_par, modifie_le FROM ticket_notes
                 WHERE workspace_id = ?1 AND ticket_id = ?2
                """)
                .setParameter(1, workspaceId)
                .setParameter(2, ticketId)
                .getResultStream()
                .findFirst()
                .map(r -> {
                    Object[] l = (Object[]) r;
                    return new TicketNote(ticketId, (String) l[0],
                            l[1] instanceof UUID u ? u : UUID.fromString(l[1].toString()), instant(l[2]));
                });
    }

    @Override
    public TicketNote save(UUID workspaceId, UUID ticketId, String contenu, UUID auteurId) {
        Instant maintenant = Instant.now();
        em.createNativeQuery("""
                INSERT INTO ticket_notes (ticket_id, workspace_id, contenu, modifie_par, modifie_le)
                VALUES (?1, ?2, ?3, ?4, ?5)
                ON CONFLICT (ticket_id) DO UPDATE
                   SET contenu = EXCLUDED.contenu, modifie_par = EXCLUDED.modifie_par,
                       modifie_le = EXCLUDED.modifie_le
                 WHERE ticket_notes.workspace_id = EXCLUDED.workspace_id
                """)
                .setParameter(1, ticketId)
                .setParameter(2, workspaceId)
                .setParameter(3, contenu)
                .setParameter(4, auteurId)
                .setParameter(5, Timestamp.from(maintenant))
                .executeUpdate();
        return new TicketNote(ticketId, contenu, auteurId, maintenant);
    }

    private static Instant instant(Object o) {
        if (o instanceof Timestamp t) return t.toInstant();
        if (o instanceof Instant i) return i;
        if (o instanceof java.time.OffsetDateTime d) return d.toInstant();
        return o == null ? null : Instant.parse(o.toString());
    }
}
