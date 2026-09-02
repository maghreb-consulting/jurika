package ma.jurika.dataroom.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

/**
 * Projection native d'une ligne workflow_progress pour la Fiche client. La
 * colonne {@code data} (jsonb) est castee en texte ({@code data::text}) et
 * parsee cote application (Jackson) pour extraire, selon le type de workflow, le
 * sous-type precis (ex. MODIFICATION -> selectedTypes) et la date juridique de
 * l'acte (ex. datePV).
 */
public interface WorkflowProgressRow {
    UUID getTicketId();
    String getWorkflowType();
    String getStatut();
    Instant getCompletedAt();
    String getDataJson();
}
