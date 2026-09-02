package ma.jurika.auth.application;

import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.RefreshTokenRepository;
import ma.jurika.auth.domain.port.TokenIssuer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class LogoutUseCase {

    private final TokenIssuer tokenIssuer;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogger auditLogger;

    public LogoutUseCase(TokenIssuer tokenIssuer,
                         RefreshTokenRepository refreshTokenRepository,
                         AuditLogger auditLogger) {
        this.tokenIssuer = tokenIssuer;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public void execute(String rawRefreshToken, UUID userId, UUID workspaceId, String ip, String ua) {
        execute(rawRefreshToken, userId, workspaceId, ip, ua, false);
    }

    /**
     * Logs out the user. If {@code revokeAll} is true, every active refresh token for the user is
     * revoked ("logout from all devices") — otherwise only the current refresh token is revoked.
     */
    @Transactional
    public void execute(String rawRefreshToken, UUID userId, UUID workspaceId, String ip, String ua,
                        boolean revokeAll) {
        Instant now = Instant.now();
        if (revokeAll) {
            refreshTokenRepository.revokeAllForUser(userId, now);
        } else if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            TokenIssuer.ParsedRefreshToken parsed = tokenIssuer.parseRefreshToken(rawRefreshToken);
            refreshTokenRepository.revoke(parsed.hash(), now);
        }
        auditLogger.log(workspaceId, userId, revokeAll ? "LOGOUT_ALL" : "LOGOUT",
                "user", userId, ip, ua, Map.of("revokeAll", revokeAll));
    }
}
