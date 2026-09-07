package ma.jurika.dataroom.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Une séance d'édition bureautique (Collabora / WOPI).
 *
 * <p>Lot 3 (2026-09-07). La séance porte tout le contrôle d'accès des points
 * d'entrée WOPI : ceux-ci sont ouverts au niveau Spring Security (Collabora ne
 * présente aucun JWT) et c'est ici que se trouve l'autorité. Le {@code fileId}
 * de l'URL n'en porte aucune — il est comparé à celui de la séance.
 */
@Entity
@Table(name = "dataroom_wopi_sessions")
public class WopiSessionEntity {

    @Id
    private UUID id;

    /**
     * Empreinte SHA-256 du jeton, en hexadécimal. Le jeton lui-même n'est
     * jamais persisté : il voyage dans une URL, donc dans les journaux de
     * Collabora et l'historique du navigateur.
     */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_display_name", length = 200)
    private String userDisplayName;

    /** Reflet du RBAC réel : un rôle en lecture seule ouvre l'éditeur en lecture seule. */
    @Column(name = "can_write", nullable = false)
    private boolean canWrite;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Fermeture explicite de l'éditeur ; la lecture est coupée aussitôt. */
    @Column(name = "closed_at")
    private Instant closedAt;

    /**
     * Collabora enregistre de façon asynchrone et peut appeler PutFile après la
     * fermeture de l'onglet. Au-delà de cet instant seulement, plus aucune
     * écriture n'est acceptée.
     */
    @Column(name = "grace_jusqua")
    private Instant graceJusqua;

    /**
     * Version juridique créée par CETTE séance, s'il y en a une. Les PutFile
     * suivants la mettent à jour : une séance d'édition produit UNE version, pas
     * une par enregistrement automatique.
     */
    @Column(name = "version_document_id")
    private UUID versionDocumentId;

    @Column(name = "last_put_at")
    private Instant lastPutAt;

    @Column(name = "put_count", nullable = false)
    private int putCount;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    /** La séance accepte-t-elle encore une LECTURE ? */
    public boolean lectureAutorisee(Instant maintenant) {
        return closedAt == null && maintenant.isBefore(expiresAt);
    }

    /**
     * La séance accepte-t-elle encore une ÉCRITURE ? Une séance fermée reste
     * ouverte à l'écriture jusqu'à la fin de sa fenêtre de grâce — sans quoi la
     * dernière sauvegarde de Collabora se perdrait en silence.
     */
    public boolean ecritureAutorisee(Instant maintenant) {
        if (!canWrite) return false;
        if (closedAt != null) {
            return graceJusqua != null && maintenant.isBefore(graceJusqua);
        }
        return maintenant.isBefore(expiresAt);
    }

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String v) { this.tokenHash = v; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID v) { this.documentId = v; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID v) { this.userId = v; }
    public String getUserDisplayName() { return userDisplayName; }
    public void setUserDisplayName(String v) { this.userDisplayName = v; }
    public boolean isCanWrite() { return canWrite; }
    public void setCanWrite(boolean v) { this.canWrite = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant v) { this.expiresAt = v; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant v) { this.closedAt = v; }
    public Instant getGraceJusqua() { return graceJusqua; }
    public void setGraceJusqua(Instant v) { this.graceJusqua = v; }
    public UUID getVersionDocumentId() { return versionDocumentId; }
    public void setVersionDocumentId(UUID v) { this.versionDocumentId = v; }
    public Instant getLastPutAt() { return lastPutAt; }
    public void setLastPutAt(Instant v) { this.lastPutAt = v; }
    public int getPutCount() { return putCount; }
    public void setPutCount(int v) { this.putCount = v; }
}
