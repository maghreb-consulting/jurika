package ma.jurika.auth.application;

import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Trace la methode 2FA choisie par l'utilisateur, avant son activation.
 * <p>
 * Apres cet appel, le user devra :
 * <ul>
 *     <li>Si SMS : appeler {@code /2fa/sms/send} puis {@code /2fa/sms/verify} (purpose 2FA_SETUP)</li>
 *     <li>Si TOTP : appeler {@code /setup-2fa} puis {@code /setup-2fa/confirm}</li>
 * </ul>
 * Recette R2 (2026-10-17) : le choix n'ECRIT plus la methode. Avant, il posait
 * {@code twofa_method} tout de suite : la configuration passait pour faite et la connexion
 * se faisait sans second facteur, jusqu'a retirer l'application d'un compte qui l'avait
 * (motif 9). La methode n'est posee qu'a la verification (code SMS ou code de l'application).
 * Le SMS est refuse quand il n'est pas disponible (DisponibiliteSms2fa).
 */
@Service
public class Choose2faMethodUseCase {

    private static final Set<String> ALLOWED = Set.of("SMS", "TOTP");

    private final AuditLogger auditLogger;
    private final DisponibiliteSms2fa disponibiliteSms;

    public Choose2faMethodUseCase(AuditLogger auditLogger, DisponibiliteSms2fa disponibiliteSms) {
        this.auditLogger = auditLogger;
        this.disponibiliteSms = disponibiliteSms;
    }

    public record Command(UUID userId, UUID workspaceId, String method,
                           String ipAddress, String userAgent) {}

    @Transactional
    public void execute(Command cmd) {
        if (cmd.method() == null || !ALLOWED.contains(cmd.method())) {
            throw new BusinessException("2FA_METHOD_INVALID", "M\u00e9thode inconnue : choisissez SMS ou application d\u2019authentification.");
        }
        if ("SMS".equals(cmd.method())) {
            disponibiliteSms.exiger();
        }
        TenantContext.set(cmd.workspaceId());
        auditLogger.log(cmd.workspaceId(), cmd.userId(), "2FA_METHOD_CHOSEN", "user",
                cmd.userId(), cmd.ipAddress(), cmd.userAgent(),
                Map.of("method", cmd.method()));
    }
}
