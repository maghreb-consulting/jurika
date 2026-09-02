package ma.jurika.billing.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Sprint 12 — toutes les Spring Data JPA repositories billing dans un meme
 * fichier pour limiter le bruit (4 interfaces de 5-10 lignes).
 */
public class BillingRepositories {

    public interface SubscriptionJpaRepository extends JpaRepository<SubscriptionEntity, Long> {
        Optional<SubscriptionEntity> findByStripeSubscriptionId(String stripeSubscriptionId);

        Optional<SubscriptionEntity> findFirstByWorkspaceIdAndStatusOrderByCreatedAtDesc(UUID workspaceId, String status);

        Optional<SubscriptionEntity> findFirstByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

        Optional<SubscriptionEntity> findByStripeCustomerId(String stripeCustomerId);
    }

    public interface InvoiceJpaRepository extends JpaRepository<InvoiceEntity, Long> {
        Optional<InvoiceEntity> findByStripeInvoiceId(String stripeInvoiceId);

        Page<InvoiceEntity> findAllByWorkspaceIdOrderByIssuedAtDesc(UUID workspaceId, Pageable pageable);

        Optional<InvoiceEntity> findByIdAndWorkspaceId(Long id, UUID workspaceId);
    }

    public interface PaymentMethodJpaRepository extends JpaRepository<PaymentMethodEntity, Long> {
        Optional<PaymentMethodEntity> findFirstByWorkspaceIdAndIsDefaultTrue(UUID workspaceId);

        Optional<PaymentMethodEntity> findByStripePaymentMethodId(String stripePaymentMethodId);
    }

    public interface WebhookEventJpaRepository extends JpaRepository<WebhookEventEntity, Long> {
        Optional<WebhookEventEntity> findByStripeEventId(String stripeEventId);

        boolean existsByStripeEventId(String stripeEventId);
    }

    /** BUG 14 (2026-06-07) — paiements multi-moyens (CARD/BANK/CHEQUE/CASH). */
    public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {
        Optional<PaymentEntity> findByIdAndWorkspaceId(Long id, UUID workspaceId);

        java.util.List<PaymentEntity> findAllByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

        Optional<PaymentEntity> findFirstByWorkspaceIdAndStatusOrderByCreatedAtDesc(UUID workspaceId, String status);

        Optional<PaymentEntity> findByStripePaymentId(String stripePaymentId);
    }

    private BillingRepositories() {}
}
