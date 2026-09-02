package ma.jurika.common.exception;

/**
 * Sprint Beta (pricing-deploy) — TASK 3.
 *
 * <p>Levee lorsqu'une operation tente de depasser un quota du plan
 * tarifaire courant (RG-SAAS-06 + RG-BL-QUOTA). Mappee a HTTP
 * <strong>402 Payment Required</strong> par
 * {@link GlobalExceptionHandler} — semantique identique au soft-lock
 * trial (RG-SAAS-07) et au verrou abonnement expire.
 *
 * <p>Codes utilises :
 * <ul>
 *   <li>{@code PLAN_LIMIT_USERS}</li>
 *   <li>{@code PLAN_LIMIT_DOSSIERS}</li>
 *   <li>{@code PLAN_LIMIT_STORAGE}</li>
 * </ul>
 */
public class PlanLimitException extends BusinessException {

    private final String quota;       // "users" | "dossiers" | "storage"
    private final long current;       // valeur courante au moment du check
    private final long limit;         // limite du plan
    private final String currentPlan; // 'essentiel' | 'business' | 'entreprise' (spec 2026-06-02)
    private final String upgradeHint; // ex: 'Passez en Business pour 100 dossiers'

    public PlanLimitException(String code, String message, String quota,
                              long current, long limit, String currentPlan, String upgradeHint) {
        super(code, message);
        this.quota = quota;
        this.current = current;
        this.limit = limit;
        this.currentPlan = currentPlan;
        this.upgradeHint = upgradeHint;
    }

    public String quota() { return quota; }
    public long current() { return current; }
    public long limit() { return limit; }
    public String currentPlan() { return currentPlan; }
    public String upgradeHint() { return upgradeHint; }
}
