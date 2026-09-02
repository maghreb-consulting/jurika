package ma.jurika.billing.application;

import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import ma.jurika.billing.application.workspace.WorkspaceStatusUpdater;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.SubscriptionJpaRepository;
import ma.jurika.billing.infrastructure.persistence.SubscriptionEntity;
import ma.jurika.billing.infrastructure.stripe.StripeProperties;
import ma.jurika.billing.infrastructure.stripe.StripeService;
import ma.jurika.common.billing.PlanCatalog;
import ma.jurika.common.billing.PlanCatalog.BillingPeriod;
import ma.jurika.common.billing.PlanCatalog.Plan;
import ma.jurika.common.billing.PlanCatalog.Quotas;
import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.PlanLimitException;
import ma.jurika.common.trial.WorkspaceStatusFeignClient;
import ma.jurika.common.trial.WorkspaceStatusFeignClient.UsageSnapshotDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * BUG 8 (2026-06-07) — Use case "Changer de forfait" sur une subscription
 * Stripe deja active. Differe de {@link SubscribeUseCase} qui n'est utilise
 * que pour la 1re souscription d'un workspace (PENDING → active).
 *
 * <p>Pipeline :
 * <ol>
 *   <li>Validation : workspaceId, subscription active trouvee, plan cible
 *       connu et != courant, entreprise rejete (envoyer vers /contact-sales)</li>
 *   <li>Si downgrade (Essentiel &lt; Business) : appel
 *       {@code GET /internal/workspaces/{id}/usage} pour verifier que le
 *       workspace tient dans les nouveaux quotas (employes/dossiers/storage).
 *       Throw {@link PlanLimitException} avec message clair sinon.</li>
 *   <li>Resolve nouveau Stripe priceId via {@link StripeProperties}</li>
 *   <li>Stripe : update subscription item + proration_behavior=create_prorations</li>
 *   <li>Persist : SubscriptionEntity.plan_code mis a jour localement
 *       (idempotent — le webhook customer.subscription.updated convergera
 *       de toute facon, mais on optimise la latence UI)</li>
 *   <li>Notify auth-service via WorkspaceStatusUpdater.activate(...) pour
 *       refresh workspace.selected_plan</li>
 * </ol>
 *
 * <p>Idempotency : la cle Stripe inclut workspace+targetPlan+date, donc
 * un double-clic = meme operation cote Stripe (24h). Le webhook
 * {@code customer.subscription.updated} ne risque pas de creer un doublon
 * de plan car il fait UPDATE par sub_id (pas INSERT).
 */
