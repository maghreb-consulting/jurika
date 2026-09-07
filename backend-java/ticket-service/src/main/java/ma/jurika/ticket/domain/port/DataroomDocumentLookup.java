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

    record DocumentVu(UUID id, UUID dossierId, UUID ticketId, String documentType, String title) {}
}
