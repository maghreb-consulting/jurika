package ma.jurika.workflow.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.common.exception.NotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Lot L3 : l'employe en charge d'un ticket -- le responsable du dossier ; a defaut de
 * dossier, l'assigne ou le createur du ticket (regle de L1). Ticket hors du workspace :
 * 404 ; autre employe : 403.
 */
@Component
public class EmployeEnCharge {

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public void exiger(UUID workspaceId, UUID ticketId, UUID employeId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT t.assigne_id, t.cree_par_id, d.responsable_id
                FROM tickets t LEFT JOIN entreprise_dossiers d
                  ON d.id = t.dossier_id AND d.workspace_id = t.workspace_id
                WHERE t.id = ?1 AND t.workspace_id = ?2
                """).setParameter(1, ticketId).setParameter(2, workspaceId).getResultList();
        if (rows.isEmpty()) {
            throw new NotFoundException("Ticket inconnu");
        }
        Object[] r = rows.get(0);
        String employe = String.valueOf(employeId);
        boolean enCharge = r[2] != null
                ? employe.equals(String.valueOf(r[2]))
                : employe.equals(String.valueOf(r[0])) || employe.equals(String.valueOf(r[1]));
        if (!enCharge) {
            throw new AccessDeniedException("Seul l'employé en charge du ticket peut le faire.");
        }
    }
}
