package ma.jurika.dataroom.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dataroom_settings")
public class SettingsEntity {

    @Id
    @Column(name = "dossier_id")
    private UUID dossierId;
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;
    @Column(name = "access_status", nullable = false, length = 20)
    private String accessStatus;
    @Column(name = "perm_download", nullable = false)
    private boolean permDownload;
    @Column(name = "perm_print", nullable = false)
    private boolean permPrint;
    /** V19 : le client peut-il DEPOSER des documents (depot comptable) ? Defaut false. */
    @Column(name = "perm_depot", nullable = false)
    private boolean permDepot;
    @Column(name = "client_link_token", nullable = false)
    private UUID clientLinkToken;
    @Column(name = "access_count", nullable = false)
    private int accessCount;
    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;
    /** RG-DC27 : email du comptable du cabinet, recoit une notif a chaque upload comptable. */
    @Column(name = "accountant_email", length = 255)
    private String accountantEmail;
    @Column(name = "notify_accountant_on_upload", nullable = false)
    private boolean notifyAccountantOnUpload;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onPersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (accessStatus == null) accessStatus = "ACTIVE";
        if (clientLinkToken == null) clientLinkToken = UUID.randomUUID();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getDossierId() { return dossierId; }
    public void setDossierId(UUID v) { this.dossierId = v; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public String getAccessStatus() { return accessStatus; }
    public void setAccessStatus(String v) { this.accessStatus = v; }
    public boolean isPermDownload() { return permDownload; }
    public void setPermDownload(boolean v) { this.permDownload = v; }
    public boolean isPermPrint() { return permPrint; }
    public void setPermPrint(boolean v) { this.permPrint = v; }
    public boolean isPermDepot() { return permDepot; }
    public void setPermDepot(boolean v) { this.permDepot = v; }
    public UUID getClientLinkToken() { return clientLinkToken; }
    public void setClientLinkToken(UUID v) { this.clientLinkToken = v; }
    public int getAccessCount() { return accessCount; }
    public void setAccessCount(int v) { this.accessCount = v; }
    public Instant getLastAccessedAt() { return lastAccessedAt; }
    public void setLastAccessedAt(Instant v) { this.lastAccessedAt = v; }
    public String getAccountantEmail() { return accountantEmail; }
    public void setAccountantEmail(String v) { this.accountantEmail = v; }
    public boolean isNotifyAccountantOnUpload() { return notifyAccountantOnUpload; }
    public void setNotifyAccountantOnUpload(boolean v) { this.notifyAccountantOnUpload = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
