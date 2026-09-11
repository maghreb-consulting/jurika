package ma.jurika.ticket.domain.port;

import java.util.List;
import java.util.UUID;

/**
 * Lecture SEULE des documents de la Data Room, pour verifier cote SERVEUR qu'un
 * justificatif a bien ete televerse avant de cocher une demarche.
 *
 * <p>Aucun appel inter-services : les services partagent une base unique et le
 * ticket-service lit ici une vue en lecture seule, exactement comme il le fait
 * deja pour les utilisateurs et les workspaces (UserViewEntity,
 * WorkspaceViewEntity). Le sens inverse existe aussi (dataroom-service lit
 * `tickets`). Ecrire dans `dataroom_documents` reste, lui, du seul ressort du
 * dataroom-service.
 */
public interface DataroomDocumentLookup {

    /** Documents existants parmi les identifiants fournis, avec leur type et leur ticket. */
    List<DocumentVu> findByIds(UUID workspaceId, List<UUID> documentIds);

    /**
     * Tous les documents EN VIGUEUR rattaches a un ticket. Sert au recapitulatif
     * de cloture, qui doit dire ce qui a ete produit — et, par difference avec le
     * referentiel, ce qui manque.
     */
    List<DocumentVu> findByTicket(UUID workspaceId, UUID ticketId);

    /**
     * @param visibleClient lot B — le client voit-il ce document ? Le recapitulatif
     *                      de cloture le rapporte : remettre un dossier dont des
     *                      pieces restent masquees est une decision, pas un hasard.
     */
    record DocumentVu(UUID id, UUID dossierId, UUID ticketId, String documentType, String title,
                      boolean visibleClient) {
        /** Surcharge de compatibilite : visibilite inconnue, presumee acquise. */
        public DocumentVu(UUID id, UUID dossierId, UUID ticketId, String documentType,
                           String title) {
            this(id, dossierId, ticketId, documentType, title, true);
        }
    }
}
