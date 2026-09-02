package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.model.UserStatus;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.common.billing.PlanLimitsService;
import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * BUG 6 (2026-06-07) — Active ou desactive un compte membre du workspace
 * courant. Reserve aux SUPERVISEUR (RBAC enforcee au controller).
 *
 * <p>Regles metier :
 * <ul>
 *   <li>Cible doit etre dans le MEME workspace que l'appelant (defense en profondeur
 *       au-dessus du RLS — cf {@code multi-tenant-defense-in-depth-2026-06-05}).</li>
 *   <li>Cible doit avoir un role EMPLOYE ou SUPERVISEUR (CLIENT se gere via le
 *       flux dataroom {@code DELETE /auth/dossiers/.../client}).</li>
 *   <li>L'appelant ne peut PAS se desactiver lui-meme (anti-lockout).</li>
 *   <li>Reactiver un EMPLOYE re-applique le quota plan ({@link PlanLimitsService}) —
 *       si plein, throw 402 PLAN_LIMIT_USERS.</li>
 *   <li>Idempotent : repasser ACTIVE sur un compte ACTIVE -> 200 OK no-op (pas
 *       d'audit log spam).</li>
 * </ul>
 *
 * <p>Audit : {@code USER_ACTIVATED} ou {@code USER_DEACTIVATED} avec
 * metadata.targetEmail + metadata.targetRole + metadata.previousStatus.
 */
@Service
public class SetUserStatusUseCase {

    private static final Logger log = LoggerFactory.getLogger(SetUserStatusUseCase.class);
    private static final Set<Role> ALLOWED_TARGET_ROLES = Set.of(Role.EMPLOYE, Role.SUPERVISEUR);

    private final UserRepository userRepository;
    private final AuditLogger auditLogger;
    private final PlanLimitsService planLimitsService;

    public SetUserStatusUseCase(UserRepository userRepository,
                                 AuditLogger auditLogger,
                                 @org.springframework.beans.factory.annotation.Autowired(required = false)
                                 PlanLimitsService planLimitsService) {
        this.userRepository = userRepository;
        this.auditLogger = auditLogger;
        this.planLimitsService = planLimitsService;
    }

    public record Command(UUID workspaceId, UUID requestingUserId, UUID targetUserId,
                           boolean active, String ipAddress, String userAgent) {}

    public record Result(UUID userId, UserStatus previousStatus, UserStatus newStatus,
                          boolean changed) {}

    @Transactional
    public Result execute(Command cmd) {
        TenantContext.set(cmd.workspaceId());

        if (cmd.targetUserId().equals(cmd.requestingUserId())) {
            throw new AccessDeniedException("Vous ne pouvez pas modifier votre propre statut.");
        }

        User target = userRepository.findById(cmd.targetUserId())
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable"));

        if (!target.workspaceId().equals(cmd.workspaceId())) {
            // Cross-tenant : on traite comme non-trouve pour ne pas leak l'existence.
            throw new NotFoundException("Utilisateur introuvable");
        }

        if (!ALLOWED_TARGET_ROLES.contains(target.role())) {
            throw new ConflictException(
                    "Ce flux ne gere que les comptes EMPLOYE et SUPERVISEUR. "
                            + "Pour un CLIENT, utiliser DELETE /auth/dossiers/{id}/client.");
        }

        UserStatus previous = target.status();
        UserStatus next = cmd.active() ? UserStatus.ACTIVE : UserStatus.INACTIVE;

        if (previous == next) {
            return new Result(target.id(), previous, next, false);
        }

        // Reactivation EMPLOYE : on re-applique le quota plan (un EMPLOYE INACTIVE
        // ne consomme pas de siege, donc le reactiver peut depasser la limite).
        if (cmd.active() && target.role() == Role.EMPLOYE && planLimitsService != null) {
            planLimitsService.enforceUserLimit(target.workspaceId());
        }

        // 2026-06-08 (session 5) : reactivation SUPERVISEUR — un cabinet ne
        // peut compter qu'UN seul SUPERVISEUR non-INACTIVE a la fois. Si un
        // autre SUPERVISEUR est deja PENDING/ACTIVE dans le workspace, on
        // refuse la reactivation. Coherent avec le garde-fou symetrique dans
        // InviteEmployeUseCase.
        if (cmd.active() && target.role() == Role.SUPERVISEUR) {
            long otherSupervisors = userRepository
                    .findByWorkspaceAndRoles(target.workspaceId(), Set.of(Role.SUPERVISEUR)).stream()
                    .filter(u -> !u.id().equals(target.id()))
                    .filter(u -> u.status() != UserStatus.INACTIVE)
                    .count();
            if (otherSupervisors >= 1) {
                throw new ConflictException(
                        "Un seul SUPERVISEUR est autorise par cabinet. "
                                + "Desactivez l'actuel avant de reactiver celui-ci.");
            }
        }

        userRepository.setStatus(target.id(), next);

        Map<String, Object> meta = new HashMap<>();
        meta.put("targetEmail", target.email());
        meta.put("targetRole", target.role().name());
        meta.put("previousStatus", previous.name());
        meta.put("newStatus", next.name());
        String action = cmd.active() ? "USER_ACTIVATED" : "USER_DEACTIVATED";
        auditLogger.log(cmd.workspaceId(), cmd.requestingUserId(), action, "user",
                target.id(), cmd.ipAddress(), cmd.userAgent(), meta);

        log.info("BUG6/setStatus workspace={} actor={} target={} {} -> {}",
                cmd.workspaceId(), cmd.requestingUserId(), target.id(), previous, next);

        return new Result(target.id(), previous, next, true);
    }
}
