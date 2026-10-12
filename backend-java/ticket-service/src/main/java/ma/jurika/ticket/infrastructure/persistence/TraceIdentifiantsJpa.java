package ma.jurika.ticket.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.ticket.domain.port.TraceIdentifiants;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Lot L5 (E7) : reglages de transaction lus par le declencheur {@code tracer_identifiants_dossier}
 * (ticket V41). Locaux a la transaction ({@code set_config(..., true)}) : ils ne debordent
 * jamais sur une autre requete du pool de connexions.
 */
@Repository
public class TraceIdentifiantsJpa implements TraceIdentifiants {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void declarerSaisie(UUID auteurId) {
        em.createNativeQuery("""
                SELECT set_config('app.identifiants_origine', 'SAISIE', true),
                       set_config('app.identifiants_auteur', ?1, true),
                       set_config('app.identifiants_ticket', '', true)
                """)
                .setParameter(1, auteurId == null ? "" : auteurId.toString())
                .getSingleResult();
    }
}
