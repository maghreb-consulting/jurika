package ma.jurika.auth.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Fix 2026-06-07 (BUG 6) — Retrait DEFINITIF de l'acces d'un client
 * a un dossier.
 *
 * <p>Difference avec la suspension (cote dataroom-service /
 * DataroomSettings.accessStatus = SUSPENDED) :
 * <ul>
 *   <li>Suspension : reversible, garde la liaison client_id intacte.
 *       Sert a bloquer temporairement (impayé, contentieux, etc.).</li>
 *   <li>Retrait : detache le compte client du dossier (entreprise_dossiers.client_id = NULL).
 *       Le user existe toujours (autres dossiers, historique audit). Mais il
 *       perd l'acces a CE dossier definitivement (jusqu'a re-invitation).</li>
 * </ul>
 *
 * <p>RBAC : EMPLOYE ou SUPERVISEUR du workspace courant.
 *
 * <p>Securite : workspace-scoped via {@code WHERE workspace_id = ?}.
 * Audit log : action {@code CLIENT_ACCESS_REMOVED}.
 */
@Service
public class RemoveClientAccessUseCase {

    private static final Logger log = LoggerFactory.getLogger(RemoveClientAccessUseCase.class);

    private final AuditLogger auditLogger;

    @PersistenceContext
    private EntityManager em;

    public RemoveClientAccessUseCase(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    public record Command(UUID workspaceId, UUID dossierId,
                           UUID removedBy, String ipAddress, String userAgent) {}

    public record Result(UUID dossierId, UUID previousClientId, boolean alreadyDetached) {}

    @Transactional
    public Result execute(Command cmd) {
        TenantContext.set(cmd.workspaceId());

        @SuppressWarnings("unchecked")
        var rows = (java.util.List<Object[]>) em.createNativeQuery("""
                SELECT id, raison_sociale, client_id FROM entreprise_dossiers
                 WHERE id = ?1 AND workspace_id = ?2
                """)
                .setParameter(1, cmd.dossierId())
                .setParameter(2, cmd.workspaceId())
                .getResultList();
        if (rows.isEmpty()) {
            throw new NotFoundException("Dossier introuvable");
        }
        Object[] row = rows.get(0);
        String raisonSociale = String.valueOf(row[1]);
        Object existingClientIdObj = row[2];

        if (existingClientIdObj == null) {
            // Idempotence : deja detache, on log mais on ne fait rien.
            log.info("RemoveClientAccess : dossier {} ({}) deja sans client",
                    cmd.dossierId(), raisonSociale);
            return new Result(cmd.dossierId(), null, true);
        }

        UUID previousClientId = (UUID) existingClientIdObj;
        em.createNativeQuery(
                "UPDATE entreprise_dossiers SET client_id = NULL WHERE id = ?1 AND workspace_id = ?2")
                .setParameter(1, cmd.dossierId())
                .setParameter(2, cmd.workspaceId())
                .executeUpdate();

        auditLogger.log(cmd.workspaceId(), cmd.removedBy(), "CLIENT_ACCESS_REMOVED", "dossier",
                cmd.dossierId(), cmd.ipAddress(), cmd.userAgent(),
                Map.of("previous_client_id", previousClientId.toString(),
                        "raison_sociale", raisonSociale));

        log.info("CLIENT_ACCESS_REMOVED dossier={} previousClient={} workspace={} by={}",
                cmd.dossierId(), previousClientId, cmd.workspaceId(), cmd.removedBy());

        return new Result(cmd.dossierId(), previousClientId, false);
    }
}
