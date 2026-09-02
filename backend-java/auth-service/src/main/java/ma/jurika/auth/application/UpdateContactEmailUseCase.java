package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BUG 7 (2026-06-08, chore finitions) — Met a jour le {@code contact_email}
 * (email de notifications) de l'utilisateur courant.
 *
 * <p>Regles metier :
 * <ul>
 *   <li>Le caller modifie son PROPRE compte (RBAC enforcer cote controller :
 *       on prend l'userId depuis le principal).</li>
 *   <li>Le nouvel email doit etre different de l'actuel (idempotent : on
 *       renvoie {@code changed=false} sinon, pas d'audit spam).</li>
 *   <li>Le nouvel email NE doit PAS etre egal au login_email (sinon UX confus,
 *       le user croirait pouvoir se reconnecter avec son contact apres une
 *       reconfiguration alors que le flag jurika.auth.login-email-enabled
 *       impose le login_email).</li>
 *   <li>Audit {@code CONTACT_EMAIL_CHANGED} avec metadata.previousContactEmail
 *       + metadata.newContactEmail.</li>
 * </ul>
 *
 * <p>Le login_email n'est PAS touche : il reste l'identifiant stable. Pour le
 * changer, il faudra une procedure administrateur dediee.
 */
@Service
public class UpdateContactEmailUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateContactEmailUseCase.class);

    private final UserRepository userRepository;
    private final AuditLogger auditLogger;

    public UpdateContactEmailUseCase(UserRepository userRepository, AuditLogger auditLogger) {
        this.userRepository = userRepository;
        this.auditLogger = auditLogger;
    }

    public record Command(UUID userId, UUID workspaceId, String newContactEmail,
                           String ipAddress, String userAgent) {}

    public record Result(UUID userId, String previousContactEmail, String contactEmail,
                          String loginEmail, boolean changed) {}

    @Transactional
    public Result execute(Command cmd) {
        TenantContext.set(cmd.workspaceId());

        if (cmd.newContactEmail() == null || cmd.newContactEmail().isBlank()) {
            throw new ValidationException("CONTACT_EMAIL_REQUIRED");
        }
        String normalized = cmd.newContactEmail().trim().toLowerCase();

        User u = userRepository.findById(cmd.userId())
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable"));

        // Defense en profondeur : workspace cross-tenant (le principal devrait
        // deja avoir le bon workspaceId via JWT, mais on verifie).
        if (!u.workspaceId().equals(cmd.workspaceId())) {
            throw new NotFoundException("Utilisateur introuvable");
        }

        String previous = u.contactEmail();
        if (normalized.equals(previous == null ? null : previous.toLowerCase())) {
            return new Result(u.id(), previous, previous, u.loginEmail(), false);
        }
        if (normalized.equalsIgnoreCase(u.loginEmail())) {
            throw new ConflictException(
                    "Le contact_email ne peut pas etre identique au login_email. "
                            + "Utilisez un email perso distinct pour les notifications.");
        }

        userRepository.updateContactEmail(u.id(), normalized);

        Map<String, Object> meta = new HashMap<>();
        meta.put("previousContactEmail", previous);
        meta.put("newContactEmail", normalized);
        meta.put("loginEmail", u.loginEmail());
        auditLogger.log(cmd.workspaceId(), cmd.userId(), "CONTACT_EMAIL_CHANGED",
                "user", u.id(), cmd.ipAddress(), cmd.userAgent(), meta);

        log.info("BUG7/contact-email user={} {} -> {}",
                cmd.userId(), previous, normalized);

        return new Result(u.id(), previous, normalized, u.loginEmail(), true);
    }
}
