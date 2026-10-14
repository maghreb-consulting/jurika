package ma.jurika.billing.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Abonnement d'un cabinet (table {@code subscriptions}).
 *
 * <p>Lot L7 (E5) : forfait souscrit pour un an (engagement), regle chaque mois ou en une fois
 * (rythme) ; {@code periode_debut}/{@code periode_fin} bornent la periode PAYEE (fin exclue). Un
 * changement vers un forfait inferieur ou de rythme attend la prochaine echeance
 * ({@code plan_a_venir}, {@code rythme_a_venir} ; RG-TAR-05, RG-TAR-06). Statuts : {@code active}
 * (un seul par cabinet), {@code unpaid} (echeance impayee : archive, RG-TAR-03), {@code cancelled}
 * (fin, resiliation, decision). Un abonnement termine n'est jamais supprime ; la reprise cree un
 * nouvel abonnement (RG-TAR-08). Les colonnes Stripe historiques ne sont plus mappees.
 */
@Entity
@Table(name = "subscriptions")
public class SubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "plan_code", nullable = false)
    private String planCode;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "rythme", length = 10)
    private String rythme;

    @Column(name = "engagement_debut")
    private LocalDate engagementDebut;

    @Column(name = "engagement_fin")
    private LocalDate engagementFin;

    @Column(name = "periode_debut")
    private LocalDate periodeDebut;

    @Column(name = "periode_fin")
    private LocalDate periodeFin;

    @Column(name = "plan_a_venir", length = 50)
    private String planAVenir;

    @Column(name = "rythme_a_venir", length = 10)
    private String rythmeAVenir;

    @Column(name = "prix_echeance_ht_centimes")
    private Long prixEcheanceHtCentimes;

    @Column(name = "archive_motif", length = 20)
    private String archiveMotif;

    /** Historique (V1) : bornes en instants, tenues a jour avec les dates (minuit, Casablanca). */
    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd = false;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    /** Pose la periode payee (dates et instants historiques). */
    /** Lot L7 : resiliation enregistree (RG-CTR-08) : archivage a la fin de la periode, sans renouvellement. */
    public boolean isCancelAtPeriodEnd() { return cancelAtPeriodEnd; }
    public void setCancelAtPeriodEnd(boolean v) { this.cancelAtPeriodEnd = v; }

    public void periode(LocalDate debut, LocalDate fin) {
        this.periodeDebut = debut;
        this.periodeFin = fin;
        this.currentPeriodStart = debut.atStartOfDay(ma.jurika.common.billing.PeriodeAbonnement.CASABLANCA).toInstant();
        this.currentPeriodEnd = fin.atStartOfDay(ma.jurika.common.billing.PeriodeAbonnement.CASABLANCA).toInstant();
    }

    public Long getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String v) { this.planCode = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getRythme() { return rythme; }
    public void setRythme(String v) { this.rythme = v; }
    public LocalDate getEngagementDebut() { return engagementDebut; }
    public void setEngagementDebut(LocalDate v) { this.engagementDebut = v; }
    public LocalDate getEngagementFin() { return engagementFin; }
    public void setEngagementFin(LocalDate v) { this.engagementFin = v; }
    public LocalDate getPeriodeDebut() { return periodeDebut; }
    public LocalDate getPeriodeFin() { return periodeFin; }
    public String getPlanAVenir() { return planAVenir; }
    public void setPlanAVenir(String v) { this.planAVenir = v; }
    public String getRythmeAVenir() { return rythmeAVenir; }
    public void setRythmeAVenir(String v) { this.rythmeAVenir = v; }
    public Long getPrixEcheanceHtCentimes() { return prixEcheanceHtCentimes; }
    public void setPrixEcheanceHtCentimes(Long v) { this.prixEcheanceHtCentimes = v; }
    public String getArchiveMotif() { return archiveMotif; }
    public void setArchiveMotif(String v) { this.archiveMotif = v; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant v) { this.cancelledAt = v; }
    public Instant getCreatedAt() { return createdAt; }
}
