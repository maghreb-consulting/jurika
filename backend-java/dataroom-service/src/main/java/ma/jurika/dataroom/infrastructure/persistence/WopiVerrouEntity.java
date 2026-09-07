package ma.jurika.dataroom.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Un verrou d'édition bureautique (WOPI Lock).
 *
 * <p>Lot 4 (2026-09-07). Au lot 3, deux employés pouvaient ouvrir le même acte
 * : chacun éditait la copie chargée dans son navigateur, et le dernier à
 * enregistrer écrasait le travail du premier. Sans erreur ni message — la
 * version juridique finale ne portait qu'une des deux séries de corrections, et
 * rien ne disait laquelle avait disparu.
 *
 * <p>Le document est la clé primaire : « au plus un verrou vivant par
 * document » est un invariant, il est tenu par la base et non par une
 * vérification applicative que deux requêtes concurrentes pourraient enjamber.
 */
@Entity
@Table(name = "dataroom_wopi_verrous")
public class WopiVerrouEntity {

    /** Le document verrouillé — et l'invariant « un seul verrou ». */
    @Id
    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    /**
     * L'identifiant choisi par l'éditeur, rendu tel quel dans {@code X-WOPI-Lock}.
     * Il est opaque pour nous : on le compare, on ne l'interprète pas.
     */
    @Column(name = "lock_id", nullable = false, length = 1024)
    private String lockId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Le nom du détenteur. « Document en cours d'édition » sans dire par qui
     * n'aide personne à décrocher son téléphone.
     */
    @Column(name = "user_display_name", length = 200)
    private String userDisplayName;

    @Column(name = "acquis_at", nullable = false)
    private Instant acquisAt;

    @Column(name = "expire_at", nullable = false)
    private Instant expireAt;

    @PrePersist
    void prePersist() {
        if (acquisAt == null) acquisAt = Instant.now();
    }

    /**
     * Le verrou tient-il encore ? Un navigateur qui plante ne relâche rien :
     * passé cette date, le verrou est reprenable. Sans quoi un acte resterait
     * bloqué jusqu'à une intervention manuelle — pire que le risque couvert.
     */
    public boolean vivant(Instant maintenant) {
        return maintenant.isBefore(expireAt);
    }

    /** Le verrou appartient-il à cet identifiant de verrou ? */
    public boolean detenuPar(String candidat) {
        return lockId.equals(candidat);
    }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID v) { this.documentId = v; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public String getLockId() { return lockId; }
    public void setLockId(String v) { this.lockId = v; }
    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID v) { this.sessionId = v; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID v) { this.userId = v; }
    public String getUserDisplayName() { return userDisplayName; }
    public void setUserDisplayName(String v) { this.userDisplayName = v; }
    public Instant getAcquisAt() { return acquisAt; }
    public void setAcquisAt(Instant v) { this.acquisAt = v; }
    public Instant getExpireAt() { return expireAt; }
    public void setExpireAt(Instant v) { this.expireAt = v; }
}
