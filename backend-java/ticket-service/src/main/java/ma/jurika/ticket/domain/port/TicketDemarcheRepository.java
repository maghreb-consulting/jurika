package ma.jurika.ticket.domain.port;

import ma.jurika.ticket.domain.model.DemarcheEtat;
import ma.jurika.ticket.domain.model.TicketDemarche;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Etat de cochage des demarches, par ticket. */
public interface TicketDemarcheRepository {

    /** Etats existants du ticket, indexes par identifiant de demarche du referentiel. */
    Map<UUID, TicketDemarche> findByTicket(UUID workspaceId, UUID ticketId);

    /**
     * Cree ou met a jour l'etat d'une demarche.
     *
     * @param justificatifs identifiants des documents deposes pour CETTE demarche
     *                      (table {@code ticket_demarche_justificatifs}) ; la liste
     *                      remplace integralement les rattachements precedents.
     */
    UUID upsert(UUID workspaceId, UUID ticketId, UUID demarcheId, DemarcheEtat etat,
                String motif, UUID acteurId, List<JustificatifDepose> justificatifs);

    /** Un document rattache a une demarche : type et groupe d'alternative satisfait. */
    record JustificatifDepose(UUID documentId, String documentType, int alternativeGroupe) {}
}
