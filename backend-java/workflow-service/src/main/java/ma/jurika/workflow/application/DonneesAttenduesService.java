package ma.jurika.workflow.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.common.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lot L3 (regle des variables) : les donnees EXTERNES qu'un acte attend d'un organisme.
 *
 * <ul>
 *   <li>{@link #enregistrer} : appele par ai-service a chaque generation reussie d'un
 *       document du ticket. Les donnees encore manquantes sont reclamees ; celles qui
 *       ne manquent plus sont closes (le document a ete regenere avec elles).</li>
 *   <li>{@link #lister} : les donnees encore attendues, et pour chacune si elle est
 *       REQUE -- presente au magasin du ticket ou a la fiche de la societe --, auquel cas
 *       l'ecran regenere le document.</li>
 * </ul>
 */
@Service
public class DonneesAttenduesService {

    /** Variable externe -> colonne de la fiche societe ou elle arrive (identifiants, RG-VAR-08). */
    static final Map<String, String> COLONNES_FICHE = Map.of(
            "ICE", "ice",
            "IDENTIFIANT_FISCAL", "identifiant_fiscal",
            "IDENTIFIANT_TP", "taxe_professionnelle",
            "CNSS_NUMERO", "cnss",
            "RC_NUMERO", "rc_numero");

    public record Donnee(String variable, String libelle) {}

    public record Attendue(String templateCode, String workflowCode, String variable, String libelle,
                           Instant reclameeLe, boolean recue) {}

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public void enregistrer(UUID workspaceId, UUID ticketId, String workflowCode, String templateCode,
                            List<Donnee> manquantes) {
        TenantContext.set(workspaceId);
        Set<String> encore = manquantes.stream().map(Donnee::variable).collect(Collectors.toSet());
        // Le document vient d'etre regenere : ce qu'il n'attend plus est clos.
        em.createNativeQuery("""
                UPDATE donnees_attendues SET regeneree_le = now()
                WHERE workspace_id = ?1 AND ticket_id = ?2 AND template_code = ?3
                  AND regeneree_le IS NULL AND NOT (variable = ANY (CAST(?4 AS text[])))
                """)
                .setParameter(1, workspaceId).setParameter(2, ticketId).setParameter(3, templateCode)
                .setParameter(4, encore.toArray(new String[0]))
                .executeUpdate();
        for (Donnee d : manquantes) {
            em.createNativeQuery("""
                    INSERT INTO donnees_attendues (workspace_id, ticket_id, workflow_code, template_code, variable, libelle)
                    VALUES (?1, ?2, ?3, ?4, ?5, ?6)
                    ON CONFLICT (workspace_id, ticket_id, template_code, variable)
                    DO UPDATE SET regeneree_le = NULL, libelle = EXCLUDED.libelle,
                                  workflow_code = EXCLUDED.workflow_code
                    """)
                    .setParameter(1, workspaceId).setParameter(2, ticketId).setParameter(3, workflowCode)
                    .setParameter(4, templateCode).setParameter(5, d.variable()).setParameter(6, d.libelle())
                    .executeUpdate();
        }
    }

    @Transactional(readOnly = true)
    public List<Attendue> lister(UUID workspaceId, UUID ticketId) {
        TenantContext.set(workspaceId);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT a.template_code, a.workflow_code, a.variable, a.libelle, a.reclamee_le,
                       EXISTS (SELECT 1 FROM dossier_variables v
                               WHERE v.workspace_id = a.workspace_id AND v.ticket_id = a.ticket_id
                                 AND v.variable = a.variable AND v.boucle IS NULL
                                 AND v.valeur IS NOT NULL AND btrim(v.valeur) <> '') AS au_magasin,
                       t.dossier_id
                FROM donnees_attendues a JOIN tickets t ON t.id = a.ticket_id AND t.workspace_id = a.workspace_id
                WHERE a.workspace_id = ?1 AND a.ticket_id = ?2 AND a.regeneree_le IS NULL
                ORDER BY a.reclamee_le, a.template_code, a.variable
                """).setParameter(1, workspaceId).setParameter(2, ticketId).getResultList();
        List<Attendue> out = new ArrayList<>();
        for (Object[] r : rows) {
            String variable = (String) r[2];
            boolean recue = Boolean.TRUE.equals(r[5]) || (r[6] != null && presenteALaFiche(workspaceId, (UUID) r[6], variable));
            out.add(new Attendue((String) r[0], (String) r[1], variable, (String) r[3], instant(r[4]), recue));
        }
        return out;
    }

    private boolean presenteALaFiche(UUID workspaceId, UUID dossierId, String variable) {
        String colonne = COLONNES_FICHE.get(variable);
        if (colonne == null) return false;
        List<?> r = em.createNativeQuery("SELECT " + colonne + " FROM entreprise_dossiers WHERE id = ?1 AND workspace_id = ?2")
                .setParameter(1, dossierId).setParameter(2, workspaceId).getResultList();
        return !r.isEmpty() && r.get(0) != null && !String.valueOf(r.get(0)).isBlank();
    }

    private static Instant instant(Object o) {
        if (o instanceof Instant i) return i;
        if (o instanceof OffsetDateTime odt) return odt.toInstant();
        if (o instanceof java.sql.Timestamp ts) return ts.toInstant();
        throw new IllegalStateException("Date inattendue : " + o);
    }
}
