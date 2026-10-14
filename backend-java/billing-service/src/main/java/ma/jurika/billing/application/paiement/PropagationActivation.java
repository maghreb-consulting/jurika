package ma.jurika.billing.application.paiement;

import ma.jurika.billing.application.cabinet.CabinetsPort;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.PaymentJpaRepository;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.SubscriptionJpaRepository;
import ma.jurika.billing.infrastructure.persistence.PaymentEntity;
import ma.jurika.billing.infrastructure.persistence.SubscriptionEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lot L7 (E5) : propage a auth-service l'activation (ou la reactivation, RG-TAR-08) qui suit un
 * paiement acquis : forfait, debut de l'abonnement, rythme, fin de la periode payee. Marque le
 * paiement {@code activation_propagee} une fois fait ; un echec laisse la marque a faux et la tache
 * planifiee rejoue ({@link ma.jurika.billing.infrastructure.config.BillingTachesPlanifiees}). Rien
 * n'est avale en silence : l'etat a rejouer est en base et visible du super-admin.
 */
@Component
public class PropagationActivation {

    private static final Logger log = LoggerFactory.getLogger(PropagationActivation.class);

    private final PaymentJpaRepository paiements;
    private final SubscriptionJpaRepository abonnements;
    private final CabinetsPort cabinets;
    private final TransactionTemplate tx;

    public PropagationActivation(PaymentJpaRepository paiements, SubscriptionJpaRepository abonnements,
                                 CabinetsPort cabinets, PlatformTransactionManager tm) {
        this.paiements = paiements;
        this.abonnements = abonnements;
        this.cabinets = cabinets;
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** @return vrai si l'activation est propagee (ou l'etait deja). */
    public boolean propager(Long paymentId) {
        try {
            Boolean ok = tx.execute(st -> {
                PaymentEntity p = paiements.findById(paymentId).orElseThrow();
                if (p.isActivationPropagee()) return true;
                SubscriptionEntity s = abonnements.findFirstByWorkspaceIdAndStatusOrderByCreatedAtDesc(p.getWorkspaceId(), "active")
                        .orElse(null);
                if (s == null) {
                    // Abonnement archive depuis l'acquisition : plus rien a activer (l'archivage, lui,
                    // a ete propage par la tache d'archivage).
                    log.warn("Paiement {} acquis, abonnement archive depuis : activation sans objet", paymentId);
                    p.setActivationPropagee(true);
                    paiements.save(p);
                    return true;
                }
                cabinets.activer(p.getWorkspaceId(), s.getPlanCode(), s.getEngagementDebut(), s.getRythme(), s.getPeriodeFin());
                // Identifiants du superviseur : seulement pour l'inscription en ligne payee par le cabinet,
                // a sa premiere souscription (une reprise ou un enregistrement par le super-admin ne
                // regenere jamais le mot de passe d'un superviseur deja installe).
                boolean emettreIdentifiants = p.isInscriptionEnLigne() && p.getEnregistrePar() == null
                        && "SOUSCRIPTION".equals(p.getObjet()) && abonnements.countByWorkspaceId(p.getWorkspaceId()) == 1;
                if (emettreIdentifiants) {
                    cabinets.emettreIdentifiants(p.getWorkspaceId(), "PAIEMENT_ACQUIS_" + p.getMethod());
                }
                p.setActivationPropagee(true);
                paiements.save(p);
                return true;
            });
            return Boolean.TRUE.equals(ok);
        } catch (RuntimeException e) {
            log.error("Activation du paiement {} non propagee a auth-service (rejouee plus tard) : {}", paymentId, e.getMessage());
            return false;
        }
    }
}