@Service
public class ChangePlanUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChangePlanUseCase.class);

    private final SubscriptionJpaRepository subscriptionRepo;
    private final StripeService stripeService;
    private final StripeProperties stripeProperties;
    private final WorkspaceStatusUpdater workspaceUpdater;
    private final ObjectProvider<WorkspaceStatusFeignClient> usageClient;

    public ChangePlanUseCase(SubscriptionJpaRepository subscriptionRepo,
                              StripeService stripeService,
                              StripeProperties stripeProperties,
                              WorkspaceStatusUpdater workspaceUpdater,
                              ObjectProvider<WorkspaceStatusFeignClient> usageClient) {
        this.subscriptionRepo = subscriptionRepo;
        this.stripeService = stripeService;
        this.stripeProperties = stripeProperties;
        this.workspaceUpdater = workspaceUpdater;
        this.usageClient = usageClient;
    }

    public record Command(UUID workspaceId, String targetPlanCode, BillingPeriod billingPeriod) {}

    public record Result(
            String previousPlanCode,
            String newPlanCode,
            String newBillingPeriod,
            String stripeSubscriptionId,
            Instant currentPeriodEnd
    ) {}

    public record Preview(
            String fromPlan, String toPlan, String billingPeriod,
            boolean isUpgrade, boolean isDowngrade,
            boolean downgradeAllowed, String blockedReason,
            Integer fromPriceMad, Integer toPriceMad
    ) {}

    @Transactional
    public Result execute(Command cmd) {
        validate(cmd);
        SubscriptionEntity current = requireActiveSubscription(cmd.workspaceId());

        String targetCode = PlanCatalog.normalize(cmd.targetPlanCode());
        if (targetCode.equals(PlanCatalog.normalize(current.getPlanCode()))) {
            // Idempotent — si le plan demande est deja le plan courant, on retourne
            // l'etat actuel sans appeler Stripe (evite proration zero + webhook bruit).
            return new Result(current.getPlanCode(), current.getPlanCode(),
                    cmd.billingPeriod() != null ? cmd.billingPeriod().suffix() : "monthly",
                    current.getStripeSubscriptionId(), current.getCurrentPeriodEnd());
        }

        BillingPeriod period = cmd.billingPeriod() != null ? cmd.billingPeriod() : BillingPeriod.MONTHLY;
        Plan target = PlanCatalog.findByCode(targetCode)
                .orElseThrow(() -> new BusinessException("PLAN_UNKNOWN", "Plan inconnu: " + cmd.targetPlanCode()));

        // RG-BL10 — entreprise sur devis, refuser cote backend (front route vers /contact-sales).
        if (target.quoteOnly()) {
            throw new BusinessException("PLAN_QUOTE_ONLY",
                    "Plan Entreprise sur devis — utilisez la demande contact-sales depuis l'app.");
        }

        // Downgrade guard — bloquant si le workspace depasse les nouveaux quotas.
        if (isDowngrade(current.getPlanCode(), targetCode)) {
            checkDowngradeAllowed(cmd.workspaceId(), target);
        }

        String newPriceId = stripeProperties.resolvePriceId(targetCode, period);

        try {
            Subscription updated = stripeService.updateSubscriptionPlan(
                    current.getStripeSubscriptionId(), newPriceId, "create_prorations",
                    cmd.workspaceId(), targetCode);

            String previousPlanCode = current.getPlanCode();
            current.setPlanCode(targetCode);
            current.setUpdatedAt(Instant.now());
            if (updated.getCurrentPeriodEnd() != null) {
                current.setCurrentPeriodEnd(Instant.ofEpochSecond(updated.getCurrentPeriodEnd()));
            }
            if (updated.getCurrentPeriodStart() != null) {
                current.setCurrentPeriodStart(Instant.ofEpochSecond(updated.getCurrentPeriodStart()));
            }
            subscriptionRepo.save(current);

            // RG-BL05 : repropage selected_plan vers auth-service immediatement
            // (le webhook customer.subscription.updated convergera aussi mais
            // on evite l'instant T+webhook ou le user voit l'ancien plan).
            workspaceUpdater.activate(cmd.workspaceId(), targetCode, current.getCurrentPeriodEnd());

            log.info("Plan change applique workspace={} from={} to={} period={} sub={}",
                    cmd.workspaceId(), previousPlanCode, targetCode, period, current.getStripeSubscriptionId());

            return new Result(previousPlanCode, targetCode, period.suffix(),
                    current.getStripeSubscriptionId(), current.getCurrentPeriodEnd());
        } catch (StripeException e) {
            log.error("Echec change plan workspace={} target={} : {}",
                    cmd.workspaceId(), targetCode, e.getMessage());
            throw new BusinessException("STRIPE_ERROR",
                    "Stripe error: " + (e.getStripeError() != null ? e.getStripeError().getMessage() : e.getMessage()));
        }
    }

    /**
     * Renvoie un apercu du changement sans appeler Stripe : utile pour la
     * page front "ChangePlanPage" qui affiche un encadre "vous passez de X
     * a Y, proration calculee a la confirmation" + bloque le bouton si le
     * downgrade est interdit.
     */
    @Transactional(readOnly = true)
    public Preview preview(Command cmd) {
        validate(cmd);
        SubscriptionEntity current = requireActiveSubscription(cmd.workspaceId());
        String fromCode = PlanCatalog.normalize(current.getPlanCode());
        String toCode = PlanCatalog.normalize(cmd.targetPlanCode());
        Plan from = PlanCatalog.findByCode(fromCode).orElse(null);
        Plan to = PlanCatalog.findByCode(toCode).orElse(null);
        if (to == null) {
            throw new BusinessException("PLAN_UNKNOWN", "Plan inconnu: " + cmd.targetPlanCode());
        }

        boolean upgrade = isUpgrade(fromCode, toCode);
        boolean downgrade = isDowngrade(fromCode, toCode);
        boolean downgradeAllowed = true;
        String blockedReason = null;
        if (downgrade) {
            try {
                checkDowngradeAllowed(cmd.workspaceId(), to);
            } catch (PlanLimitException ex) {
                downgradeAllowed = false;
                blockedReason = ex.getMessage();
            }
        }
        BillingPeriod period = cmd.billingPeriod() != null ? cmd.billingPeriod() : BillingPeriod.MONTHLY;
        Integer fromPrice = from == null ? null
                : (period == BillingPeriod.YEARLY ? from.priceYearlyMad() : from.priceMonthlyMad());
        Integer toPrice = period == BillingPeriod.YEARLY ? to.priceYearlyMad() : to.priceMonthlyMad();

        return new Preview(fromCode, toCode, period.suffix(),
                upgrade, downgrade, downgradeAllowed, blockedReason, fromPrice, toPrice);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static final java.util.List<String> RANK = java.util.List.of("essentiel", "business", "entreprise");

    private static boolean isUpgrade(String from, String to) {
        return RANK.indexOf(PlanCatalog.normalize(to)) > RANK.indexOf(PlanCatalog.normalize(from));
    }

    private static boolean isDowngrade(String from, String to) {
        int fi = RANK.indexOf(PlanCatalog.normalize(from));
        int ti = RANK.indexOf(PlanCatalog.normalize(to));
        return fi >= 0 && ti >= 0 && ti < fi;
    }

    private void validate(Command cmd) {
        if (cmd.workspaceId() == null) {
            throw new IllegalArgumentException("workspaceId requis");
        }
        if (cmd.targetPlanCode() == null || cmd.targetPlanCode().isBlank()) {
            throw new IllegalArgumentException("targetPlanCode requis");
        }
    }

    private SubscriptionEntity requireActiveSubscription(UUID workspaceId) {
        return subscriptionRepo
                .findFirstByWorkspaceIdAndStatusOrderByCreatedAtDesc(workspaceId, "active")
                .orElseThrow(() -> new NotFoundException(
                        "Aucun abonnement actif pour ce workspace — utilisez /checkout-session pour la 1re souscription."));
    }

    private void checkDowngradeAllowed(UUID workspaceId, Plan target) {
        WorkspaceStatusFeignClient client = usageClient.getIfAvailable();
        if (client == null) {
            // En dev/tests sans Feign, on autorise le downgrade (mode dégrade
            // — l'enforcement applicatif des quotas reste actif post-changement).
            log.warn("Feign usage indisponible — downgrade autorise par defaut workspace={}", workspaceId);
            return;
        }
        UsageSnapshotDto usage;
        try {
            usage = client.getUsage(workspaceId);
        } catch (RuntimeException ex) {
            log.warn("Echec /internal/usage workspace={} : {} — downgrade autorise par defaut",
                    workspaceId, ex.getMessage());
            return;
        }
        if (usage == null) return;
        Quotas q = target.quotas();

        if (!q.isUsersUnlimited() && usage.users() > q.maxUsers()) {
            throw new PlanLimitException("PLAN_DOWNGRADE_BLOCKED_USERS",
                    String.format("Downgrade impossible : %d employes actifs dans le workspace pour un plan %s limite a %d. "
                                    + "Supprimez %d employe(s) avant de redescendre.",
                            usage.users(), target.displayLabel(), q.maxUsers(),
                            usage.users() - q.maxUsers()),
                    "users", usage.users(), q.maxUsers(),
                    target.internalCode(), null);
        }
        if (!q.isDossiersUnlimited() && usage.dossiers() > q.maxDossiers()) {
            throw new PlanLimitException("PLAN_DOWNGRADE_BLOCKED_DOSSIERS",
                    String.format("Downgrade impossible : %d dossiers actifs pour un plan %s limite a %d. "
                                    + "Archivez %d dossier(s) avant de redescendre.",
                            usage.dossiers(), target.displayLabel(), q.maxDossiers(),
                            usage.dossiers() - q.maxDossiers()),
                    "dossiers", usage.dossiers(), q.maxDossiers(),
                    target.internalCode(), null);
        }
        if (!q.isStorageUnlimited()) {
            long maxBytes = (long) q.maxStorageGb() * 1024L * 1024L * 1024L;
            if (usage.storageBytes() > maxBytes) {
                throw new PlanLimitException("PLAN_DOWNGRADE_BLOCKED_STORAGE",
                        String.format("Downgrade impossible : stockage utilise %d Go > limite plan %s (%d Go).",
                                usage.storageBytes() / (1024 * 1024 * 1024),
                                target.displayLabel(), q.maxStorageGb()),
                        "storage", usage.storageBytes(), maxBytes,
                        target.internalCode(), null);
            }
        }
    }
}
