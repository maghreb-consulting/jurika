package ma.jurika.dataroom.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.common.audit.Auditable;
import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Fix 2026-06-07 (BUG 3) -- Suppression complete d'un dataroom.
 *
 * <p>Difference avec :
 * <ul>
 *   <li><b>Suspension</b> ({@link DataroomSettingsService#toggleSuspension}) :
 *       reversible, garde toutes les donnees, bloque seulement l'acces client.</li>
 *   <li><b>Retrait acces client</b>
 *       ({@code RemoveClientAccessUseCase} auth-service) : detache uniquement le
 *       compte client, le dataroom et ses documents restent intacts.</li>
 *   <li><b>Suppression</b> (ce use case) : destructif. Supprime
 *       {@code dataroom_settings} (cascade -> documents juridiques, comptables,
 *       fiscaux, demandes client, snapshots, access-log, exercices, alertes
 *       echeances). Le dossier {@code entreprise_dossiers} est conserve mais
 *       passe en {@code RADIE} si des references historiques subsistent
 *       (tickets, audit_log) -- ce qui est le cas en general, l'integrite
 *       referentielle est preservee. Si aucun ticket ne reference le dossier,
 *       il est DELETE pour ne pas polluer la liste des dossiers.</li>
 * </ul>
 *
 * <p>RBAC : EMPLOYE ou SUPERVISEUR (controle dans le controller).
 *
 * <p>Securite : workspace-scoped via {@code WHERE workspace_id = ?}.
 *
 * <p>Idempotent : si le dossier ou le dataroom_settings n'existent plus,
 * retourne {@code alreadyDeleted=true} sans lever d'exception (le controller
 * decide alors de repondre 204 No Content).
 *
 * <p>Refuse (409 Conflict) si un ticket ACTIF (statut NOUVEAU ou EN_COURS)
 * reference encore le dossier. L'employe doit d'abord cloturer ou annuler
 * les tickets ouverts.
 *
 * <p>Audit : action {@code DATAROOM_DELETED}, resourceType {@code dossier}.
 */
@Service
public class DeleteDataroomUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteDataroomUseCase.class);

    @PersistenceContext
    private EntityManager em;

    public record Command(UUID workspaceId, UUID dossierId,
                           UUID deletedBy, String ipAddress, String userAgent) {}

    public record Result(UUID dossierId, boolean alreadyDeleted,
                          boolean dossierDeleted, boolean dossierRadied,
                          String raisonSociale) {}

    @Transactional
    @Auditable(action = "DATAROOM_DELETED", resourceType = "dossier", resourceIdExpr = "#cmd.dossierId()")
    public Result execute(Command cmd) {
        // Lot L0 (E15, W3) : le workspace courant n'est plus pose ici. Pose dans
        // le corps, il arrivait apres l'ouverture de la transaction (trop tard
        // pour la RLS) et restait sur le fil. L'appelant le pose avant :
        // JwtAuthFilter en HTTP, l'ecouteur d'annulation (W2) sinon.

        // 1) Verifier l'existence du dossier dans le workspace.
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT raison_sociale, statut FROM entreprise_dossiers
                 WHERE id = ?1 AND workspace_id = ?2
                """)
                .setParameter(1, cmd.dossierId())
                .setParameter(2, cmd.workspaceId())
                .getResultList();
        if (rows.isEmpty()) {
            // Idempotence : dossier deja supprime / inexistant -> 204 cote controller.
            log.info("DeleteDataroom : dossier {} introuvable dans workspace {} (idempotent)",
                    cmd.dossierId(), cmd.workspaceId());
            return new Result(cmd.dossierId(), true, false, false, null);
        }
        Object[] row = rows.get(0);
        String raisonSociale = String.valueOf(row[0]);
        String statut = row[1] == null ? null : row[1].toString();
        if ("RADIE".equals(statut)) {
            // Idempotence forte : un dossier deja RADIE n'a plus de dataroom_settings
            // (cascade DELETE applique au 1er passage). On retourne alreadyDeleted=true.
            // Verification : si dataroom_settings existe encore (RADIE legacy hors
            // de ce flow), on continue pour nettoyer.
            Number remaining = (Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM dataroom_settings WHERE dossier_id = ?1 AND workspace_id = ?2")
                    .setParameter(1, cmd.dossierId())
                    .setParameter(2, cmd.workspaceId())
                    .getSingleResult();
            if (remaining.intValue() == 0) {
                log.info("DeleteDataroom : dossier {} ({}) deja RADIE et purge (idempotent)",
                        cmd.dossierId(), raisonSociale);
                return new Result(cmd.dossierId(), true, false, true, raisonSociale);
            }
        }

        // 2) Refus si un ticket ACTIF (NOUVEAU/EN_COURS) reference encore le dossier.
        //    Les tickets CLOTURE / ANNULE ne bloquent pas (historique).
        @SuppressWarnings("unchecked")
        List<Object[]> activeTickets = em.createNativeQuery("""
                SELECT id, reference, statut FROM tickets
                 WHERE dossier_id = ?1 AND workspace_id = ?2
                   AND statut IN ('CREATION_TICKET','GENERATION_DOCUMENTS','DEROULEMENT_DEMARCHE')
                 LIMIT 5
                """)
                .setParameter(1, cmd.dossierId())
                .setParameter(2, cmd.workspaceId())
                .getResultList();
        if (!activeTickets.isEmpty()) {
            String refs = activeTickets.stream()
                    .map(t -> String.valueOf(t[1]))
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            throw new ConflictException(
                    "Impossible de supprimer ce dataroom : " + activeTickets.size()
                    + " ticket(s) actif(s) y est/sont rattache(s) (" + refs
                    + "). Cloturez ou annulez-les d'abord.");
        }

        // 3) Suppression EXPLICITE de toutes les tables filles ratachees au dossier.
        //    En base, toutes les FK pointent vers entreprise_dossiers ON DELETE CASCADE
        //    (V5 dataroom_documents/demandes/snapshots, V6 dataroom_settings,
        //    V11 access_log, V20 depots). Mais on n'a pas
        //    forcement le droit de DELETE entreprise_dossiers (cf etape 4 : RADIE
        //    si des tickets historiques referencent encore le dossier). On nettoie
        //    donc explicitement chaque table fille -- meme si on finit par DELETE
        //    le dossier (les CASCADE absorbent les rares orphelins eventuels).
        //    Note : ticket_document_snapshots cascade depuis dataroom_documents.
        int settingsDeleted = exec("DELETE FROM dataroom_settings WHERE dossier_id = ?1 AND workspace_id = ?2", cmd);
        // V25 : les tables comptable / fiscal / exercices / alertes ont disparu.
        exec("DELETE FROM dataroom_client_access_log WHERE dossier_id = ?1 AND workspace_id = ?2", cmd);
        exec("DELETE FROM dataroom_demandes_client WHERE dossier_id = ?1 AND workspace_id = ?2", cmd);
        exec("DELETE FROM dataroom_depots WHERE dossier_id = ?1 AND workspace_id = ?2", cmd);
        // snapshots reference dataroom_documents (ON DELETE CASCADE) -- les suivants
        // partent au DELETE des documents.
        exec("DELETE FROM dataroom_documents WHERE dossier_id = ?1 AND workspace_id = ?2", cmd);

        // 4) Fix 2026-06-08 — Decision user : suppression PHYSIQUE INCONDITIONNELLE.
        //    Toutes les FK vers entreprise_dossiers sont ON DELETE CASCADE (tables
        //    dataroom_*) ou ON DELETE SET NULL (tickets.dossier_id). Aucun risque
        //    d'integrite. L'audit_log conserve entity_id=<dossierId> (UUID sans FK)
        //    pour la tracabilite CNDP. Les tickets historiques restent visibles
        //    avec dossier_id=NULL (orphan tickets).
        em.createNativeQuery(
                "DELETE FROM entreprise_dossiers WHERE id = ?1 AND workspace_id = ?2")
                .setParameter(1, cmd.dossierId())
                .setParameter(2, cmd.workspaceId())
                .executeUpdate();
        boolean dossierDeleted = true;
        boolean dossierRadied = false;

        log.info("DATAROOM_DELETED dossier={} ({}) workspace={} by={} settings_deleted={} dossier_deleted=PHYSIQUE",
                cmd.dossierId(), raisonSociale, cmd.workspaceId(), cmd.deletedBy(),
                settingsDeleted);

        return new Result(cmd.dossierId(), false, dossierDeleted, dossierRadied, raisonSociale);
    }

    private int exec(String sql, Command cmd) {
        return em.createNativeQuery(sql)
                .setParameter(1, cmd.dossierId())
                .setParameter(2, cmd.workspaceId())
                .executeUpdate();
    }
}
