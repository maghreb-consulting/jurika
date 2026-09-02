package ma.jurika.dataroom.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Vue read-only sur la table tickets (creee par ticket-service).
 * Sert a afficher l'historique des operations dans le Data Room V2.
 */
@Entity
@Table(name = "tickets")
public class TicketViewEntity {
    @Id
    private UUID id;
    @Column(name = "workspace_id", insertable = false, updatable = false)
    private UUID workspaceId;
    @Column(insertable = false, updatable = false)
    private String reference;
    @Column(insertable = false, updatable = false)
    private String titre;
    @Column(insertable = false, updatable = false)
    private String type;
    @Column(insertable = false, updatable = false)
    private String statut;
    @Column(name = "dossier_id", insertable = false, updatable = false)
    private UUID dossierId;
    @Column(name = "cloture_at", insertable = false, updatable = false)
    private Instant clotureAt;
    // Fiche client (2026-07-14) — date d'ouverture du ticket, utilisee comme
    // « date de debut » d'une operation quand aucune date juridique d'acte n'est
    // stockee dans le workflow.
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
    @Column(insertable = false, updatable = false)
    private String description;

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getReference() { return reference; }
    public String getTitre() { return titre; }
    public String getType() { return type; }
    public String getStatut() { return statut; }
    public UUID getDossierId() { return dossierId; }
    public Instant getClotureAt() { return clotureAt; }
    public Instant getCreatedAt() { return createdAt; }
    public String getDescription() { return description; }
}
