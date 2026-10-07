package ma.jurika.auth.application;

import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.EmailVerificationTokenRepository;
import ma.jurika.auth.domain.port.EmailVerificationTokenRepository.StoredToken;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.domain.port.WorkspaceRepository;
import ma.jurika.auth.domain.service.VerificationTokenHasher;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Verifie un lien d'activation email :
 * <ol>
 *     <li>Hash le token recu</li>
 *     <li>Lookup en DB</li>
 *     <li>Verifie pas expire, pas utilise</li>
 *     <li>Active le workspace (PENDING_VERIFICATION -> ACTIVE)</li>
 *     <li>Marque l'email du user comme verifie</li>
 *     <li>Marque le token comme utilise (single-use)</li>
 * </ol>
 */
@Service
public class VerifyEmailUseCase {

    private final EmailVerificationTokenRepository tokenRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final VerificationTokenHasher tokenHasher;
    private final AuditLogger auditLogger;

    public VerifyEmailUseCase(EmailVerificationTokenRepository tokenRepository,
                               WorkspaceRepository workspaceRepository,
                               UserRepository userRepository,
                               VerificationTokenHasher tokenHasher,
                               AuditLogger auditLogger) {
        this.tokenRepository = tokenRepository;
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.tokenHasher = tokenHasher;
        this.auditLogger = auditLogger;
    }

    public record Command(String rawToken, String ipAddress, String userAgent) {}

    public record Result(UUID workspaceId, UUID userId, String email) {}

    @Transactional
    public Result execute(Command cmd) {
        String hash = tokenHasher.hash(cmd.rawToken());

        StoredToken token = tokenRepository.findByHash(hash)
                .orElseThrow(() -> new NotFoundException("Lien de verification invalide"));

        if (token.isUsed()) {
            throw new ValidationException("EMAIL_VERIFICATION_ALREADY_USED");
        }
        if (token.isExpired()) {
            throw new ValidationException("EMAIL_VERIFICATION_EXPIRED");
        }

        TenantContext.set(token.workspaceId());

        // Lot L0 (E12b) : consommation ATOMIQUE avant les effets (activation,
        // verification) ; un lien rejoue ou presente deux fois en meme temps
        // n'est accepte qu'une fois.
        if (!tokenRepository.markUsed(token.id(), Instant.now())) {
            throw new ValidationException("EMAIL_VERIFICATION_ALREADY_USED");
        }
        workspaceRepository.activate(token.workspaceId());
        userRepository.markEmailVerified(token.userId(), Instant.now());

        auditLogger.log(token.workspaceId(), token.userId(), "EMAIL_VERIFIED", "user",
                token.userId(), cmd.ipAddress(), cmd.userAgent(),
                Map.of("tokenId", String.valueOf(token.id())));

        return new Result(token.workspaceId(), token.userId(), token.email());
    }
}
