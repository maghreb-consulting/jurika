package ma.jurika.workflow.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Lot L4a (E1c) : les faits du ticket que les actes impriment -- reference, date
 * d'ouverture, employe en charge, annulation. Aucune saisie ne les porte : ils se lisent
 * sur le ticket (meme base, filtre par workspace ; RLS en plus).
 *
 * <p>Avant, rien ne les posait : $DOSSIER_NUMERO, $DOSSIER_DATE_OUVERTURE et $DOSSIER_CHARGE
 * manquaient a sept documents de la creation, refuses en production.
 */
@Component
public class FaitsDuTicket {

    /** Faits lus ; {@code null} pour une donnee absente (jamais une valeur de repli). */
    public record Faits(String reference, String ouvertLe, String chargeNom,
                        String annuleLe, String motifAnnulation) {
    }

    private static final ZoneId CASABLANCA = ZoneId.of("Africa/Casablanca");

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public Faits lire(UUID workspaceId, UUID ticketId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT t.reference, t.created_at, u.first_name, u.last_name, t.annule_at, t.annulation_motif
                FROM tickets t
                LEFT JOIN entreprise_dossiers d ON d.id = t.dossier_id AND d.workspace_id = t.workspace_id
                LEFT JOIN users u ON u.id = COALESCE(d.responsable_id, t.assigne_id, t.cree_par_id)
                                 AND u.workspace_id = t.workspace_id
                WHERE t.id = ?1 AND t.workspace_id = ?2
                """).setParameter(1, ticketId).setParameter(2, workspaceId).getResultList();
        if (rows.isEmpty()) return new Faits(null, null, null, null, null);
        Object[] r = rows.get(0);
        String nom = ((r[2] == null ? "" : r[2]) + " " + (r[3] == null ? "" : r[3])).trim();
        return new Faits(texte(r[0]), date(r[1]), nom.isEmpty() ? null : nom, date(r[4]), texte(r[5]));
    }

    private static String texte(Object o) {
        return o == null || String.valueOf(o).isBlank() ? null : String.valueOf(o);
    }

    /** Date du jour de l'evenement, a l'heure de Casablanca (RG-DEM-06), au format ISO. */
    private static String date(Object o) {
        if (o instanceof java.sql.Timestamp ts) return ts.toInstant().atZone(CASABLANCA).toLocalDate().toString();
        if (o instanceof java.time.Instant i) return i.atZone(CASABLANCA).toLocalDate().toString();
        if (o instanceof java.time.OffsetDateTime odt) return odt.atZoneSameInstant(CASABLANCA).toLocalDate().toString();
        return texte(o);
    }
}
