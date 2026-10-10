package ma.jurika.ticket.domain.port;

import ma.jurika.ticket.domain.model.DossierTransfertRequest;
import ma.jurika.ticket.domain.model.DossierTransfertRequestView;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de persistance des demandes de transfert de dossier (V9).
 */
public interface DossierTransfertRequestRepository {

    DossierTransfertRequest save(DossierTransfertRequest req);

    Optional<DossierTransfertRequest> findById(UUID workspaceId, UUID id);

    /** Garde-fou anti-doublon : une demande EN_ATTENTE existe-t-elle deja pour ce dossier ? */
    boolean existsPendingForDossier(UUID workspaceId, UUID dossierId);

    /** Lot L1 : la demande EN_ATTENTE du dossier, s'il y en a une. */
    java.util.Optional<DossierTransfertRequest> findPendingForDossier(UUID workspaceId, UUID dossierId);

    /** Demandes EN_ATTENTE recues par {@code toUserId} (la cible accepte/refuse). */
    List<DossierTransfertRequestView> listInbox(UUID workspaceId, UUID toUserId);

    /** Demandes EN_ATTENTE emises par {@code fromUserId} (l'initiateur peut annuler). */
    List<DossierTransfertRequestView> listOutbox(UUID workspaceId, UUID fromUserId);
}
