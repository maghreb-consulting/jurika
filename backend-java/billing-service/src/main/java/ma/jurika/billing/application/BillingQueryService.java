package ma.jurika.billing.application;

import com.stripe.exception.StripeException;
import com.stripe.model.billingportal.Session;
import ma.jurika.billing.api.dto.BillingDtos;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.InvoiceJpaRepository;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.PaymentMethodJpaRepository;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.SubscriptionJpaRepository;
import ma.jurika.billing.infrastructure.persistence.InvoiceEntity;
import ma.jurika.billing.infrastructure.persistence.PaymentMethodEntity;
import ma.jurika.billing.infrastructure.persistence.SubscriptionEntity;
import ma.jurika.billing.infrastructure.stripe.BillingProperties;
import ma.jurika.billing.infrastructure.stripe.StripeService;
import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Sprint 12 — services de lecture billing pour les endpoints GET.
 * Pas d'ecriture (sauf retrieveSubscription qui touche Stripe mais ne mute pas).
 */
@Service
public class BillingQueryService {

    private final SubscriptionJpaRepository subscriptionRepo;
    private final InvoiceJpaRepository invoiceRepo;
    private final PaymentMethodJpaRepository paymentMethodRepo;
    private final StripeService stripeService;
    private final BillingProperties billingProperties;

    public BillingQueryService(SubscriptionJpaRepository subscriptionRepo,
                                InvoiceJpaRepository invoiceRepo,
                                PaymentMethodJpaRepository paymentMethodRepo,
                                StripeService stripeService,
                                BillingProperties billingProperties) {
        this.subscriptionRepo = subscriptionRepo;
        this.invoiceRepo = invoiceRepo;
        this.paymentMethodRepo = paymentMethodRepo;
        this.stripeService = stripeService;
        this.billingProperties = billingProperties;
    }

    @Transactional(readOnly = true)
    public BillingDtos.SubscriptionDto getCurrentSubscription(UUID workspaceId) {
        SubscriptionEntity sub = subscriptionRepo
                .findFirstByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
                .orElseThrow(() -> new NotFoundException("Aucune souscription pour ce workspace"));
        PaymentMethodEntity pm = paymentMethodRepo
                .findFirstByWorkspaceIdAndIsDefaultTrue(workspaceId).orElse(null);
        return BillingDtos.SubscriptionDto.from(sub, pm);
    }

    @Transactional(readOnly = true)
    public BillingDtos.InvoicesPageDto listInvoices(UUID workspaceId, int page, int size) {
        Page<InvoiceEntity> p = invoiceRepo.findAllByWorkspaceIdOrderByIssuedAtDesc(
                workspaceId, PageRequest.of(page, size));
        List<BillingDtos.InvoiceDto> items = p.getContent().stream()
                .map(BillingDtos.InvoiceDto::from)
                .toList();
        return new BillingDtos.InvoicesPageDto(items, p.getTotalElements(), p.getNumber(), p.getSize());
    }

    /**
     * Retourne l'URL hosted PDF de la facture (proxy depuis Stripe).
     * Pas de fetch du binary cote serveur — economie de bandwidth + Stripe
     * URLs signees sont publiques mais a duree limitee.
     */
    @Transactional(readOnly = true)
    public String getInvoicePdfUrl(UUID workspaceId, Long invoiceId) {
        InvoiceEntity invoice = invoiceRepo.findByIdAndWorkspaceId(invoiceId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Facture introuvable"));
        if (invoice.getInvoicePdfUrl() == null || invoice.getInvoicePdfUrl().isBlank()) {
            throw new BusinessException("INVOICE_PDF_MISSING",
                    "PDF non disponible chez Stripe pour cette facture");
        }
        return invoice.getInvoicePdfUrl();
    }

    /**
     * Cree une session Customer Portal Stripe (RG-BL09). Necessite que le
     * workspace ait deja un stripe_customer_id (donc au moins une tentative
     * de souscription).
     */
    public String createCustomerPortalUrl(UUID workspaceId) {
        SubscriptionEntity sub = subscriptionRepo
                .findFirstByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
                .orElseThrow(() -> new NotFoundException(
                        "Aucun customer Stripe pour ce workspace — souscris d'abord."));
        try {
            Session session = stripeService.createCustomerPortalSession(
                    sub.getStripeCustomerId(), workspaceId,
                    billingProperties.getPortal().getReturnUrl());
            return session.getUrl();
        } catch (StripeException e) {
            throw new BusinessException("STRIPE_ERROR", "Stripe portal error: " + e.getMessage());
        }
    }
}
