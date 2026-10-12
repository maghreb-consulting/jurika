package ma.jurika.ticket.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.ticket.domain.port.PermissionsClientLookup;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Lot L1 : lecture de dataroom_settings (dataroom V6, V34), filtre workspace explicite en
 * plus de la RLS. Aucun reglage enregistre : la valeur par defaut des colonnes (TRUE pour la
 * consultation et le telechargement) s'applique, comme dans dataroom-service.
 */
@Component
public class PermissionsClientLookupAdapter implements PermissionsClientLookup {

    @PersistenceContext
    private EntityManager em;

    @Override
    public boolean consultationPermise(UUID workspaceId, UUID dossierId) {
        return lire("perm_consultation AND access_status = 'ACTIVE'", workspaceId, dossierId);
    }

    @Override
    public boolean telechargementPermis(UUID workspaceId, UUID dossierId) {
        return lire("perm_consultation AND perm_download AND access_status = 'ACTIVE'", workspaceId, dossierId);
    }

    private boolean lire(String expression, UUID workspaceId, UUID dossierId) {
        List<?> r = em.createNativeQuery("SELECT " + expression + " FROM dataroom_settings"
                        + " WHERE dossier_id = ?1 AND workspace_id = ?2")
                .setParameter(1, dossierId)
                .setParameter(2, workspaceId)
                .getResultList();
        return r.isEmpty() || Boolean.TRUE.equals(r.get(0));
    }
}
