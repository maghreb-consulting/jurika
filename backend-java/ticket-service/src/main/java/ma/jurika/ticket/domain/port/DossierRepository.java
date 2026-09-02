package ma.jurika.ticket.domain.port;

import ma.jurika.ticket.domain.model.EntrepriseDossier;

import java.util.Optional;
import java.util.UUID;

public interface DossierRepository {

    EntrepriseDossier save(EntrepriseDossier dossier);

    Optional<EntrepriseDossier> findById(UUID workspaceId, UUID id);

    /**
     * Fix 2026-06-07 (BUG 2 idempotence) — Recherche un dossier VIVANT
     * (EN_CONSTITUTION / ACTIVE / EN_LIQUIDATION) deja porteur du couple
     * (workspaceId, raisonSociale) — case insensitive.
     *
     * Utilise par {@code CreateTicketUseCase} pour eviter la creation
     * d'un dossier en double lors d'un double-submit (React StrictMode
     * ou clic-clic). Retourne {@code Optional.empty()} si aucun dossier
     * vivant n'existe pour ce nom.
     */
    Optional<EntrepriseDossier> findAliveByRaisonSociale(UUID workspaceId, String raisonSociale);

    /**
     * Fix 2026-06-07 (BUG 2) — Trace l'origine d'un dossier auto-cree
     * par un ticket. UPDATE conditionnel : ne marque que si
     * {@code created_by_ticket_id IS NULL} et {@code statut = 'EN_CONSTITUTION'},
     * pour ne pas voler la paternite a un ticket precedent ni marquer un
     * dossier reutilise via idempotence (qui peut etre ACTIVE).
     *
     * @return true si le marquage a effectivement eu lieu.
     */
    boolean markCreatedByTicket(UUID workspaceId, UUID dossierId, UUID ticketId);

    /**
     * V9 (transfert de dossier) — Met a jour l'owner durable du dossier.
     * Scope au workspace (RLS + filtre explicite). {@code responsableId}
     * peut etre null (desassignation). Retourne true si une ligne a ete maj.
     */
    boolean updateResponsable(UUID workspaceId, UUID dossierId, UUID responsableId);
}
