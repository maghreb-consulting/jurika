package ma.jurika.ticket.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Demande de transfert d'un dossier d'un employe a un autre (V9).
 * VOIE A : creee par le responsable courant en EN_ATTENTE, la cible accepte/refuse.
 * VOIE B : transfert direct superviseur, creee directement ACCEPTE ({@code direct=true}).
 */
public record DossierTransfertRequest(
        UUID id,
        UUID workspaceId,
        UUID dossierId,
        UUID fromUserId,
        UUID toUserId,
        TransfertStatut statut,
        boolean direct,
        String motif,
        Instant createdAt,
        Instant decidedAt
) {}
