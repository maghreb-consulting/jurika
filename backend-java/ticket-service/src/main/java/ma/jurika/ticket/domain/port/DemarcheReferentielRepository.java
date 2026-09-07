package ma.jurika.ticket.domain.port;

import ma.jurika.ticket.domain.model.Demarche;
import ma.jurika.ticket.domain.model.TicketStatut;

import java.util.List;
import java.util.Optional;

/**
 * Lecture du referentiel des demarches. Referentiel GLOBAL (pas de
 * workspace_id) : c'est une donnee de reference, identique pour tous les
 * cabinets, rechargee par migration a chaque version du guide.
 */
public interface DemarcheReferentielRepository {

    /** Toutes les demarches d'un workflow, dans l'ordre du guide. */
    List<Demarche> findByWorkflow(String workflowType);

    /** Les demarches cochables pendant un statut donne, dans l'ordre du guide. */
    List<Demarche> findByWorkflowAndStatut(String workflowType, TicketStatut statut);

    Optional<Demarche> findByWorkflowAndOrdre(String workflowType, int ordre);
}
