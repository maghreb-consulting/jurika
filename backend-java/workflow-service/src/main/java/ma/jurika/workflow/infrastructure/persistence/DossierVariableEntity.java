package ma.jurika.workflow.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Une variable du magasin du dossier — migration {@code V13}.
 *
 * <p>Une ligne = une variable, une valeur, une provenance. Les deux index
 * uniques partiels de la migration interdisent qu'il y en ait deux pour le même
 * nom sur le même ticket : la décision 2 du cabinet (« une variable se saisit
 * une seule fois ») est tenue par la base, pas par une convention d'appel.
 *
 * <p>{@link #boucle} et {@link #rang} sont renseignés ensemble ou pas du tout —
 * une occurrence de boucle porte son rang, une variable simple n'en a pas.
 */
@Entity
@Table(name = "dossier_variables")
public class DossierVariableEntity {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    /** {@code null} tant que le dossier n'existe pas (création en cours). */
    @Column(name = "dossier_id")
    private UUID dossierId;

    /** Le nom SANS le {@code $}. */
    @Column(nullable = false, length = 80)
    private String variable;

    @Column(length = 60)
    private String boucle;

    private Short rang;

    @Column(columnDefinition = "text")
    private String valeur;

    /** {@code SAISIE} | {@code BASE} | {@code DERIVEE}. */
    @Column(nullable = false, length = 10)
    private String origine;

    @Column(name = "saisie_par_id")
    private UUID saisieParId;

    @Column(name = "saisie_le", nullable = false)
    private Instant saisieLe;

    @Column(length = 60)
    private String occasion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onPersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (saisieLe == null) saisieLe = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public UUID getTicketId() { return ticketId; }
    public void setTicketId(UUID v) { this.ticketId = v; }
    public UUID getDossierId() { return dossierId; }
    public void setDossierId(UUID v) { this.dossierId = v; }
    public String getVariable() { return variable; }
    public void setVariable(String v) { this.variable = v; }
    public String getBoucle() { return boucle; }
    public void setBoucle(String v) { this.boucle = v; }
    public Short getRang() { return rang; }
    public void setRang(Short v) { this.rang = v; }
    public String getValeur() { return valeur; }
    public void setValeur(String v) { this.valeur = v; }
    public String getOrigine() { return origine; }
    public void setOrigine(String v) { this.origine = v; }
    public UUID getSaisieParId() { return saisieParId; }
    public void setSaisieParId(UUID v) { this.saisieParId = v; }
    public Instant getSaisieLe() { return saisieLe; }
    public void setSaisieLe(Instant v) { this.saisieLe = v; }
    public String getOccasion() { return occasion; }
    public void setOccasion(String v) { this.occasion = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
