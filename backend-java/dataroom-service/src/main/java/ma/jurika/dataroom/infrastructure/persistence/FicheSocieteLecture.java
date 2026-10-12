package ma.jurika.dataroom.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lot L5 (E7) : lectures de la fiche client et de la fiche dossier, dans les tables de
 * ticket-service (base partagee jurika_db) : tickets du dossier, trace des identifiants
 * (ticket V41), versions de la taxe professionnelle (ticket V30). Filtre workspace
 * explicite en plus de la RLS.
 */
@Repository
public class FicheSocieteLecture {

    public record TicketLigne(UUID id, String reference, String type, String statut, Instant creeLe,
                              Instant clotureLe, Instant annuleLe, UUID assigneId) {}

    public record TraceLigne(String champ, String ancienneValeur, String nouvelleValeur, String origine,
                             UUID ticketId, UUID auteurId, Instant date) {}

    public record VersionTpLigne(String numero, LocalDate dateEffet, UUID saisiPar, Instant saisiLe) {}

    @PersistenceContext
    private EntityManager em;

    private static final String COLONNES_TICKET =
            "id, reference, type, statut, created_at, cloture_at, annule_at, assigne_id";

    /** Tous les tickets du dossier, annules compris, du plus ancien au plus recent. */
    @SuppressWarnings("unchecked")
    public List<TicketLigne> tickets(UUID workspaceId, UUID dossierId) {
        List<Object[]> rows = em.createNativeQuery("SELECT " + COLONNES_TICKET
                        + " FROM tickets WHERE workspace_id = ?1 AND dossier_id = ?2 ORDER BY created_at, reference")
                .setParameter(1, workspaceId).setParameter(2, dossierId).getResultList();
        return rows.stream().map(FicheSocieteLecture::ticket).toList();
    }

    /** Tickets par identifiant (references citees par la trace). */
    @SuppressWarnings("unchecked")
    public Map<UUID, TicketLigne> ticketsParId(UUID workspaceId, Collection<UUID> ids) {
        Map<UUID, TicketLigne> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) return out;
        List<Object[]> rows = em.createNativeQuery("SELECT " + COLONNES_TICKET
                        + " FROM tickets WHERE workspace_id = ?1 AND id IN (?2)")
                .setParameter(1, workspaceId).setParameter(2, List.copyOf(ids)).getResultList();
        for (Object[] r : rows) {
            TicketLigne t = ticket(r);
            out.put(t.id(), t);
        }
        return out;
    }

    /** Trace des identifiants du dossier, du plus ancien au plus recent. */
    @SuppressWarnings("unchecked")
    public List<TraceLigne> traces(UUID workspaceId, UUID dossierId) {
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT champ, ancienne_valeur, nouvelle_valeur, origine, ticket_id, auteur_id, modifie_le
                          FROM dossier_identifiants_historique
                         WHERE workspace_id = ?1 AND dossier_id = ?2
                         ORDER BY modifie_le, id
                        """)
                .setParameter(1, workspaceId).setParameter(2, dossierId).getResultList();
        List<TraceLigne> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            out.add(new TraceLigne((String) r[0], (String) r[1], (String) r[2], (String) r[3],
                    uuid(r[4]), uuid(r[5]), instant(r[6])));
        }
        return out;
    }

    /** Versions de la taxe professionnelle, de la plus ancienne a la plus recente (en vigueur). */
    @SuppressWarnings("unchecked")
    public List<VersionTpLigne> versionsTp(UUID workspaceId, UUID dossierId) {
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT numero, date_effet, saisi_par, saisi_le FROM dossier_tp_versions
                         WHERE workspace_id = ?1 AND dossier_id = ?2
                         ORDER BY saisi_le, id
                        """)
                .setParameter(1, workspaceId).setParameter(2, dossierId).getResultList();
        List<VersionTpLigne> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            out.add(new VersionTpLigne((String) r[0], date(r[1]), uuid(r[2]), instant(r[3])));
        }
        return out;
    }

    /** Type du ticket qui a ouvert le dossier (CREATION, IMPORT...), ou {@code null}. */
    @SuppressWarnings("unchecked")
    public String typeDuTicketOrigine(UUID workspaceId, UUID dossierId) {
        List<Object> rows = em.createNativeQuery("""
                        SELECT t.type FROM entreprise_dossiers d
                          JOIN tickets t ON t.id = d.created_by_ticket_id AND t.workspace_id = d.workspace_id
                         WHERE d.workspace_id = ?1 AND d.id = ?2
                        """)
                .setParameter(1, workspaceId).setParameter(2, dossierId).getResultList();
        return rows.isEmpty() ? null : (String) rows.get(0);
    }

    private static TicketLigne ticket(Object[] r) {
        return new TicketLigne(uuid(r[0]), (String) r[1], (String) r[2], (String) r[3],
                instant(r[4]), instant(r[5]), instant(r[6]), uuid(r[7]));
    }

    private static UUID uuid(Object o) {
        return o == null ? null : (o instanceof UUID u ? u : UUID.fromString(o.toString()));
    }

    private static LocalDate date(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDate d) return d;
        if (o instanceof Date d) return d.toLocalDate();
        return LocalDate.parse(o.toString());
    }

    private static Instant instant(Object o) {
        if (o == null) return null;
        if (o instanceof Instant i) return i;
        if (o instanceof Timestamp t) return t.toInstant();
        if (o instanceof OffsetDateTime d) return d.toInstant();
        throw new IllegalStateException("Date inattendue : " + o.getClass());
    }
}
