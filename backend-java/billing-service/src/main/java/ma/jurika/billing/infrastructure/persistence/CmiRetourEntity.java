package ma.jurika.billing.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Lot L7 (E5), RG-PAY-05 : journal d'un retour serveur du CMI (billing V7). Champs nommes
 * seulement : aucune donnee de carte (RG-PAY-04).
 */
@Entity
@Table(name = "cmi_retours")
public class CmiRetourEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recu_le", nullable = false)
    private Instant recuLe = Instant.now();

    @Column(name = "oid", length = 64)
    private String oid;

    @Column(name = "signature_valide", nullable = false)
    private boolean signatureValide;

    @Column(name = "code_retour", length = 10)
    private String codeRetour;

    @Column(name = "reponse_cmi", length = 20)
    private String reponseCmi;

    @Column(name = "transaction", length = 64)
    private String transaction;

    @Column(name = "montant", length = 20)
    private String montant;

    @Column(name = "reponse_rendue", nullable = false, length = 40)
    private String reponseRendue;

    @Column(name = "payment_id")
    private Long paymentId;

    protected CmiRetourEntity() {}

    public CmiRetourEntity(String oid, boolean signatureValide, String codeRetour, String reponseCmi,
                           String transaction, String montant, String reponseRendue, Long paymentId) {
        this.oid = tronquer(oid, 64);
        this.signatureValide = signatureValide;
        this.codeRetour = tronquer(codeRetour, 10);
        this.reponseCmi = tronquer(reponseCmi, 20);
        this.transaction = tronquer(transaction, 64);
        this.montant = tronquer(montant, 20);
        this.reponseRendue = reponseRendue;
        this.paymentId = paymentId;
    }

    private static String tronquer(String s, int n) {
        return s == null ? null : (s.length() <= n ? s : s.substring(0, n));
    }

    public Long getId() { return id; }
    public String getOid() { return oid; }
    public boolean isSignatureValide() { return signatureValide; }
    public String getReponseRendue() { return reponseRendue; }
    public Long getPaymentId() { return paymentId; }
}
