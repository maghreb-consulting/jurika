package ma.jurika.common.trial;

import ma.jurika.common.exception.PlanLimitException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.UUID;

/**
 * Lot L7 (E4), RG-ESS-04 : limites de l'essai gratuit, verifiees par le
 * serveur (P6) aux points de creation : 3 dossiers, 1 employe (le superviseur
 * est celui de l'inscription), 500 Mo de stockage. Les 20 questions au
 * chatbot sont comptees par ai-service (compteur atomique).
 *
 * <p>Hors essai : aucun effet (les quotas du forfait s'appliquent par
 * ailleurs). Etat de l'essai illisible : l'exception remonte (refus).
 *
 * <p>Les comptages s'executent DANS la transaction de l'appelant (workspace
 * courant pose, RLS) : appel hors transaction refuse, sinon la RLS rendrait
 * zero ligne et la limite ne serait jamais atteinte.
 *
 * <p>Refus : {@link PlanLimitException} (HTTP 402), message qui nomme la
 * limite et invite a souscrire un forfait.
 */
public class EssaiQuotas {

    public static final String CODE_DOSSIERS = "ESSAI_LIMITE_DOSSIERS";
    public static final String CODE_EMPLOYES = "ESSAI_LIMITE_EMPLOYES";
    public static final String CODE_STOCKAGE = "ESSAI_LIMITE_STOCKAGE";
    public static final String CODE_QUESTIONS = "ESSAI_LIMITE_QUESTIONS";
    private static final String CONSEIL = "Souscrivez un forfait pour lever les limites de l\u2019essai : "
            + "vous conservez tout votre travail.";

    private final TrialAccessChecker checker;
    private final JdbcTemplate jdbc;

    public EssaiQuotas(TrialAccessChecker checker, JdbcTemplate jdbc) {
        this.checker = checker;
        this.jdbc = jdbc;
    }

    /** {@code true} si le cabinet est en essai (en cours ou en lecture seule). */
    public boolean enEssai(UUID workspaceId) {
        Optional<TrialAccessChecker.TrialState> etat = checker.getState(workspaceId);
        return etat.map(TrialAccessChecker.TrialState::enEssai).orElse(false);
    }

    /** A appeler AVANT de creer un dossier. */
    public void verifierNouveauDossier(UUID workspaceId) {
        if (!enEssai(workspaceId)) return;
        long n = compter("SELECT COUNT(*) FROM entreprise_dossiers WHERE workspace_id = ?", workspaceId);
        if (n >= EssaiRegles.MAX_DOSSIERS) {
            throw new PlanLimitException(CODE_DOSSIERS,
                    "Limite de l\u2019essai gratuit atteinte : " + EssaiRegles.MAX_DOSSIERS
                            + " dossiers au plus. " + CONSEIL,
                    "dossiers", n, EssaiRegles.MAX_DOSSIERS, EssaiRegles.FORFAIT_ESSAI, CONSEIL);
        }
    }

    /** A appeler AVANT d'inviter (ou de reactiver) un employe. */
    public void verifierNouvelEmploye(UUID workspaceId) {
        if (!enEssai(workspaceId)) return;
        long n = compter("SELECT COUNT(*) FROM users WHERE workspace_id = ? AND role = 'EMPLOYE' "
                + "AND status <> 'INACTIVE'", workspaceId);
        if (n >= EssaiRegles.MAX_EMPLOYES) {
            throw new PlanLimitException(CODE_EMPLOYES,
                    "Limite de l\u2019essai gratuit atteinte : 1 superviseur et " + EssaiRegles.MAX_EMPLOYES
                            + " employ\u00e9 au plus. " + CONSEIL,
                    "users", n, EssaiRegles.MAX_EMPLOYES, EssaiRegles.FORFAIT_ESSAI, CONSEIL);
        }
    }

    /**
     * A appeler AVANT d'enregistrer un fichier de {@code octetsEntrants} octets.
     * Compte tout ce qui est stocke : versions des documents et depots non supprimes.
     */
    public void verifierStockage(UUID workspaceId, long octetsEntrants) {
        if (!enEssai(workspaceId)) return;
        long documents = compter("SELECT COALESCE(SUM(size_bytes), 0) FROM dataroom_documents WHERE workspace_id = ?",
                workspaceId);
        long depots = compter("SELECT COALESCE(SUM(size_bytes), 0) FROM dataroom_depots "
                + "WHERE workspace_id = ? AND deleted_at IS NULL", workspaceId);
        long total = documents + depots;
        if (total + Math.max(0, octetsEntrants) > EssaiRegles.MAX_STOCKAGE_OCTETS) {
            throw new PlanLimitException(CODE_STOCKAGE,
                    "Limite de l\u2019essai gratuit atteinte : 500 Mo de stockage au plus (utilis\u00e9s : "
                            + (total / (1024L * 1024L)) + " Mo). " + CONSEIL,
                    "storage", total, EssaiRegles.MAX_STOCKAGE_OCTETS, EssaiRegles.FORFAIT_ESSAI, CONSEIL);
        }
    }

    /** Refus des questions au chatbot au-dela de la limite (compteur tenu par ai-service). */
    public static PlanLimitException limiteQuestionsAtteinte() {
        return new PlanLimitException(CODE_QUESTIONS,
                "Limite de l\u2019essai gratuit atteinte : " + EssaiRegles.MAX_QUESTIONS_CHATBOT
                        + " questions au chatbot au plus. " + CONSEIL,
                "chatbot", EssaiRegles.MAX_QUESTIONS_CHATBOT, EssaiRegles.MAX_QUESTIONS_CHATBOT,
                EssaiRegles.FORFAIT_ESSAI, CONSEIL);
    }

    private long compter(String sql, UUID workspaceId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Limite de l'essai verifiee hors transaction : la RLS rendrait zero ligne");
        }
        Long n = jdbc.queryForObject(sql, Long.class, workspaceId);
        return n == null ? 0 : n;
    }
}
