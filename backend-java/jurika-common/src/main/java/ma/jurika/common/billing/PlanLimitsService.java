package ma.jurika.common.billing;

import ma.jurika.common.trial.EssaiRegles;
import ma.jurika.common.billing.PlanCatalog.Plan;
import ma.jurika.common.billing.PlanCatalog.Quotas;
import ma.jurika.common.exception.FonctionHorsForfaitException;
import ma.jurika.common.exception.PlanLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Quotas et fonctions du forfait d'un cabinet, verifies par le serveur (P6).
 *
 * <p><b>Lot L7 (E5)</b>, CDC 4.7 :
 * <ul>
 *   <li>RG-PLN-01 : sont comptes les dossiers OUVERTS AU COURS DU MOIS D'ABONNEMENT : un dossier
 *       compte des qu'un ticket y est ouvert dans le mois, une seule fois quel que soit le nombre
 *       de tickets (l'ancien compteur comptait tous les dossiers du cabinet). Mois d'abonnement :
 *       {@link PeriodeAbonnement}, ancre sur {@code workspaces.abonnement_debut} (auth V44).</li>
 *   <li>RG-PLN-03 : a 100 % du quota de dossiers, l'ouverture d'un dossier de plus dans le mois est
 *       bloquee ({@link #enforceDossierLimit}) ; les dossiers deja comptes restent utilisables.</li>
 *   <li>RG-PLN-04 : a 100 % du stockage, l'ajout de documents est bloque
 *       ({@link #enforceStorageLimit}) ; consultation et telechargement restent permis (rien n'est
 *       controle a la lecture).</li>
 *   <li>RG-PLN-08 : les clients ne sont pas comptes ; le superviseur non plus (1 par cabinet) :
 *       le quota d'utilisateurs porte sur les acces EMPLOYES.</li>
 *   <li>CDC 4.5 : {@link #exigerFonction} refuse une fonction hors forfait.</li>
 * </ul>
 *
 * <p>Forfait absent ({@code selected_plan} NULL, workspace anterieur aux forfaits) : les quotas du
 * forfait Essentiel (le plus restrictif) s'appliquent, et l'instantane le dit
 * ({@code forfaitDefini = false}) au lieu de le masquer. Pendant l'essai ({@code trial_status =
 * TRIAL_ACTIVE}), les fonctions sont celles du forfait Professionnel (RG-ESS-07).
 */
@Service
@ConditionalOnProperty(name = "jurika.plan-limits.enabled", havingValue = "true", matchIfMissing = true)
public class PlanLimitsService {

    private static final Logger log = LoggerFactory.getLogger(PlanLimitsService.class);
    private static final long OCTETS_PAR_GO = 1024L * 1024L * 1024L;

    private final JdbcTemplate jdbc;
    private final boolean enforcementEnabled;
    private final Clock horloge;

    @org.springframework.beans.factory.annotation.Autowired
    public PlanLimitsService(JdbcTemplate jdbc,
                             @Value("${jurika.plan-limits.enforcement-enabled:true}") boolean enforcementEnabled) {
        this(jdbc, enforcementEnabled, Clock.systemUTC());
    }

    PlanLimitsService(JdbcTemplate jdbc, boolean enforcementEnabled, Clock horloge) {
        this.jdbc = jdbc;
        this.enforcementEnabled = enforcementEnabled;
        this.horloge = horloge;
    }

    /**
     * Consommation du forfait. {@code dossiers} = dossiers comptes dans le mois d'abonnement
     * [{@code periodeDebut}, {@code periodeFin}). {@code storageMesure = false} si les tables de la
     * Data Room sont absentes de la base lue (le stockage n'est alors pas mesure, et le dit).
     */
    public record UsageSnapshot(
            String planCode,
            String planLabel,
            int users, int maxUsers, boolean usersUnlimited,
            int dossiers, int maxDossiers, boolean dossiersUnlimited,
            long storageBytes, int maxStorageGb, boolean storageUnlimited,
            boolean forfaitDefini,
            LocalDate periodeDebut,
            LocalDate periodeFin,
            boolean storageMesure,
            // Recette R1 (RG-ESS-07, RG-ESS-04) : cabinet en essai sans forfait souscrit ; le forfait
            // affiche est alors l'essai, avec ses limites. Stockage en octets (500 Mo pendant l'essai).
            boolean essai,
            long maxStorageOctets
    ) {
        public boolean usersOver()    { return !usersUnlimited && users >= maxUsers; }
        public boolean dossiersOver() { return !dossiersUnlimited && dossiers >= maxDossiers; }
        public boolean storageOver()  { return !storageUnlimited && storageBytes >= maxStorageOctets; }
    }

    /** Forfait du cabinet tel que lu en base. */
    record Contexte(Plan plan, boolean forfaitDefini, LocalDate ancre, boolean essaiEnCours,
                    boolean essaiSansForfait) {}

    // ------------------------------------------------------------------ lecture

    public UsageSnapshot snapshot(UUID workspaceId) {
        Contexte c = contexte(workspaceId);
        Quotas q = c.plan().quotas();
        PeriodeAbonnement periode = PeriodeAbonnement.courante(c.ancre(), horloge.instant());
        int users = countUsers(workspaceId);
        Long storage = countStorageBytes(workspaceId);
        if (c.essaiSansForfait()) {
            // Recette R1, RG-ESS-07 : le forfait affiche pendant l'essai est l'essai, avec les limites
            // de RG-ESS-04 (dossiers comptes sur tout l'essai, comme EssaiQuotas), jamais Essentiel.
            return new UsageSnapshot(
                    EssaiRegles.CODE_FORFAIT_AFFICHE, EssaiRegles.LIBELLE_FORFAIT,
                    users, EssaiRegles.MAX_EMPLOYES, false,
                    countDossiersDeLEssai(workspaceId), EssaiRegles.MAX_DOSSIERS, false,
                    storage == null ? 0L : storage, 0, false,
                    false, periode.debut(), periode.fin(), storage != null,
                    true, EssaiRegles.MAX_STOCKAGE_OCTETS);
        }
        int dossiers = countDossiersDuMois(workspaceId, periode);
        return new UsageSnapshot(
                c.plan().internalCode(), c.plan().displayLabel(),
                users, q.maxUsers(), q.isUsersUnlimited(),
                dossiers, q.maxDossiers(), q.isDossiersUnlimited(),
                storage == null ? 0L : storage, q.maxStorageGb(), q.isStorageUnlimited(),
                c.forfaitDefini(), periode.debut(), periode.fin(), storage != null,
                false, (long) q.maxStorageGb() * OCTETS_PAR_GO);
    }

    /** Mois d'abonnement courant du cabinet. */
    public PeriodeAbonnement periodeCourante(UUID workspaceId) {
        return PeriodeAbonnement.courante(contexte(workspaceId).ancre(), horloge.instant());
    }

    /** Dossiers comptes dans le mois d'abonnement courant (RG-PLN-01). */
    public int dossiersDuMois(UUID workspaceId) {
        return countDossiersDuMois(workspaceId, periodeCourante(workspaceId));
    }

    /** Le forfait du cabinet (ou de l'essai) donne-t-il acces a la fonction ? */
    public boolean fonctionIncluse(UUID workspaceId, FonctionForfait fonction) {
        Contexte c = contexte(workspaceId);
        if (c.essaiEnCours()) return PlanCatalog.PROFESSIONNEL.inclut(fonction);
        return c.plan().inclut(fonction);
    }

    // ------------------------------------------------------------------ controles

    /** A appeler AVANT d'inviter ou de reactiver un EMPLOYE. */
    public void enforceUserLimit(UUID workspaceId) {
        if (!enforcementEnabled) return;
        Plan plan = contexte(workspaceId).plan();
        Quotas q = plan.quotas();
        if (q.isUsersUnlimited()) return;
        int current = countUsers(workspaceId);
        if (current >= q.maxUsers()) {
            throw new PlanLimitException(
                    "PLAN_LIMIT_USERS",
                    String.format(Locale.FRANCE, "Quota d\u2019acc\u00e8s employ\u00e9s atteint pour le forfait %s : %d sur %d. "
                            + "Changez de forfait pour ajouter un employ\u00e9.",
                            plan.displayLabel(), current, q.maxUsers()),
                    "users", current, q.maxUsers(),
                    plan.internalCode(),
                    upgradeHintFor(plan));
        }
    }

    /**
     * RG-PLN-01 et RG-PLN-03 : a appeler, DANS la transaction qui ouvre le ticket, avant de l'ouvrir.
     * Le dossier deja compte dans le mois passe toujours ; sinon, le quota atteint bloque. Un verrou
     * transactionnel par cabinet serialise les ouvertures concurrentes (deux ouvertures simultanees
     * ne depassent pas le quota).
     *
     * @param dossierId dossier du ticket, ou {@code null} pour un dossier nouveau
     */
    public void enforceDossierLimit(UUID workspaceId, UUID dossierId) {
        if (!enforcementEnabled) return;
        Contexte c = contexte(workspaceId);
        Quotas q = c.plan().quotas();
        if (q.isDossiersUnlimited()) return;
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class,
                "jurika-quota-dossiers:" + workspaceId);
        PeriodeAbonnement periode = PeriodeAbonnement.courante(c.ancre(), horloge.instant());
        if (dossierId != null && dossierDejaCompte(workspaceId, dossierId, periode)) return;
        int current = countDossiersDuMois(workspaceId, periode);
        if (current >= q.maxDossiers()) {
            throw new PlanLimitException(
                    "PLAN_LIMIT_DOSSIERS",
                    String.format(Locale.FRANCE, "Quota de dossiers du mois atteint pour le forfait %s : %d dossiers ouverts sur %d "
                            + "depuis le %s. Les dossiers d\u00e9j\u00e0 ouverts ce mois-ci restent accessibles ; "
                            + "pour en ouvrir un autre, changez de forfait ou attendez le %s.",
                            c.plan().displayLabel(), current, q.maxDossiers(),
                            formatDate(periode.debut()), formatDate(periode.fin())),
                    "dossiers", current, q.maxDossiers(),
                    c.plan().internalCode(),
                    upgradeHintFor(c.plan()));
        }
    }

    /**
     * RG-PLN-04 : a appeler avant d'AJOUTER un document. L'ajout qui ferait depasser le stockage du
     * forfait est refuse ; la consultation et le telechargement ne passent jamais par ici.
     */
    public void enforceStorageLimit(UUID workspaceId, long incomingBytes) {
        if (!enforcementEnabled) return;
        Plan plan = contexte(workspaceId).plan();
        Quotas q = plan.quotas();
        if (q.isStorageUnlimited()) return;
        Long current = countStorageBytes(workspaceId);
        if (current == null) {
            throw new IllegalStateException("Stockage non mesurable : tables de la Data Room absentes de la base");
        }
        long limit = (long) q.maxStorageGb() * OCTETS_PAR_GO;
        if (current + Math.max(0L, incomingBytes) > limit) {
            throw new PlanLimitException(
                    "PLAN_LIMIT_STORAGE",
                    String.format(Locale.FRANCE, "Stockage du forfait %s atteint : %.2f Go utilis\u00e9s sur %d Go. "
                            + "L\u2019ajout de documents est bloqu\u00e9 ; la consultation et le t\u00e9l\u00e9chargement restent possibles. "
                            + "Changez de forfait pour ajouter ce document.",
                            plan.displayLabel(), current / (double) OCTETS_PAR_GO, q.maxStorageGb()),
                    "storage", current, limit,
                    plan.internalCode(),
                    upgradeHintFor(plan));
        }
    }

    /** CDC 4.5 : refuse une fonction que le forfait du cabinet ne donne pas. */
    public void exigerFonction(UUID workspaceId, FonctionForfait fonction) {
        if (fonctionIncluse(workspaceId, fonction)) return;
        throw new FonctionHorsForfaitException(fonction,
                fonction.libelle() + " : fonction non comprise dans votre forfait. Elle est incluse dans le forfait "
                        + premierForfaitAvec(fonction) + " et au-del\u00e0 ; changez de forfait pour y acc\u00e9der.");
    }

    /** Premier forfait (par rang) qui inclut la fonction. */
    public static String premierForfaitAvec(FonctionForfait fonction) {
        return PlanCatalog.ALL.stream().filter(p -> p.inclut(fonction))
                .map(Plan::displayLabel).findFirst().orElse("Sur Devis");
    }

    // ------------------------------------------------------------------ requetes

    Contexte contexte(UUID workspaceId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT selected_plan, abonnement_debut, trial_status FROM workspaces WHERE id = ?", workspaceId);
        if (rows.isEmpty()) {
            throw new IllegalStateException("Workspace introuvable pour le controle du forfait : " + workspaceId);
        }
        Map<String, Object> r = rows.get(0);
        String code = (String) r.get("selected_plan");
        Object debut = r.get("abonnement_debut");
        LocalDate ancre = debut instanceof Date d ? d.toLocalDate()
                : debut instanceof Timestamp t ? t.toLocalDateTime().toLocalDate()
                : debut instanceof LocalDate l ? l : null;
        boolean essai = "TRIAL_ACTIVE".equals(r.get("trial_status"));
        // Essai en cours ou termine (lecture seule) sans forfait souscrit : l'essai est affiche.
        boolean essaiAffiche = essai || "TRIAL_EXPIRED".equals(r.get("trial_status"));
        if (code == null) {
            return new Contexte(PlanCatalog.ESSENTIEL, false, ancre, essai, essaiAffiche);
        }
        Plan plan = PlanCatalog.findByCode(code).orElseThrow(() ->
                new IllegalStateException("Forfait inconnu en base pour le workspace " + workspaceId + " : " + code));
        return new Contexte(plan, true, ancre, essai, false);
    }

    private int countUsers(UUID workspaceId) {
        // Quota = acces EMPLOYES non desactives (PENDING reserve deja le siege). Le superviseur
        // (un par cabinet) et les clients (RG-PLN-08) ne sont pas comptes.
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE workspace_id = ? "
                        + "AND role = 'EMPLOYE' AND status <> 'INACTIVE'",
                Integer.class, workspaceId);
        return n == null ? 0 : n;
    }

    private int countDossiersDuMois(UUID workspaceId, PeriodeAbonnement periode) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT dossier_id) FROM tickets WHERE workspace_id = ? "
                        + "AND dossier_id IS NOT NULL AND created_at >= ? AND created_at < ?",
                Integer.class, workspaceId,
                Timestamp.from(periode.debutInstant()), Timestamp.from(periode.finInstant()));
        return n == null ? 0 : n;
    }

    /** Dossiers du cabinet en essai (RG-ESS-04 : 3 au plus sur tout l'essai) ; 0 si la table est absente. */
    private int countDossiersDeLEssai(UUID workspaceId) {
        Boolean presente = jdbc.queryForObject(
                "SELECT to_regclass('public.entreprise_dossiers') IS NOT NULL", Boolean.class);
        if (!Boolean.TRUE.equals(presente)) return 0;
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM entreprise_dossiers WHERE workspace_id = ?",
                Integer.class, workspaceId);
        return n == null ? 0 : n;
    }

    private boolean dossierDejaCompte(UUID workspaceId, UUID dossierId, PeriodeAbonnement periode) {
        Boolean b = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM tickets WHERE workspace_id = ? AND dossier_id = ? "
                        + "AND created_at >= ? AND created_at < ?)",
                Boolean.class, workspaceId, dossierId,
                Timestamp.from(periode.debutInstant()), Timestamp.from(periode.finInstant()));
        return Boolean.TRUE.equals(b);
    }

    /**
     * Somme des documents juridiques et des depots client. {@code null} si les tables de la Data
     * Room n'existent pas dans la base lue (verifie par to_regclass, sans requete en echec qui
     * avorterait la transaction).
     */
    private Long countStorageBytes(UUID workspaceId) {
        Boolean presentes = jdbc.queryForObject(
                "SELECT to_regclass('public.dataroom_documents') IS NOT NULL "
                        + "AND to_regclass('public.dataroom_depots') IS NOT NULL", Boolean.class);
        if (!Boolean.TRUE.equals(presentes)) {
            log.warn("Stockage non mesure pour le workspace {} : tables de la Data Room absentes", workspaceId);
            return null;
        }
        Long documents = jdbc.queryForObject(
                "SELECT COALESCE(SUM(size_bytes), 0) FROM dataroom_documents WHERE workspace_id = ?",
                Long.class, workspaceId);
        Long depots = jdbc.queryForObject(
                "SELECT COALESCE(SUM(size_bytes), 0) FROM dataroom_depots WHERE workspace_id = ? AND deleted_at IS NULL",
                Long.class, workspaceId);
        return (documents == null ? 0L : documents) + (depots == null ? 0L : depots);
    }

    private static String upgradeHintFor(Plan current) {
        return PlanCatalog.ALL.stream()
                .filter(p -> p.rang() == current.rang() + 1)
                .findFirst()
                .map(p -> p.quoteOnly()
                        ? "Contactez JURIKA pour le forfait Sur Devis (plus de 100 dossiers par mois)."
                        : String.format(Locale.FRANCE, "Passez au forfait %s (%d dossiers par mois, 1 superviseur et %d employ\u00e9s, %d Go).",
                                p.displayLabel(), p.quotas().maxDossiers(), p.quotas().maxUsers(), p.quotas().maxStorageGb()))
                .orElse("Contactez JURIKA.");
    }

    private static String formatDate(LocalDate d) {
        return String.format(Locale.FRANCE, "%02d/%02d/%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }
}
