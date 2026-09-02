package ma.jurika.auth.application;

import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Definit la methode 2FA choisie par l'utilisateur.
 * <p>
 * Apres cet appel, le user devra :
 * <ul>
 *     <li>Si SMS : appeler {@code /2fa/sms/send} puis {@code /2fa/sms/verify}</li>
 *     <li>Si TOTP : recuperer {@code /2fa/totp/qr-code} puis {@code /2fa/totp/verify}</li>
 * </ul>
 * Cette etape pre-positionne la methode et autorise les endpoints d'activation correspondants.
 */
@Service
public class Choose2faMethodUseCase {

    private static final Set<String> ALLOWED = Set.of("SMS", "TOTP");

    private final UserRepository userRepository;
    private final AuditLogger auditLogger;

    public Choose2faMethodUseCase(UserRepository userRepository, AuditLogger auditLogger) {
        this.userRepository = userRepository;
        this.auditLogger = auditLogger;
    }

    public record Command(UUID userId, UUID workspaceId, String method,
                           String ipAddress, String userAgent) {}

    @Transactional
    public void execute(Command cmd) {
        if (cmd.method() == null || !ALLOWED.contains(cmd.method())) {
            throw new ValidationException("2FA_METHOD_INVALID");
        }
        TenantContext.set(cmd.workspaceId());
        userRepository.updateTwofaMethod(cmd.userId(), cmd.method());

        auditLogger.log(cmd.workspaceId(), cmd.userId(), "2FA_METHOD_CHOSEN", "user",
                cmd.userId(), cmd.ipAddress(), cmd.userAgent(),
                Map.of("method", cmd.method()));
    }
}
