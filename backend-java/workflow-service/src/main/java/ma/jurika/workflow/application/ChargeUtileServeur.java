package ma.jurika.workflow.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.common.exception.NotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lot L3 (P2, RG-VAR-05) : la charge utile de la creation, construite par le SERVEUR
 * depuis le magasin, pour ai-service. Le navigateur ne la construit plus : une
 * correction faite au magasin se repercute sur toutes les generations suivantes.
 *
 * <p>Acces : l'employe en charge du ticket (responsable du dossier ; a defaut,
 * assigne ou createur du ticket), dans son workspace (filtre SQL et RLS).
 */
@Service
public class ChargeUtileServeur {

    private final MagasinVariables magasin;

    @PersistenceContext
    private EntityManager em;

    public ChargeUtileServeur(MagasinVariables magasin) {
        this.magasin = magasin;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> creation(UUID workspaceId, UUID ticketId, UUID employeId) {
        exigerEnCharge(workspaceId, ticketId, employeId);
        Map<String, Object> charge = new LinkedHashMap<>(
                ConstructeurChargeUtileCreation.construire(magasin.lirePourGeneration(workspaceId, ticketId)));
        charge.put("ticketId", ticketId.toString());
        return charge;
    }

    private void exigerEnCharge(UUID workspaceId, UUID ticketId, UUID employeId) {
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
            throw new AccessDeniedException("Seul l'employe en charge du ticket genere ses documents");
        }
    }
}
