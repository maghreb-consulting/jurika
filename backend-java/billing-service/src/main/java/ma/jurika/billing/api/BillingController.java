package ma.jurika.billing.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import ma.jurika.billing.api.dto.BillingDtos;
import ma.jurika.billing.application.BillingQueryService;
import ma.jurika.billing.application.ChangePlanUseCase;
import ma.jurika.billing.application.ContactSalesUseCase;
import ma.jurika.billing.application.PreparePaymentUseCase;
import ma.jurika.billing.application.SubscribeUseCase;
import ma.jurika.billing.application.ValidatePaymentUseCase;
import ma.jurika.common.billing.PlanCatalog;
import ma.jurika.common.billing.PlanCatalog.BillingPeriod;
import ma.jurika.common.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * Sprint 12 — endpoints billing pour l'admin cabinet.
 *
 * <p>RBAC (PLAN §2.6) : historiquement tout le controller exigeait
 * ROLE_SUPERVISEUR. La doc PLAN §2.6 mentionnait "ROLE_ADMIN_CABINET" mais
 * cette autorite n'a jamais existe dans l'enum
 * {@link ma.jurika.common.security.Role} : le role "admin du cabinet" correspond
 * au SUPERVISEUR cree par RegisterWorkspaceUseCase. Avec la hierarchie
 * SUPER_ADMIN &gt; SUPERVISEUR, un SUPER_ADMIN passe naturellement aussi
 * (fix bug C 2026-06-04).
 *
 * <p><b>Lot J3 (2026-06-27)</b> — le gating passe du niveau CLASSE au niveau
 * METHODE pour ouvrir le parcours d'upgrade a l'EMPLOYE (le CTA "Upgrade" lui
 * est deja affiche). Repartition :
 * <ul>
 *   <li><b>SUPERVISEUR + EMPLOYE</b> (parcours upgrade/souscription) :
 *       checkout-session, subscription (lecture necessaire au parcours),
 *       customer-portal, contact-sales, change-plan, change-plan/preview,
 *       prepare-payment.</li>
 *   <li><b>SUPERVISEUR seul</b> (operations sensibles / financieres) :
 *       invoices (+download) = historique financier ; payments (liste).</li>
 *   <li><b>SUPER_ADMIN seul</b> (lot L0, RG-PAY-02) :
 *       <b>payments/{id}/validate</b> = mutation critique (active le workspace +
 *       emet les identifiants).</li>
 * </ul>
 * L'annotation au niveau METHODE prime sur celle de la CLASSE (Spring Security).
 * Le {@code @PreAuthorize} de classe est conserve comme garde-fou par defaut :
 * tout nouvel endpoint non annote reste SUPERVISEUR-only.
 */
@RestController
@RequestMapping("/api/v1/billing")
@PreAuthorize("hasRole('SUPERVISEUR')")
public class BillingController {

    private static final Logger log = LoggerFactory.getLogger(BillingController.class);

    private final SubscribeUseCase subscribeUseCase;
    private final BillingQueryService queryService;
    private final ContactSalesUseCase contactSalesUseCase;
    private final ChangePlanUseCase changePlanUseCase;
    private final PreparePaymentUseCase preparePaymentUseCase;
    private final ValidatePaymentUseCase validatePaymentUseCase;

    public BillingController(SubscribeUseCase subscribeUseCase,
                              BillingQueryService queryService,
                              ContactSalesUseCase contactSalesUseCase,
                              ChangePlanUseCase changePlanUseCase,
                              PreparePaymentUseCase preparePaymentUseCase,
                              ValidatePaymentUseCase validatePaymentUseCase) {
        this.subscribeUseCase = subscribeUseCase;
        this.queryService = queryService;
        this.contactSalesUseCase = contactSalesUseCase;
        this.changePlanUseCase = changePlanUseCase;
        this.preparePaymentUseCase = preparePaymentUseCase;
        this.validatePaymentUseCase = validatePaymentUseCase;
    }

    // ─── DTOs locaux pour les endpoints mutables ───────────────────────
    /**
     * Sprint Beta (pricing-deploy) — {@code billingPeriod} optionnel ajoute.
     * Par defaut MONTHLY pour retro-compat avec frontend Sprint 12.
     */
    public record CheckoutSessionRequest(
            @NotBlank String planCode,
            @NotBlank @Email String contactEmail,
            @NotBlank String workspaceName,
            String billingPeriod
    ) {}
    public record CheckoutSessionResponse(
            String checkoutSessionId,
            String checkoutUrl,
            String planCode,
            String billingPeriod
    ) {}

