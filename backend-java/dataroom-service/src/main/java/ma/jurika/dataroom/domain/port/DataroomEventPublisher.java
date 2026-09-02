package ma.jurika.dataroom.domain.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 7 / TASK 5.2 -- Port domain pour publier les evenements Data Room
 * (Observer pattern, cf. CLAUDE.md "8 Design Patterns").
 *
 * Producteur cote dataroom-service ; consommateur cote realtime-service
 * (Node.js) qui push WebSocket vers le client connecte au dossier (Socket.io
 * room "dataroom:{dossierId}").
 *
 * Impl par defaut : RabbitDataroomEventPublisher (topic exchange).
 * Test : peut etre remplace par un mock/noop bean.
 */
public interface DataroomEventPublisher {

    /** Kind du payload (string libre dans le payload, enum cote consumer). */
    enum Kind { UPLOADED, DELETED, REPLACED }

    /**
     * Payload du message JSON.
     *
     * @param kind        UPLOADED / DELETED / REPLACED
     * @param workspaceId workspace du dossier (pour filtrage cote realtime)
     * @param dossierId   dossier impacte
     * @param documentId  document (null pour deleteBulk si on aggrege)
     * @param documentType STATUTS, PV_AGE, ... (utile pour le toast cote client)
     * @param title       affichable cote client
     * @param byUserId    auteur de l'action (employe)
     * @param at          horodatage
     */
    record DataroomDocumentEvent(
            Kind kind,
            UUID workspaceId,
            UUID dossierId,
            UUID documentId,
            String documentType,
            String title,
            UUID byUserId,
            Instant at) {}

    void publish(DataroomDocumentEvent event);
}
