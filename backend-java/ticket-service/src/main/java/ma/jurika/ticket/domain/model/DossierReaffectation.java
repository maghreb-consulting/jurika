package ma.jurika.ticket.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Lot L1 : trace d'un changement de responsable de dossier (RG-DOS-02, RG-DOS-03,
 * RG-TKT-08 : auteur, date, ancien et nouveau responsable).
 */
public record DossierReaffectation(UUID id, UUID workspaceId, UUID dossierId,
                                   UUID ancienResponsableId, UUID nouveauResponsableId,
                                   NatureReaffectation nature, UUID auteurId, UUID transfertId,
                                   String motif, Instant createdAt) {
}