    // ─── T5 — Souscription ─────────────────────────────────────────────
    @PostMapping("/checkout-session")
    @PreAuthorize("hasAnyRole('SUPERVISEUR','EMPLOYE')")
    public ResponseEntity<CheckoutSessionResponse> createCheckoutSession(@Valid @RequestBody CheckoutSessionRequest req) {
        UUID workspaceId = requireWorkspace();
        BillingPeriod period = BillingPeriod.fromString(req.billingPeriod());
        SubscribeUseCase.Result result = subscribeUseCase.subscribe(new SubscribeUseCase.Command(
                workspaceId, req.contactEmail(), req.workspaceName(),
                PlanCatalog.normalize(req.planCode()), period));
        log.info("Checkout session emise workspace={} plan={} period={} session={}",
                workspaceId, req.planCode(), period, result.checkoutSessionId());
        return ResponseEntity.ok(new CheckoutSessionResponse(
                result.checkoutSessionId(), result.checkoutUrl(), result.planCode(), period.suffix()));
    }

    // ─── T6 — Lectures ─────────────────────────────────────────────────
    /**
     * GET /api/v1/billing/subscription — souscription courante + payment method.
     * Lecture necessaire au parcours d'upgrade (BillingPage / ChangePlanPage),
     * donc ouverte a l'EMPLOYE.
     */
    @GetMapping("/subscription")
    @PreAuthorize("hasAnyRole('SUPERVISEUR','EMPLOYE')")
    public ResponseEntity<BillingDtos.SubscriptionDto> getSubscription() {
        return ResponseEntity.ok(queryService.getCurrentSubscription(requireWorkspace()));
    }

