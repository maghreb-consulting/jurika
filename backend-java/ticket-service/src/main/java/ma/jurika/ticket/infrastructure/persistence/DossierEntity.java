package ma.jurika.ticket.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "entreprise_dossiers")
public class DossierEntity {

    @Id
    private UUID id;
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;
    @Column(name = "raison_sociale", nullable = false, length = 200)
    private String raisonSociale;
    @Column(name = "forme_juridique", nullable = false, length = 20)
    private String formeJuridique;
    @Column(length = 20)
    private String ice;
    @Column(name = "rc_numero", length = 50)
    private String rcNumero;
    @Column(name = "rc_tribunal", length = 100)
    private String rcTribunal;
    @Column(name = "identifiant_fiscal", length = 50)
    private String identifiantFiscal;
    @Column(name = "taxe_professionnelle", length = 50)
    private String taxeProfessionnelle;
    @Column(length = 50)
    private String cnss;
    @Column(name = "adresse_siege", columnDefinition = "TEXT")
    private String adresseSiege;
    @Column(length = 100)
    private String ville;
    @Column(name = "capital_social_mad", precision = 15, scale = 2)
    private BigDecimal capitalSocialMad;
    @Column(name = "date_constitution")
    private LocalDate dateConstitution;
    @Column(nullable = false, length = 30)
    private String statut;
    @Column(name = "client_id")
    private UUID clientId;
    /** V9 — owner durable du dossier (employe qui gere la societe). */
    @Column(name = "responsable_id")
    private UUID responsableId;
    /**
     * Fix 2026-06-07 (BUG 2) — Ticket d'origine pour un dossier
     * auto-cree par CreateTicketUseCase (types CREATION/IMPORT).
     * NULL si le dossier preexistait ou a ete reutilise via idempotence.
     * Permet a TransitionTicketUseCase (annulation -> ANNULE) de decider
     * si le dataroom associe doit etre supprime ou conserve.
     */
    @Column(name = "created_by_ticket_id")
    private UUID createdByTicketId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onPersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (statut == null) statut = "EN_CONSTITUTION";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public String getRaisonSociale() { return raisonSociale; }
    public void setRaisonSociale(String v) { this.raisonSociale = v; }
    public String getFormeJuridique() { return formeJuridique; }
    public void setFormeJuridique(String v) { this.formeJuridique = v; }
    public String getIce() { return ice; }
    public void setIce(String v) { this.ice = v; }
    public String getRcNumero() { return rcNumero; }
    public void setRcNumero(String v) { this.rcNumero = v; }
    public String getRcTribunal() { return rcTribunal; }
    public void setRcTribunal(String v) { this.rcTribunal = v; }
    public String getIdentifiantFiscal() { return identifiantFiscal; }
    public void setIdentifiantFiscal(String v) { this.identifiantFiscal = v; }
    public String getTaxeProfessionnelle() { return taxeProfessionnelle; }
    public void setTaxeProfessionnelle(String v) { this.taxeProfessionnelle = v; }
    public String getCnss() { return cnss; }
    public void setCnss(String v) { this.cnss = v; }
    public String getAdresseSiege() { return adresseSiege; }
    public void setAdresseSiege(String v) { this.adresseSiege = v; }
    public String getVille() { return ville; }
    public void setVille(String v) { this.ville = v; }
    public BigDecimal getCapitalSocialMad() { return capitalSocialMad; }
    public void setCapitalSocialMad(BigDecimal v) { this.capitalSocialMad = v; }
    public LocalDate getDateConstitution() { return dateConstitution; }
    public void setDateConstitution(LocalDate v) { this.dateConstitution = v; }
    public String getStatut() { return statut; }
    public void setStatut(String v) { this.statut = v; }
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID v) { this.clientId = v; }
    public UUID getResponsableId() { return responsableId; }
    public void setResponsableId(UUID v) { this.responsableId = v; }
    public UUID getCreatedByTicketId() { return createdByTicketId; }
    public void setCreatedByTicketId(UUID v) { this.createdByTicketId = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
