package ma.jurika.ticket.api.dto;

import ma.jurika.ticket.domain.model.DossierTransfertRequest;
import ma.jurika.ticket.domain.model.DossierTransfertRequestView;

import java.time.Instant;
import java.util.UUID;

/**
 * Vue d'une demande de transfert pour le frontend. {@code raisonSociale} est
 * renseigne pour les listes inbox/outbox (panneau "Transferts en attente").
 */
public record DossierTransferDto(
        UUID id,
        UUID dossierId,
        String raisonSociale,
        UUID fromUserId,
        UUID toUserId,
        String statut,
        boolean direct,
        String motif,
        Instant createdAt,
        Instant decidedAt
) {
    public static DossierTransferDto from(DossierTransfertRequest r) {
        return from(r, null);
    }

    public static DossierTransferDto from(DossierTransfertRequest r, String raisonSociale) {
        return new DossierTransferDto(
                r.id(), r.dossierId(), raisonSociale, r.fromUserId(), r.toUserId(),
                r.statut().name(), r.direct(), r.motif(), r.createdAt(), r.decidedAt());
    }

    public static DossierTransferDto from(DossierTransfertRequestView v) {
        return from(v.request(), v.raisonSociale());
    }
}