    /**
     * GET /api/v1/billing/invoices — liste paginee (default 20 derniers).
     * Historique financier : reserve SUPERVISEUR (le front BillingPage degrade
     * proprement si 403 pour l'employe, via .catch()).
     */
    @GetMapping("/invoices")
    @PreAuthorize("hasRole('SUPERVISEUR')")
    public ResponseEntity<BillingDtos.InvoicesPageDto> listInvoices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 100);
        return ResponseEntity.ok(queryService.listInvoices(requireWorkspace(), page, size));
    }

    /**
     * GET /api/v1/billing/invoices/{id}/download — redirige vers le PDF
     * hoste cote Stripe (302 Location).
     */
    @GetMapping("/invoices/{id}/download")
    @PreAuthorize("hasRole('SUPERVISEUR')")
    public ResponseEntity<Void> downloadInvoice(@PathVariable Long id) {
        String pdfUrl = queryService.getInvoicePdfUrl(requireWorkspace(), id);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, pdfUrl)
                .build();
    }

    /** POST /api/v1/billing/customer-portal — URL Stripe Customer Portal (RG-BL09). */
    @PostMapping("/customer-portal")
    @PreAuthorize("hasAnyRole('SUPERVISEUR','EMPLOYE')")
    public ResponseEntity<BillingDtos.CustomerPortalDto> createCustomerPortal() {
        String url = queryService.createCustomerPortalUrl(requireWorkspace());
        return ResponseEntity.ok(new BillingDtos.CustomerPortalDto(url));
    }

    /** POST /api/v1/billing/contact-sales — Enterprise sur devis (RG-BL10). */
    @PostMapping("/contact-sales")
    @PreAuthorize("hasAnyRole('SUPERVISEUR','EMPLOYE')")
    public ResponseEntity<BillingDtos.ContactSalesResponse> contactSales(
            @RequestBody BillingDtos.ContactSalesRequest req) {
        return ResponseEntity.ok(contactSalesUseCase.send(requireWorkspace(), req));
    }

    // ─── BUG 8 (2026-06-07) — Change plan ───────────────────────────────
    /**
     * POST /api/v1/billing/change-plan — modifier l'abonnement existant
     * vers un autre plan (Essentiel ↔ Business). Bloque le downgrade si
     * le workspace depasse les nouveaux quotas. Entreprise non supporte
     * ici (route vers /contact-sales cote front).
     */
    @PostMapping("/change-plan")
    @PreAuthorize("hasAnyRole('SUPERVISEUR','EMPLOYE')")
    public ResponseEntity<BillingDtos.ChangePlanResponse> changePlan(
            @RequestBody BillingDtos.ChangePlanRequest req) {
        UUID workspaceId = requireWorkspace();
        BillingPeriod period = BillingPeriod.fromString(req.billingPeriod());
        ChangePlanUseCase.Result r = changePlanUseCase.execute(
                new ChangePlanUseCase.Command(workspaceId, req.targetPlanCode(), period));
        return ResponseEntity.ok(new BillingDtos.ChangePlanResponse(
                r.previousPlanCode(), r.newPlanCode(), r.newBillingPeriod(),
                r.stripeSubscriptionId(), r.currentPeriodEnd()));
    }

    /** POST /api/v1/billing/change-plan/preview — apercu (proration, blocage). */
    @PostMapping("/change-plan/preview")
    @PreAuthorize("hasAnyRole('SUPERVISEUR','EMPLOYE')")
    public ResponseEntity<BillingDtos.ChangePlanPreviewResponse> previewChangePlan(
            @RequestBody BillingDtos.ChangePlanRequest req) {
        UUID workspaceId = requireWorkspace();
        BillingPeriod period = BillingPeriod.fromString(req.billingPeriod());
        ChangePlanUseCase.Preview p = changePlanUseCase.preview(
                new ChangePlanUseCase.Command(workspaceId, req.targetPlanCode(), period));
        return ResponseEntity.ok(new BillingDtos.ChangePlanPreviewResponse(
                p.fromPlan(), p.toPlan(), p.billingPeriod(),
                p.isUpgrade(), p.isDowngrade(),
                p.downgradeAllowed(), p.blockedReason(),
                p.fromPriceMad(), p.toPriceMad()));
    }

    // ─── BUG 14 (2026-06-07) — Multi-method payments ────────────────────
    /**
     * POST /api/v1/billing/prepare-payment — initialise un paiement
     * (CARD/BANK_TRANSFER/CHEQUE/CASH). CARD = checkoutUrl Stripe a suivre,
     * les 3 autres = instructions + paiement PENDING en attente de validation
     * manuelle.
     */
    @PostMapping("/prepare-payment")
    @PreAuthorize("hasAnyRole('SUPERVISEUR','EMPLOYE')")
    public ResponseEntity<BillingDtos.PreparePaymentResponse> preparePayment(
            @RequestBody BillingDtos.PreparePaymentRequest req) {
        UUID workspaceId = requireWorkspace();
        return ResponseEntity.ok(preparePaymentUseCase.execute(workspaceId, req));
    }

    /**
     * GET /api/v1/billing/payments — derniers paiements PENDING/COMPLETED du
     * workspace. Vue financiere (montants, methodes) : reserve SUPERVISEUR.
     */
    @GetMapping("/payments")
    @PreAuthorize("hasRole('SUPERVISEUR')")
    public ResponseEntity<BillingDtos.PaymentsListDto> listPayments() {
        return ResponseEntity.ok(preparePaymentUseCase.listForWorkspace(requireWorkspace()));
    }

    /**
     * PATCH /api/v1/billing/payments/{id}/validate — passe PENDING -> COMPLETED,
     * declenche l'activation du workspace du paiement + envoi des identifiants.
     *
     * <p><b>Lot L0 (E17, RG-PAY-02)</b> : operation SENSIBLE reservee au
     * SUPER_ADMIN (JURIKA confirme la reception d'un virement, cheque ou
     * especes). Le cabinet ne valide jamais son propre paiement. Le paiement est
     * designe par son identifiant seul (le workspace du super-admin n'est pas
     * celui du cabinet).
     */
    @PatchMapping("/payments/{id}/validate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<BillingDtos.PaymentDto> validatePayment(
            @PathVariable Long id,
            @RequestBody(required = false) BillingDtos.ValidatePaymentRequest req) {
        return ResponseEntity.ok(validatePaymentUseCase.execute(id, req));
    }

    // ─── helpers ───────────────────────────────────────────────────────
    private static UUID requireWorkspace() {
        UUID id = TenantContext.get();
        if (id == null) {
            throw new IllegalStateException("TenantContext.workspaceId absent — JwtAuthFilter doit etre actif");
        }
        return id;
    }
}
