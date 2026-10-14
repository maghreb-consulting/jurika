package ma.jurika.billing.infrastructure.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Paiement d'un cabinet (table {@code payments}).
 *
 * <p>Lot L7 (E5), RG-PAY-01 a RG-PAY-05 : quatre modes ({@code CARD} par le CMI,
 * {@code BANK_TRANSFER}, {@code CHEQUE}, {@code CASH} enregistres par le super-admin). Statuts :
 * {@code PENDING} (en attente : retour serveur du CMI ou reception constatee par le super-admin),
 * {@code COMPLETED} (acquis : abonnement active ou prolonge, facture emise), {@code FAILED} (refuse
 * par le CMI ou rejete par le super-admin), {@code CANCELLED}. {@code amount_mad_cents} est le
 * montant TTC a regler ; HT et TVA sont figes a la preparation, au taux des parametres de
 * l'editeur. Les colonnes Stripe historiques ne sont plus mappees (billing V7).
 */
@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "plan_code", nullable = false, length = 50)
    private String planCode;

    @Column(name = "plan_precedent", length = 50)
    private String planPrecedent;

    @Column(name = "billing_period", nullable = false, length = 20)
    private String billingPeriod;

    @Column(name = "objet", length = 20)
    private String objet;

    @Column(name = "periode_debut")
    private LocalDate periodeDebut;

    @Column(name = "periode_fin")
    private LocalDate periodeFin;

    @Column(name = "amount_mad_cents", nullable = false)
    private long amountMadCents;

    @Column(name = "montant_ht_centimes")
    private Long montantHtCentimes;

    @Column(name = "montant_tva_centimes")
    private Long montantTvaCentimes;

    @Column(name = "taux_tva", precision = 5, scale = 2)
    private BigDecimal tauxTva;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "mad";

    @Column(name = "method", nullable = false, length = 30)
    private String method;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING";

    @Column(name = "bank_reference")
    private String bankReference;

    @Column(name = "cheque_number", length = 50)
    private String chequeNumber;

    @Column(name = "cmi_oid", length = 64)
    private String cmiOid;

    @Column(name = "cmi_code_retour", length = 10)
    private String cmiCodeRetour;

    @Column(name = "cmi_transaction", length = 64)
    private String cmiTransaction;

    @Column(name = "recu_le")
    private LocalDate recuLe;

    @Column(name = "enregistre_par")
    private UUID enregistrePar;

    @Column(name = "activation_propagee", nullable = false)
    private boolean activationPropagee = false;

    @Column(name = "inscription_en_ligne", nullable = false)
    private boolean inscriptionEnLigne = false;

    @Column(name = "facture_refus", columnDefinition = "TEXT")
    private String factureRefus;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "validated_by")
    private String validatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String v) { this.planCode = v; }
    public String getPlanPrecedent() { return planPrecedent; }
    public void setPlanPrecedent(String v) { this.planPrecedent = v; }
    public String getBillingPeriod() { return billingPeriod; }
    public void setBillingPeriod(String v) { this.billingPeriod = v; }
    public String getObjet() { return objet; }
    public void setObjet(String v) { this.objet = v; }
    public LocalDate getPeriodeDebut() { return periodeDebut; }
    public void setPeriodeDebut(LocalDate v) { this.periodeDebut = v; }
    public LocalDate getPeriodeFin() { return periodeFin; }
    public void setPeriodeFin(LocalDate v) { this.periodeFin = v; }
    public long getAmountMadCents() { return amountMadCents; }
    public void setAmountMadCents(long v) { this.amountMadCents = v; }
    public Long getMontantHtCentimes() { return montantHtCentimes; }
    public void setMontantHtCentimes(Long v) { this.montantHtCentimes = v; }
    public Long getMontantTvaCentimes() { return montantTvaCentimes; }
    public void setMontantTvaCentimes(Long v) { this.montantTvaCentimes = v; }
    public BigDecimal getTauxTva() { return tauxTva; }
    public void setTauxTva(BigDecimal v) { this.tauxTva = v; }
    public String getCurrency() { return currency; }
    public String getMethod() { return method; }
    public void setMethod(String v) { this.method = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getBankReference() { return bankReference; }
    public void setBankReference(String v) { this.bankReference = v; }
    public String getChequeNumber() { return chequeNumber; }
    public void setChequeNumber(String v) { this.chequeNumber = v; }
    public String getCmiOid() { return cmiOid; }
    public void setCmiOid(String v) { this.cmiOid = v; }
    public String getCmiCodeRetour() { return cmiCodeRetour; }
    public void setCmiCodeRetour(String v) { this.cmiCodeRetour = v; }
    public String getCmiTransaction() { return cmiTransaction; }
    public void setCmiTransaction(String v) { this.cmiTransaction = v; }
    public LocalDate getRecuLe() { return recuLe; }
    public void setRecuLe(LocalDate v) { this.recuLe = v; }
    public UUID getEnregistrePar() { return enregistrePar; }
    public void setEnregistrePar(UUID v) { this.enregistrePar = v; }
    public boolean isActivationPropagee() { return activationPropagee; }
    public void setActivationPropagee(boolean v) { this.activationPropagee = v; }
    public boolean isInscriptionEnLigne() { return inscriptionEnLigne; }
    public void setInscriptionEnLigne(boolean v) { this.inscriptionEnLigne = v; }
    public String getFactureRefus() { return factureRefus; }
    public void setFactureRefus(String v) { this.factureRefus = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public String getValidatedBy() { return validatedBy; }
    public void setValidatedBy(String v) { this.validatedBy = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant v) { this.completedAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
}
