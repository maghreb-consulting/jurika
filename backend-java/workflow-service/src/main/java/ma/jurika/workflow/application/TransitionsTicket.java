package ma.jurika.workflow.application;

import java.util.UUID;

/**
 * Lot L4c (E1, RG-TKT-02, 06) : un parcours DEMANDE a ticket-service de faire avancer son
 * ticket ; ticket-service applique la machine d'etats et les controles de passage. Plus
 * aucune ecriture du statut d'un ticket hors de ticket-service.
 */
public interface TransitionsTicket {

    /** Resultat : APPLIQUEE, SANS_OBJET (ticket deja ailleurs) ou REFUSEE (motif nomme). */
    record Reponse(String resultat, String statut, String motif) {}

    Reponse demander(UUID workspaceId, UUID ticketId, String attendu, String cible, String raison, UUID acteurId);
}
