package ma.jurika.billing.application.paiement;

import ma.jurika.billing.infrastructure.cmi.CmiProperties;
import ma.jurika.billing.infrastructure.cmi.CmiSpecification;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.CmiRetourJpaRepository;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.PaymentJpaRepository;
import ma.jurika.billing.infrastructure.persistence.CmiRetourEntity;
import ma.jurika.billing.infrastructure.persistence.PaymentEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * Lot L7 (E5), RG-PAY-05 : retour SERVEUR du CMI (callbackUrl), seule voie qui rend un paiement par
 * carte acquis.
 *
 * <ol>
 *   <li>Signature verifiee avec la cle du magasin ({@link CmiSpecification#signatureValide}) ;
 *       invalide : rien n'est fait, reponse {@code FAILURE}.</li>
 *   <li>Commande ({@code oid}) connue, montant et devise identiques a ceux du paiement prepare ;
 *       sinon : rien n'est fait, {@code FAILURE} (aucune capture).</li>
 *   <li>Accepte ({@code ProcReturnCode = 00}) : confirmation unique
 *       ({@link PaiementService#confirmer}, idempotente : un deuxieme retour identique ne cree ni
 *       paiement ni facture), reponse {@code ACTION=POSTAUTH}.</li>
 *   <li>Refuse : paiement en echec, reponse {@code APPROVED} (accuse de reception).</li>
 * </ol>
 * Chaque retour est journalise ({@code cmi_retours}) dans sa propre transaction, meme refuse.
 */
@Service
public class CmiRetourService {

    private static final Logger log = LoggerFactory.getLogger(CmiRetourService.class);

    private final CmiProperties cmi;
    private final PaymentJpaRepository paiements;
    private final CmiRetourJpaRepository journal;
    private final PaiementService service;
    private final TransactionTemplate tx;

    public CmiRetourService(CmiProperties cmi, PaymentJpaRepository paiements, CmiRetourJpaRepository journal,
                            PaiementService service, PlatformTransactionManager tm) {
        this.cmi = cmi;
        this.paiements = paiements;
        this.journal = journal;
        this.service = service;
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** @return la reponse texte attendue par le CMI. */
    public String traiter(Map<String, String> recus) {
        String oid = recus.get(CmiSpecification.OID);
        String code = recus.get(CmiSpecification.PROC_RETURN_CODE);
        String reponseCmi = recus.get(CmiSpecification.RESPONSE);
        String transaction = recus.get(CmiSpecification.TRANS_ID);
        String montant = recus.get(CmiSpecification.AMOUNT);

        if (!cmi.configure()) {
            return journaliser(oid, false, code, reponseCmi, transaction, montant, CmiSpecification.REPONSE_SIGNATURE_INVALIDE, null,
                    "CMI non configure");
        }
        boolean signature;
        try {
            signature = CmiSpecification.signatureValide(recus, cmi.getStoreKey());
        } catch (RuntimeException e) {
            signature = false;
        }
        if (!signature) {
            return journaliser(oid, false, code, reponseCmi, transaction, montant, CmiSpecification.REPONSE_SIGNATURE_INVALIDE, null,
                    "signature invalide");
        }
        Optional<PaymentEntity> trouve = oid == null ? Optional.empty() : paiements.findByCmiOid(oid);
        if (trouve.isEmpty()) {
            return journaliser(oid, true, code, reponseCmi, transaction, montant, CmiSpecification.REPONSE_SIGNATURE_INVALIDE, null,
                    "commande inconnue");
        }
        PaymentEntity p = trouve.get();
        long recu;
        try {
            recu = CmiSpecification.centimes(montant);
        } catch (IllegalArgumentException e) {
            recu = -1;
        }
        if (recu != p.getAmountMadCents() || !CmiSpecification.DEVISE_MAD.equals(recus.get(CmiSpecification.CURRENCY))) {
            return journaliser(oid, true, code, reponseCmi, transaction, montant, CmiSpecification.REPONSE_SIGNATURE_INVALIDE, p.getId(),
                    "montant ou devise different du paiement prepare");
        }
        if (CmiSpecification.CODE_ACCEPTE.equals(code)) {
            try {
                service.confirmer(p.getId(), "CMI", null, code, transaction);
            } catch (RuntimeException e) {
                log.error("Retour CMI accepte mais paiement {} non confirme : {}", p.getId(), e.getMessage());
                return journaliser(oid, true, code, reponseCmi, transaction, montant, CmiSpecification.REPONSE_SIGNATURE_INVALIDE,
                        p.getId(), "confirmation impossible : " + e.getMessage());
            }
            return journaliser(oid, true, code, reponseCmi, transaction, montant, CmiSpecification.REPONSE_ACCEPTE, p.getId(), null);
        }
        tx.executeWithoutResult(st -> {
            PaymentEntity q = paiements.verrouiller(p.getId()).orElseThrow();
            if ("PENDING".equals(q.getStatus())) {
                q.setStatus("FAILED");
                q.setCmiCodeRetour(code);
                q.setCmiTransaction(transaction);
                q.setNotes("Refus\u00e9 par le CMI (code " + code + ")");
                paiements.save(q);
            }
        });
        return journaliser(oid, true, code, reponseCmi, transaction, montant, CmiSpecification.REPONSE_REFUS_ACQUITTE, p.getId(), null);
    }

    private String journaliser(String oid, boolean signature, String code, String reponseCmi, String transaction,
                               String montant, String reponse, Long paymentId, String raison) {
        if (raison != null) {
            log.warn("Retour CMI oid={} : {} -> {}", oid, raison, reponse);
        }
        tx.executeWithoutResult(st -> journal.save(new CmiRetourEntity(oid, signature, code, reponseCmi, transaction,
                montant, reponse, paymentId)));
        return reponse;
    }
}
