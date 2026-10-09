package ma.jurika.auth.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.auth.domain.port.DroitSuppressionDataroomRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Lot L1 : colonne users.droit_suppression_dataroom (V35), filtre workspace explicite. */
@Repository
public class DroitSuppressionDataroomRepositoryAdapter implements DroitSuppressionDataroomRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public boolean lire(UUID workspaceId, UUID userId) {
        java.util.List<?> r = em.createNativeQuery(
                        "SELECT droit_suppression_dataroom FROM users WHERE id = ?1 AND workspace_id = ?2")
                .setParameter(1, userId)
                .setParameter(2, workspaceId)
                .getResultList();
        return !r.isEmpty() && Boolean.TRUE.equals(r.get(0));
    }

    @Override
    public void definir(UUID workspaceId, UUID userId, boolean accorde) {
        em.createNativeQuery(
                        "UPDATE users SET droit_suppression_dataroom = ?1 WHERE id = ?2 AND workspace_id = ?3")
                .setParameter(1, accorde)
                .setParameter(2, userId)
                .setParameter(3, workspaceId)
                .executeUpdate();
    }
}
