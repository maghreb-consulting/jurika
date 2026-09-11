package ma.jurika.ticket.domain.port;

import ma.jurika.ticket.domain.model.DemarcheEvenement;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le journal des gestes poses sur les demarches d'un ticket.
 *
 * <p>Il n'expose AUCUNE suppression ni mise a jour : un journal qu'on peut
 * reecrire ne prouve rien. La seule facon d'en effacer une ligne est de
 * supprimer la demarche elle-meme, en cascade.
 */
public interface DemarcheJournalRepository {

    /** Ajoute un evenement. Rend son identifiant. */
    UUID enregistrer(UUID workspaceId, UUID ticketDemarcheId, DemarcheEvenement.Type type,
                     String motif, UUID acteurId, int justificatifs);

    /** Le journal de chaque demarche citee, du plus ancien au plus recent. */
    Map<UUID, List<DemarcheEvenement>> parDemarche(UUID workspaceId,
                                                   Collection<UUID> ticketDemarcheIds);
}
