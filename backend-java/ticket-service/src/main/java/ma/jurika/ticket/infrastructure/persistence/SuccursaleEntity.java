package ma.jurika.ticket.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Succursale persistee (2026-07-05). Cree a la completion des workflows
 * SUCCURSALE_MA / SUCCURSALE_ETR (INSERT natif depuis workflow-service) et
 * passe a {@code FERMEE} a la completion de FERMETURE_SUCCURSALE.
 *
 * <p>Appartient a ticket-service (meme base que entreprise_dossiers). Sert la
 * liste des succursales d'une societe mere pour l'auto-remplissage a la
 * fermeture.
 */
@Entity
@Table(name = "succursales")
public class SuccursaleEntity {

    @Id
    private UUID id;
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;
    @Column(name = "parent_dossier_id", nullable = false)
    private UUID parentDossierId;
    /** 'MA' | 'ETR'. */
    @Column(nullable = false, length = 10)
    private String type;
    @Column(length = 200)
    private String denomination;
    @Column(columnDefinition = "TEXT")
    private String activite;
    @Column(columnDefinition = "TEXT")
    private String adresse;
    @Column(length = 100)
    private String ville;
    @Column(name = "rc_secondaire", length = 50)
    private String rcSecondaire;
    @Column(name = "directeur_nom", length = 120)
    private String directeurNom;
    @Column(name = "directeur_prenom", length = 120)
    private String directeurPrenom;
    @Column(name = "directeur_cin", length = 30)
    private String directeurCin;
    /** ETR uniquement (NULL pour MA). */
    @Column(name = "pays_origine", length = 100)
    private String paysOrigine;
    /** 'ACTIVE' | 'FERMEE'. */
    @Column(nullable = false, length = 10)
    private String statut;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "closed_at")
    private Instant closedAt;

    @PrePersist
    void onPersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (statut == null) statut = "ACTIVE";
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public UUID getParentDossierId() { return parentDossierId; }
    public void setParentDossierId(UUID v) { this.parentDossierId = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getDenomination() { return denomination; }
    public void setDenomination(String v) { this.denomination = v; }
    public String getActivite() { return activite; }
    public void setActivite(String v) { this.activite = v; }
    public String getAdresse() { return adresse; }
    public void setAdresse(String v) { this.adresse = v; }
    public String getVille() { return ville; }
    public void setVille(String v) { this.ville = v; }
    public String getRcSecondaire() { return rcSecondaire; }
    public void setRcSecondaire(String v) { this.rcSecondaire = v; }
    public String getDirecteurNom() { return directeurNom; }
    public void setDirecteurNom(String v) { this.directeurNom = v; }
    public String getDirecteurPrenom() { return directeurPrenom; }
    public void setDirecteurPrenom(String v) { this.directeurPrenom = v; }
    public String getDirecteurCin() { return directeurCin; }
    public void setDirecteurCin(String v) { this.directeurCin = v; }
    public String getPaysOrigine() { return paysOrigine; }
    public void setPaysOrigine(String v) { this.paysOrigine = v; }
    public String getStatut() { return statut; }
    public void setStatut(String v) { this.statut = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant v) { this.closedAt = v; }
}
