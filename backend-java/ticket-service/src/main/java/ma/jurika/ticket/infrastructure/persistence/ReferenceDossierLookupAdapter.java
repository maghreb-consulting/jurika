package ma.jurika.ticket.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.common.reference.ReferencesLisibles;
import ma.jurika.ticket.domain.port.ReferenceDossierLookup;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Lot L5 (E6) : numero du dossier (V38), filtre workspace explicite en plus de la RLS. */
@Component
public class ReferenceDossierLookupAdapter implements ReferenceDossierLookup {

    @PersistenceContext
    private EntityManager em;

    @Override
    public String reference(UUID workspaceId, UUID dossierId) {
        if (dossierId == null) return null;
        List<?> r = em.createNativeQuery("SELECT numero_annee, numero_ordre, raison_sociale FROM entreprise_dossiers"
                        + " WHERE id = ?1 AND workspace_id = ?2")
                .setParameter(1, dossierId)
                .setParameter(2, workspaceId)
                .getResultList();
        if (r.isEmpty()) return null;
        Object[] ligne = (Object[]) r.get(0);
        return ReferencesLisibles.dossier((Number) ligne[0], (Number) ligne[1], (String) ligne[2]);
    }
}
