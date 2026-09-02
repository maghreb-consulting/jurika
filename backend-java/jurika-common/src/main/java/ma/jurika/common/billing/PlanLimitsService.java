package ma.jurika.common.billing;

import ma.jurika.common.billing.PlanCatalog.Plan;
import ma.jurika.common.billing.PlanCatalog.Quotas;
import ma.jurika.common.exception.PlanLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Sprint Beta (pricing-deploy) — TASK 3.
 *
 * <p>Enforce les quotas du plan tarifaire actif d'un workspace
 * (RG-SAAS-06 + RG-BL-QUOTA). Lit {@code workspaces.selected_plan} pour
 * connaitre le plan, compte les entites concernees via JDBC direct, et
 * leve {@link PlanLimitException} si le seuil est atteint.
 *
 * <p>Pour le P0 demo : utilisateurs (EMPLOYE + SUPERVISEUR + CLIENT) et
 * dossiers ({@code entreprise_dossiers}). Storage = P1 partiel (compte
 * seulement pour le {@code /usage} sans bloquer l'upload).
 *
 * <p>Activation conditionnelle via property
 * {@code jurika.plan-limits.enabled=true} (defaut true). On peut couper
 * l'enforcement en local pour les tests d'integration qui sortent
 * volontairement du plan.
 *
 * <p>Resilience : si {@code workspaces.selected_plan} est NULL (workspaces
 * legacy pre-Sprint 11), on assume le plan {@code essentiel} (le plus
 * restrictif) — fail-safe vs fail-open.
 */
@Service
@ConditionalOnProperty(name = "jurika.plan-limits.enabled", havingValue = "true", matchIfMissing = true)
public class PlanLimitsService {

    private static final Logger log = LoggerFactory.getLogger(PlanLimitsService.class);
    private static final String DEFAULT_PLAN = "essentiel";

    private final JdbcTemplate jdbc;
    private final boolean enforcementEnabled;

    public PlanLimitsService(JdbcTemplate jdbc,
                             @Value("${jurika.plan-limits.enforcement-enabled:true}") boolean enforcementEnabled) {
        this.jdbc = jdbc;
        this.enforcementEnabled = enforcementEnabled;
    }

    public record UsageSnapshot(
            String planCode,
            String planLabel,
            int users, int maxUsers, boolean usersUnlimited,
            int dossiers, int maxDossiers, boolean dossiersUnlimited,
            long storageBytes, int maxStorageGb, boolean storageUnlimited
    ) {
        public boolean usersOver()    { return !usersUnlimited && users >= maxUsers; }
        public boolean dossiersOver() { return !dossiersUnlimited && dossiers >= maxDossiers; }
    }

    // ─── Snapshot pour /usage endpoint ──────────────────────────────────
    public UsageSnapshot snapshot(UUID workspaceId) {
        Plan plan = currentPlan(workspaceId);
        Quotas q = plan.quotas();
        int users = countUsers(workspaceId);
        int dossiers = countDossiers(workspaceId);
        long storage = countStorageBytes(workspaceId);
        return new UsageSnapshot(
                plan.internalCode(), plan.displayLabel(),
                users, q.maxUsers(), q.isUsersUnlimited(),
                dossiers, q.maxDossiers(), q.isDossiersUnlimited(),
                storage, q.maxStorageGb(), q.isStorageUnlimited());
    }

    // ─── Enforcement points ─────────────────────────────────────────────
    /** A appeler AVANT d'inserer un nouvel utilisateur. */
    public void enforceUserLimit(UUID workspaceId) {
        if (!enforcementEnabled) return;
        Plan plan = currentPlan(workspaceId);
        Quotas q = plan.quotas();
        if (q.isUsersUnlimited()) return;
        int current = countUsers(workspaceId);
        if (current >= q.maxUsers()) {
            throw new PlanLimitException(
                    "PLAN_LIMIT_USERS",
                    String.format("Limite d'utilisateurs atteinte pour le plan %s : %d/%d. "
                            + "Passez au plan superieur pour ajouter plus d'utilisateurs.",
                            plan.displayLabel(), current, q.maxUsers()),
                    "users", current, q.maxUsers(),
                    plan.internalCode(),
                    upgradeHintFor(plan, "users"));
        }
    }

    /** A appeler AVANT d'inserer un nouvel entreprise_dossier. */
    public void enforceDossierLimit(UUID workspaceId) {
        if (!enforcementEnabled) return;
        Plan plan = currentPlan(workspaceId);
        Quotas q = plan.quotas();
        if (q.isDossiersUnlimited()) return;
        int current = countDossiers(workspaceId);
        if (current >= q.maxDossiers()) {
            throw new PlanLimitException(
                    "PLAN_LIMIT_DOSSIERS",
                    String.format("Limite de dossiers atteinte pour le plan %s : %d/%d. "
                            + "Passez au plan superieur pour creer plus de dossiers.",
                            plan.displayLabel(), current, q.maxDossiers()),
                    "dossiers", current, q.maxDossiers(),
                    plan.internalCode(),
                    upgradeHintFor(plan, "dossiers"));
        }
    }

    /**
     * Storage P1 — pour l'instant on logge le depassement sans bloquer
     * l'upload (eviter de bloquer la demo si MinIO mesure mal le total).
     * Le frontend l'affiche en warning via /usage.
     */
    public void warnIfStorageOver(UUID workspaceId, long incomingBytes) {
        Plan plan = currentPlan(workspaceId);
        Quotas q = plan.quotas();
        if (q.isStorageUnlimited()) return;
        long current = countStorageBytes(workspaceId);
        long limit = (long) q.maxStorageGb() * 1024L * 1024L * 1024L;
        if (current + incomingBytes > limit) {
            log.warn("Storage limit warning workspace={} plan={} current={}MB incoming={}MB limit={}GB",
                    workspaceId, plan.internalCode(),
                    current / 1024 / 1024, incomingBytes / 1024 / 1024,
                    q.maxStorageGb());
        }
    }

    // ─── DB queries ─────────────────────────────────────────────────────
    private Plan currentPlan(UUID workspaceId) {
        String code;
        try {
            code = jdbc.queryForObject(
                    "SELECT selected_plan FROM workspaces WHERE id = ?",
                    String.class, workspaceId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            log.warn("Workspace {} introuvable lors du check plan -- defaut {}", workspaceId, DEFAULT_PLAN);
            return PlanCatalog.findByCode(DEFAULT_PLAN).orElseThrow();
        }
        return PlanCatalog.findByCode(code != null ? code : DEFAULT_PLAN)
                .orElse(PlanCatalog.findByCode(DEFAULT_PLAN).orElseThrow());
    }

    private int countUsers(UUID workspaceId) {
        // 2026-06-04 (fix P1) : le quota maxUsers d'un plan ne s'applique
        // QU'AUX EMPLOYE. Le SUPERVISEUR (1/workspace, admin du cabinet) est
        // toujours autorise (1 par definition). Les CLIENT (utilisateurs finaux
        // invites par dossier) ne sont jamais factures par siege.
        // Reference : decision metier 2026-06-04 (Oussama) — un cabinet paie
        // pour le nombre d'employes productifs, pas pour ses clients.
        // 2026-06-07 (BUG 6) : on ne compte plus les EMPLOYE desactives (status
        // INACTIVE) — sinon un superviseur ne pourrait pas re-utiliser un siege
        // libere et serait piege par sa propre purge. PENDING (invite jamais
        // venu) compte normalement : le siege est deja "reserve".
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE workspace_id = ? "
                        + "AND role = 'EMPLOYE' AND status <> 'INACTIVE'",
                Integer.class, workspaceId);
        return n == null ? 0 : n;
    }

    private int countDossiers(UUID workspaceId) {
        // entreprise_dossiers vit dans la DB principale jurika_db, accessible
        // via le JdbcTemplate du service qui appelle (auth-service ou
        // ticket-service partagent le datasource principal ; billing-service
        // a sa propre DB jurika_billing et ne devrait pas appeler ce check).
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM entreprise_dossiers WHERE workspace_id = ?",
                Integer.class, workspaceId);
        return n == null ? 0 : n;
    }

    private long countStorageBytes(UUID workspaceId) {
        // Approx : somme des tailles des documents juridiques + comptables +
        // fiscaux. Tables peuvent etre absentes selon le microservice qui
        // appelle — on tente chacune et on tolere une erreur (renvoie 0).
        long total = 0;
        for (String sql : new String[]{
                "SELECT COALESCE(SUM(file_size_bytes), 0) FROM dataroom_documents WHERE workspace_id = ?",
                "SELECT COALESCE(SUM(file_size_bytes), 0) FROM dataroom_comptable_documents WHERE workspace_id = ?",
                "SELECT COALESCE(SUM(file_size_bytes), 0) FROM dataroom_fiscal_documents WHERE workspace_id = ?"
        }) {
            try {
                Long n = jdbc.queryForObject(sql, Long.class, workspaceId);
                if (n != null) total += n;
            } catch (Exception ex) {
                // Table absente cote service appelant -- normal en hexagonal.
            }
        }
        return total;
    }

    private String upgradeHintFor(Plan current, String quota) {
        // Spec directeur 2026-06-02 : essentiel -> business -> entreprise.
        if ("essentiel".equals(current.internalCode())) {
            return "Passez au plan Business (100 dossiers, 6 utilisateurs, 20 Go).";
        }
        if ("business".equals(current.internalCode())) {
            return "Contactez les ventes pour le plan Entreprise (illimite).";
        }
        return "Contactez les ventes.";
    }

}
