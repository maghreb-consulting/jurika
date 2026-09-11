package ma.jurika.ticket.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemarcheReferentielJpaRepository extends JpaRepository<DemarcheReferentielEntity, UUID> {

    /**
     * Lot B — {@code actif} filtre les lignes RETIREES du parcours. La migration
     * V24 en conserve une par ancienne ligne encore cochee sur un ticket : leur
     * trace ne se jette pas, mais elles n'appartiennent plus au parcours et ne
     * doivent apparaitre ni a l'ecran, ni dans un decompte d'avancement.
     */
    List<DemarcheReferentielEntity> findByWorkflowTypeAndActifIsTrueOrderByOrdreAsc(
            String workflowType);

    List<DemarcheReferentielEntity> findByWorkflowTypeAndStatutTicketAndActifIsTrueOrderByOrdreAsc(
            String workflowType, String statutTicket);

    Optional<DemarcheReferentielEntity> findByWorkflowTypeAndOrdreAndActifIsTrue(
            String workflowType, Short ordre);

    /** Sans filtre : sert au rattachement d'un cochage porte par une ligne retiree. */
    Optional<DemarcheReferentielEntity> findByWorkflowTypeAndOrdre(String workflowType, Short ordre);
}
