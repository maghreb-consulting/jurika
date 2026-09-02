package ma.jurika.dataroom.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Ancre JPA read-only sur la table workflow_progress (creee par
 * workflow-service). On n'expose que la PK : les colonnes utiles (workflow_type,
 * statut, completed_at, data jsonb) sont lues via une projection native
 * ({@link WorkflowProgressRow}) qui caste le jsonb en texte, evitant tout mapping
 * de type JSONB cote Hibernate.
 */
@Entity
@Table(name = "workflow_progress")
public class WorkflowProgressViewEntity {
    @Id
    private UUID id;

    public UUID getId() { return id; }
}
