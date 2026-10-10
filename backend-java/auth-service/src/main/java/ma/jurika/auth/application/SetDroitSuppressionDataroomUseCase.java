package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.DroitSuppressionDataroomRepository;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lot L1, etape E8 : le superviseur accorde ou retire a un EMPLOYE de son cabinet le
 * droit de suppression en Data Room (CDC 3.2, RG-DR-06). Chaque changement est trace
 * (DROIT_SUPPRESSION_DATAROOM_ACCORDE / _RETIRE).
 */
@Service
public class SetDroitSuppressionDataroomUseCase {

    private final UserRepository users;
    private final DroitSuppressionDataroomRepository droits;
    private final AuditLogger audit;

    public SetDroitSuppressionDataroomUseCase(UserRepository users, DroitSuppressionDataroomRepository droits,
                                              AuditLogger audit) {
        this.users = users;
        this.droits = droits;
        this.audit = audit;
    }

    public record Command(UUID workspaceId, UUID superviseurId, UUID employeId, boolean accorde,
                          String ipAddress, String userAgent) {}

    public record Result(UUID userId, boolean droitSuppressionDataroom, boolean changed) {}

    @Transactional
    public Result execute(Command cmd) {
        TenantContext.set(cmd.workspaceId());
        User cible = users.findById(cmd.employeId())
                .filter(u -> cmd.workspaceId().equals(u.workspaceId()))
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable"));
        if (cible.role() != Role.EMPLOYE) {
            throw new ConflictException("Le droit de suppression ne s'accorde qu'a un employe");
        }
        boolean avant = droits.lire(cmd.workspaceId(), cible.id());
        if (avant == cmd.accorde()) {
            return new Result(cible.id(), avant, false);
        }
        droits.definir(cmd.workspaceId(), cible.id(), cmd.accorde());
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("targetEmail", cible.email());
        meta.put("avant", avant);
        meta.put("apres", cmd.accorde());
        audit.log(cmd.workspaceId(), cmd.superviseurId(),
                cmd.accorde() ? "DROIT_SUPPRESSION_DATAROOM_ACCORDE" : "DROIT_SUPPRESSION_DATAROOM_RETIRE",
                "user", cible.id(), cmd.ipAddress(), cmd.userAgent(), meta);
        return new Result(cible.id(), cmd.accorde(), true);
    }
}
