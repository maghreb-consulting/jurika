package ma.jurika.dataroom.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Un changement de visibilite client, et qui l'a decide (migration V32).
 *
 * <p>Retirer une piece de la vue du client est une decision ; la rendre a nouveau
 * visible en est une autre. Les deux se relisent.
 */
@Entity
@Table(name = "dataroom_visibilite_evenements")
public class VisibiliteEvenementEntity {

    @Id
    private UUID id;
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;
    @Column(name = "document_id", nullable = false)
    private UUID documentId;
    @Column(nullable = false)
    private boolean visible;
    /** DEPOT, DATAROOM ou WORKFLOW — d'ou le geste a ete pose. */
    @Column(nullable = false, length = 20)
    private String origine;
    @Column(name = "acteur_id")
    private UUID acteurId;
    @Column(name = "survenu_le", nullable = false)
    private Instant survenuLe;

    @PrePersist
    void onPersist() {
        if (id == null) id = UUID.randomUUID();
        if (survenuLe == null) survenuLe = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID v) { this.documentId = v; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean v) { this.visible = v; }
    public String getOrigine() { return origine; }
    public void setOrigine(String v) { this.origine = v; }
    public UUID getActeurId() { return acteurId; }
    public void setActeurId(UUID v) { this.acteurId = v; }
    public Instant getSurvenuLe() { return survenuLe; }
    public void setSurvenuLe(Instant v) { this.survenuLe = v; }
}
