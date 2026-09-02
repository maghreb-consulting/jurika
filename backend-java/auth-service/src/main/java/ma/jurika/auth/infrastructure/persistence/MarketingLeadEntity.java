package ma.jurika.auth.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 11 TASK 1 — Lead collecté depuis la landing publique jurika.ai
 * via le modal "Demander une démo" (RG-MK01/02/03).
 *
 * Pas de FK workspace : le lead existe AVANT toute création de compte ;
 * la conversion (workspace créé suite à ce lead) est tracée via le
 * champ {@code convertedWorkspaceId} (nullable) renseigné Sprint 11 TASK 6.
 */
@Entity
@Table(name = "marketing_leads")
public class MarketingLeadEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "raison_sociale", nullable = false, length = 150)
    private String raisonSociale;

    @Column(length = 30)
    private String telephone;

    @Column(nullable = false, length = 30)
    private String source = "marketing-site";

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(nullable = false, length = 20)
    private String status = "NEW";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "contacted_at")
    private Instant contactedAt;

    @Column(name = "converted_workspace_id")
    private UUID convertedWorkspaceId;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getRaisonSociale() { return raisonSociale; }
    public void setRaisonSociale(String raisonSociale) { this.raisonSociale = raisonSociale; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getContactedAt() { return contactedAt; }
    public void setContactedAt(Instant contactedAt) { this.contactedAt = contactedAt; }
    public UUID getConvertedWorkspaceId() { return convertedWorkspaceId; }
    public void setConvertedWorkspaceId(UUID convertedWorkspaceId) { this.convertedWorkspaceId = convertedWorkspaceId; }
}
