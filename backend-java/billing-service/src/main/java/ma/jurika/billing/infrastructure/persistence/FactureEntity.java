package ma.jurika.billing.infrastructure.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lot L7 (E5), RG-PAY-03 : facture JURIKA (billing V8). Une par paiement acquis, numerotee sans
 * rupture, mentions figees a l'emission ({@code editeur}, {@code cabinet} en JSON), PDF conserve tel
 * qu'emis. Jamais modifiee : aucun mutateur apres la creation.
 */
@Entity
@Table(name = "factures")
public class FactureEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero", nullable = false, updatable = false, length = 20)
    private String numero;

    @Column(name = "annee", nullable = false, updatable = false)
    private int annee;

    @Column(name = "rang", nullable = false, updatable = false)
    private int rang;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private Long paymentId;

    @Column(name = "date_emission", nullable = false, updatable = false)
    private LocalDate dateEmission;

    @Column(name = "designation", nullable = false, updatable = false, length = 400)
    private String designation;

    @Column(name = "periode_debut", updatable = false)
    private LocalDate periodeDebut;

    @Column(name = "periode_fin", updatable = false)
    private LocalDate periodeFin;

    @Column(name = "mode_paiement", nullable = false, updatable = false, length = 30)
    private String modePaiement;

    @Column(name = "montant_ht_centimes", nullable = false, updatable = false)
    private long montantHtCentimes;

    @Column(name = "taux_tva", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal tauxTva;

    @Column(name = "montant_tva_centimes", nullable = false, updatable = false)
    private long montantTvaCentimes;

    @Column(name = "montant_ttc_centimes", nullable = false, updatable = false)
    private long montantTtcCentimes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "editeur", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String editeur;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cabinet", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String cabinet;

    @Column(name = "pdf", nullable = false, updatable = false)
    private byte[] pdf;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe = Instant.now();

    protected FactureEntity() {}

    public FactureEntity(String numero, int annee, int rang, UUID workspaceId, Long paymentId, LocalDate dateEmission,
                         String designation, LocalDate periodeDebut, LocalDate periodeFin, String modePaiement,
                         long montantHtCentimes, BigDecimal tauxTva, long montantTvaCentimes, long montantTtcCentimes,
                         String editeur, String cabinet, byte[] pdf) {
        this.numero = numero;
        this.annee = annee;
        this.rang = rang;
        this.workspaceId = workspaceId;
        this.paymentId = paymentId;
        this.dateEmission = dateEmission;
        this.designation = designation;
        this.periodeDebut = periodeDebut;
        this.periodeFin = periodeFin;
        this.modePaiement = modePaiement;
        this.montantHtCentimes = montantHtCentimes;
        this.tauxTva = tauxTva;
        this.montantTvaCentimes = montantTvaCentimes;
        this.montantTtcCentimes = montantTtcCentimes;
        this.editeur = editeur;
        this.cabinet = cabinet;
        this.pdf = pdf;
    }

    public Long getId() { return id; }
    public String getNumero() { return numero; }
    public int getAnnee() { return annee; }
    public int getRang() { return rang; }
    public UUID getWorkspaceId() { return workspaceId; }
    public Long getPaymentId() { return paymentId; }
    public LocalDate getDateEmission() { return dateEmission; }
    public String getDesignation() { return designation; }
    public LocalDate getPeriodeDebut() { return periodeDebut; }
    public LocalDate getPeriodeFin() { return periodeFin; }
    public String getModePaiement() { return modePaiement; }
    public long getMontantHtCentimes() { return montantHtCentimes; }
    public BigDecimal getTauxTva() { return tauxTva; }
    public long getMontantTvaCentimes() { return montantTvaCentimes; }
    public long getMontantTtcCentimes() { return montantTtcCentimes; }
    public String getEditeur() { return editeur; }
    public String getCabinet() { return cabinet; }
    public byte[] getPdf() { return pdf; }
    public Instant getCreeLe() { return creeLe; }
}
