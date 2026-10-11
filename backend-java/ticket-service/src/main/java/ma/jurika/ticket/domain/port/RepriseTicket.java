package ma.jurika.ticket.domain.port;

import ma.jurika.ticket.domain.model.TicketStatut;

import java.util.Optional;
import java.util.UUID;

/**
 * Lot L4a (E4, RG-TKT-03 a 05) : ce que l'annulation conserve et ce que la reprise exige.
 */
public interface RepriseTicket {

    /** A l'annulation : statut atteint et auteur (motif et date sont portes par le ticket). */
    void enregistrerAnnulation(UUID workspaceId, UUID ticketId, TicketStatut statutAnterieur, UUID auteur);

    /**
     * Statut atteint au moment de l'annulation : la colonne, sinon la trace de l'annulation
     * (tickets annules avant L4a) ; vide si aucune des deux ne le dit.
     */
    Optional<TicketStatut> statutAnterieur(UUID workspaceId, UUID ticketId);

    /** Vrai si une demarche de RETRAIT a ete cochee : la reprise est alors impossible. */
    boolean retraitEffectue(UUID workspaceId, UUID ticketId);

    /** A la reprise : le statut anterieur est consomme. */
    void effacer(UUID workspaceId, UUID ticketId);
}
