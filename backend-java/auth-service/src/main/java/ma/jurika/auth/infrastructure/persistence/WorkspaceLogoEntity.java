package ma.jurika.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Projection dédiée au logo du cabinet (papier à en-tête V31, 2026-07-14),
 * mappée sur la table {@code workspaces} mais ne portant QUE les octets du logo.
 *
 * <p>Motivation : ne pas charger ~1 Mo d'image sur les chemins chauds (login,
 * trial…) qui lisent {@link WorkspaceEntity}. Seuls les endpoints logo
 * (upload / download / delete) manipulent cette entité.
 */
@Entity
@Table(name = "workspaces")
public class WorkspaceLogoEntity {

    @Id
    private UUID id;
    @Column(name = "logo_bytes")
    private byte[] logoBytes;
    @Column(name = "logo_content_type", length = 50)
    private String logoContentType;

    public UUID getId() { return id; }
    public byte[] getLogoBytes() { return logoBytes; }
    public void setLogoBytes(byte[] v) { this.logoBytes = v; }
    public String getLogoContentType() { return logoContentType; }
    public void setLogoContentType(String v) { this.logoContentType = v; }
}
