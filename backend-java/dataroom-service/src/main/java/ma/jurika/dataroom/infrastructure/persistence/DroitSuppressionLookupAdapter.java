package ma.jurika.dataroom.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.dataroom.application.DroitSuppressionLookup;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Lot L1 : lecture du droit de suppression dans la table partagee {@code users}
 * (auth V35), filtre workspace explicite en plus de la RLS. Compte inconnu : pas de droit.
 */
@Component
public class DroitSuppressionLookupAdapter implements DroitSuppressionLookup {

    @PersistenceContext
    private EntityManager em;

    @Override
    public boolean aLeDroit(UUID workspaceId, UUID userId) {
        java.util.List<?> r = em.createNativeQuery("""
                        SELECT droit_suppression_dataroom FROM users
                         WHERE id = ?1 AND workspace_id = ?2 AND role = 'EMPLOYE'
                        """)
                .setParameter(1, userId)
                .setParameter(2, workspaceId)
                .getResultList();
        return !r.isEmpty() && Boolean.TRUE.equals(r.get(0));
    }
}
