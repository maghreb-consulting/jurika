package ma.jurika.billing.application.abonnement;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lot L7 (E8, billing V15) : journal de la sequence d'une echeance impayee (article 4.4 du
 * contrat) : rappel envoye ou en echec, suspension faite, differee faute de rappel ou en echec,
 * retablissement au paiement. Ecrit une fois, jamais modifie (declencheur) ; un envoi rate se lit
 * ici et sur l'ecran du super-admin (motif 9 : rien n'est avale).
 *
 * <p>Isolation : RLS par workspace et filtrage explicite ; la tache quotidienne et le super-admin
 * posent {@code app.facturation_portee = 'globale'} dans leur transaction.
 */
@Component
public class ImpayeJournal {

    public static final String RAPPEL_ENVOYE = "RAPPEL_ENVOYE";
    public static final String RAPPEL_ECHEC = "RAPPEL_ECHEC";
    public static final String SUSPENSION = "SUSPENSION";
    public static final String SUSPENSION_ECHEC = "SUSPENSION_ECHEC";
    public static final String SUSPENSION_SANS_RAPPEL = "SUSPENSION_SANS_RAPPEL";
    public static final String RETABLISSEMENT = "RETABLISSEMENT";

    public record Evenement(Long id, UUID workspaceId, Long abonnementId, LocalDate echeance, String evenement,
                            String destinataire, String detail, Instant creeLe) {}

    private final JdbcTemplate jdbc;

    public ImpayeJournal(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Lecture ou ecriture transverse (tous cabinets), locale a la transaction courante. */
    public void porteeGlobale() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Portee globale de la facturation demandee hors transaction");
        }
        jdbc.queryForObject("SELECT set_config('app.facturation_portee', 'globale', true)", String.class);
    }

    public void ecrire(UUID workspaceId, Long abonnementId, LocalDate echeance, String evenement,
                       String destinataire, String detail) {
        jdbc.update("INSERT INTO impaye_journal (workspace_id, abonnement_id, echeance, evenement, destinataire, detail) "
                + "VALUES (?, ?, ?, ?, ?, ?)", workspaceId, abonnementId, echeance, evenement, destinataire, detail);
    }

    /** Un fait de ce type est-il deja journalise pour cette echeance de cet abonnement ? */
    public boolean existe(UUID workspaceId, Long abonnementId, LocalDate echeance, String evenement) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM impaye_journal WHERE workspace_id = ? AND abonnement_id = ? "
                + "AND echeance = ? AND evenement = ?", Integer.class, workspaceId, abonnementId, echeance, evenement);
        return n != null && n > 0;
    }

    /** Derniers faits d'un cabinet, du plus recent au plus ancien. */
    public List<Evenement> duCabinet(UUID workspaceId, int limite) {
        return jdbc.query("SELECT id, workspace_id, abonnement_id, echeance, evenement, destinataire, detail, cree_le "
                        + "FROM impaye_journal WHERE workspace_id = ? ORDER BY cree_le DESC, id DESC LIMIT ?",
                (rs, i) -> new Evenement(rs.getLong(1), rs.getObject(2, UUID.class), rs.getLong(3),
                        rs.getObject(4, LocalDate.class), rs.getString(5), rs.getString(6), rs.getString(7),
                        rs.getTimestamp(8).toInstant()),
                workspaceId, limite);
    }
}
