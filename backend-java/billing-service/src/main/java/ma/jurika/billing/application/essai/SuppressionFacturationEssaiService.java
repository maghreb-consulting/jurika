package ma.jurika.billing.application.essai;

import ma.jurika.billing.application.essai.ClassementFacturationEssai.Sort;
import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.trial.TrialAccessChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lot L7 (E14), RG-ESS-10 : donnees de facturation d'un essai supprime definitivement.
 *
 * <p>Appele par auth-service AVANT la suppression des lignes de jurika_db : un refus ou une
 * erreur ici arrete la suppression. Decision du 2026-10-14 : paiements non aboutis supprimes
 * (et leurs retours du CMI) ; paiements aboutis et factures conserves 10 ans, inscrits au registre
 * {@code conservation_comptable}, puis purges a l'echeance par {@link #purgerEcheancesPassees()}.
 * Preuves du contrat et journal des impayes : conserves, duree A_DECIDER.
 *
 * <p>Garde-fous de ce service : le cabinet doit etre un essai en lecture seule (etat lu aupres
 * d'auth-service, comme dataroom) ; ceux de la base (fonction {@code essai_facturation_supprimer}) :
 * aucun abonnement actif, aucun paiement acquis dont la facture ou l'activation reste a traiter.
 */
@Service
public class SuppressionFacturationEssaiService {

    private static final Logger log = LoggerFactory.getLogger(SuppressionFacturationEssaiService.class);

    /** Duree de conservation des preuves du contrat et du journal des impayes d'un essai. */
    public static final String DUREE_PREUVES_CONTRAT = "A_DECIDER";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectProvider<TrialAccessChecker> checker;

    @Autowired
    public SuppressionFacturationEssaiService(JdbcTemplate jdbc, PlatformTransactionManager transactions,
                                              ObjectProvider<TrialAccessChecker> checker) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactions);
        this.checker = checker;
    }

    /** Ce qui a ete supprime et conserve (nombre de lignes par table). */
    public record Bilan(UUID workspaceId, Map<String, Long> supprimees, Map<String, Long> conservees10Ans,
                        Map<String, Long> conserveesDureeADecider, LocalDate echeanceConservation,
                        String dureePreuvesContrat) {}

    /** Bilan d'un passage de la purge planifiee. */
    public record BilanPurge(int traites, int reussis, int echecs, List<String> details) {}

    public Bilan supprimer(UUID workspaceId) {
        if (workspaceId == null) throw new IllegalArgumentException("Cabinet absent.");
        TrialAccessChecker c = checker.getIfAvailable();
        if (c == null) {
            throw new IllegalStateException("Etat d'essai indisponible dans billing-service : suppression refusee");
        }
        boolean lectureSeule = c.getState(workspaceId).map(TrialAccessChecker.TrialState::isExpired).orElse(false);
        if (!lectureSeule) {
            throw new BusinessException("ESSAI_SUPPRESSION_REFUSEE",
                    "Suppression refus\u00e9e : ce cabinet n\u2019est pas un essai gratuit en lecture seule.");
        }
        Bilan b;
        try {
            b = transaction.execute(st -> executer(workspaceId));
        } catch (DataAccessException e) {
            throw traduire(e);
        }
        log.warn("Essai {} : facturation supprimee {} ; conservee 10 ans {} (echeance {}) ; conservee, duree A_DECIDER {}",
                workspaceId, b.supprimees(), b.conservees10Ans(), b.echeanceConservation(), b.conserveesDureeADecider());
        return b;
    }

    private Bilan executer(UUID ws) {
        Map<Sort, Map<String, Long>> parSort = new EnumMap<>(Sort.class);
        for (Sort s : Sort.values()) parSort.put(s, new LinkedHashMap<>());
        Set<String> vues = new HashSet<>();
        jdbc.query("SELECT table_nom, sort, lignes FROM essai_facturation_supprimer(?)", rs -> {
            String table = rs.getString(1);
            Sort sort = Sort.valueOf(rs.getString(2));
            ClassementFacturationEssai.Regle regle = ClassementFacturationEssai.TABLES.get(table);
            if (regle == null || !regle.sorts().contains(sort)) {
                // Fonction et classement divergents : la transaction est annulee (rien n'est supprime).
                throw new IllegalStateException("Table de facturation non classee pour la suppression d'un essai : "
                        + table + " (" + sort + ")");
            }
            vues.add(table);
            parSort.get(sort).merge(table, rs.getLong(3), Long::sum);
        }, ws);
        if (!vues.equals(ClassementFacturationEssai.TABLES.keySet())) {
            Set<String> manquantes = new HashSet<>(ClassementFacturationEssai.TABLES.keySet());
            manquantes.removeAll(vues);
            throw new IllegalStateException("Tables classees absentes du bilan de la suppression : " + manquantes);
        }
        List<Date> echeance = jdbc.queryForList(
                "SELECT echeance FROM conservation_comptable WHERE workspace_id = ?", Date.class, ws);
        return new Bilan(ws, parSort.get(Sort.SUPPRIMEE), parSort.get(Sort.CONSERVEE_10_ANS),
                parSort.get(Sort.CONSERVEE_DUREE_A_DECIDER),
                echeance.isEmpty() ? null : echeance.get(0).toLocalDate(), DUREE_PREUVES_CONTRAT);
    }

    /** Purge d'un cabinet dont l'echeance est passee (la fonction refuse sinon). */
    public Map<String, Long> purger(UUID workspaceId) {
        Map<String, Long> lignes = new LinkedHashMap<>();
        try {
            transaction.executeWithoutResult(st -> jdbc.query(
                    "SELECT table_nom, lignes FROM conservation_comptable_purger(?)",
                    rs -> { lignes.put(rs.getString(1), rs.getLong(2)); }, workspaceId));
        } catch (DataAccessException e) {
            throw traduire(e);
        }
        log.warn("Conservation comptable du cabinet {} purgee a l'echeance : {}", workspaceId, lignes);
        return lignes;
    }

    /** Tache planifiee : purge de chaque cabinet dont l'echeance de conservation est passee. */
    public BilanPurge purgerEcheancesPassees() {
        List<UUID> dus = jdbc.queryForList("""
                SELECT workspace_id FROM conservation_comptable
                 WHERE purge_le IS NULL AND echeance < (now() AT TIME ZONE 'Africa/Casablanca')::date
                 ORDER BY echeance, workspace_id
                """, UUID.class);
        int reussis = 0;
        List<String> details = new ArrayList<>();
        for (UUID ws : dus) {
            try {
                purger(ws);
                reussis++;
            } catch (RuntimeException e) {
                log.error("Purge de la conservation comptable du cabinet {} en echec : {}", ws, e.getMessage());
                details.add(ws + " : " + e.getMessage());
            }
        }
        return new BilanPurge(dus.size(), reussis, dus.size() - reussis, details);
    }

    private static RuntimeException traduire(DataAccessException e) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
        String etat = cause instanceof SQLException sql ? sql.getSQLState() : null;
        if (etat == null) return e;
        return switch (etat) {
            case "JE001" -> new BusinessException("ESSAI_ABONNEMENT_ACTIF",
                    "Suppression refus\u00e9e : le cabinet a un abonnement actif.");
            case "JE002" -> new BusinessException("ESSAI_PAIEMENT_A_TRAITER",
                    "Suppression refus\u00e9e : un paiement acquis attend encore sa facture ou l\u2019activation du cabinet.");
            case "JE010" -> new BusinessException("CONSERVATION_ABSENTE",
                    "Aucune pi\u00e8ce comptable conserv\u00e9e pour ce cabinet.");
            case "JE011" -> new BusinessException("CONSERVATION_DEJA_PURGEE",
                    "Les pi\u00e8ces comptables de ce cabinet ont d\u00e9j\u00e0 \u00e9t\u00e9 purg\u00e9es.");
            case "JE012" -> new BusinessException("CONSERVATION_NON_ECHUE",
                    "Purge refus\u00e9e : la dur\u00e9e de conservation de 10 ans n\u2019est pas \u00e9coul\u00e9e.");
            default -> e;
        };
    }
}
