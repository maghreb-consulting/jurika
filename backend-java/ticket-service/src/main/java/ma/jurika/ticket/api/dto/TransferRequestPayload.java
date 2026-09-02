package ma.jurika.ticket.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Corps d'une creation de demande de transfert (VOIE A) ou d'un transfert
 * direct superviseur (VOIE B) : le destinataire + un motif optionnel.
 */
public record TransferRequestPayload(
        @NotNull UUID toUserId,
        @Size(max = 1000) String motif
) {}
