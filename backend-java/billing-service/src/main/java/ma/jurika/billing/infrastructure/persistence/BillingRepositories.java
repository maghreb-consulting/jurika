package ma.jurika.billing.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositories Spring Data JPA du billing-service, regroupes (interfaces imbriquees, cf.
 * {@link BillingJpaConfig}). Toute lecture d'un cabinet filtre explicitement sur
 * {@code workspaceId} ; les lectures sans workspace sont celles du super-admin et des taches
 * planifiees, et le disent par leur nom.
 */
public class BillingRepositories {

    public interface SubscriptionJpaRepository extends JpaRepository<SubscriptionEntity, Long> {
        Optional<SubscriptionEntity> findFirstByWorkspaceIdAndStatusOrderByCreatedAtDesc(UUID workspaceId, String status);

        Optional<SubscriptionEntity> findFirstByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

        long countByWorkspaceId(UUID workspaceId);

        /** Verrou d'ecriture : application d'un paiement a l'abonnement actif. */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select s from SubscriptionEntity s where s.workspaceId = :ws and s.status = 'active'")
        Optional<SubscriptionEntity> verrouillerActif(@Param("ws") UUID workspaceId);

        /** Super-admin : tous les abonnements, du plus recent au plus ancien. */
        List<SubscriptionEntity> findAllByOrderByCreatedAtDesc();

        /** Tache planifiee : abonnements actifs dont la periode payee est terminee (fin exclue). */
        List<SubscriptionEntity> findAllByStatusAndPeriodeFinLessThanEqual(String status, LocalDate jour);
    }

    public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {
        Optional<PaymentEntity> findByIdAndWorkspaceId(Long id, UUID workspaceId);

        List<PaymentEntity> findAllByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

        Optional<PaymentEntity> findByCmiOid(String cmiOid);

        /** Verrou d'ecriture : une confirmation a la fois pour un paiement (RG-PAY-05). */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select p from PaymentEntity p where p.id = :id")
        Optional<PaymentEntity> verrouiller(@Param("id") Long id);

        /** Super-admin : paiements d'un statut, tous cabinets. */
        List<PaymentEntity> findAllByStatusOrderByCreatedAtAsc(String status);

        /** Super-admin : paiements recents, tous cabinets. */
        List<PaymentEntity> findTop200ByOrderByCreatedAtDesc();

        /** Tache planifiee : activations a propager vers auth-service. */
        List<PaymentEntity> findAllByStatusAndActivationPropageeFalse(String status);

        /** Tache planifiee : paiements acquis dont la facture attend une mention. */
        List<PaymentEntity> findAllByStatusAndFactureRefusIsNotNull(String status);

        /** Un paiement en attente de meme objet pour ce cabinet (doublon d'un double clic). */
        List<PaymentEntity> findAllByWorkspaceIdAndStatus(UUID workspaceId, String status);
    }

    public interface FactureJpaRepository extends JpaRepository<FactureEntity, Long> {
        List<FactureEntity> findAllByWorkspaceIdOrderByDateEmissionDescRangDesc(UUID workspaceId);

        Optional<FactureEntity> findByIdAndWorkspaceId(Long id, UUID workspaceId);

        Optional<FactureEntity> findByPaymentId(Long paymentId);

        /** Super-admin : factures recentes, tous cabinets. */
        List<FactureEntity> findTop500ByOrderByAnneeDescRangDesc();
    }

    public interface CmiRetourJpaRepository extends JpaRepository<CmiRetourEntity, Long> {
    }

    private BillingRepositories() {}

    /** Lot L7 (socle commun) : parametres de l'editeur, une seule ligne (id = 1). */
    public interface EditeurParametresJpaRepository extends JpaRepository<EditeurParametresEntity, Short> {
        default java.util.Optional<EditeurParametresEntity> unique() {
            return findById(EditeurParametresEntity.ID_UNIQUE);
        }
    }
}
