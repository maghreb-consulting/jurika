package ma.jurika.ticket.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.ticket.domain.port.PermissionsClientLookup;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Lot L1 : lecture de dataroom_settings.perm_consultation (dataroom V34), filtre
 * workspace explicite en plus de la RLS. Aucun reglage enregistre : la valeur par
 * defaut de la colonne (TRUE) s'applique, comme dans dataroom-service.
 */
@Component
public class PermissionsClientLookupAdapter implements PermissionsClientLookup {

    @PersistenceContext
    private EntityManager em;

    @Override
    public boolean consultationPermise(UUID workspaceId, UUID dossierId) {
        List<?> r = em.createNativeQuery("""
                        SELECT perm_consultation AND access_status = 'ACTIVE' FROM dataroom_settings
                         WHERE dossier_id = ?1 AND workspace_id = ?2
                        """)
                .setParameter(1, dossierId)
                .setParameter(2, workspaceId)
                .getResultList();
        return r.isEmpty() || Boolean.TRUE.equals(r.get(0));
    }
}
