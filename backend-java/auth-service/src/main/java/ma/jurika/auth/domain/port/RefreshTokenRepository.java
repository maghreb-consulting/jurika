package ma.jurika.auth.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    void store(UUID userId, UUID workspaceId, String tokenHash, Instant expiresAt,
               String userAgent, String ipAddress);

    Optional<StoredRefreshToken> findByHash(String tokenHash);

    void revoke(String tokenHash, Instant when);

    void revokeAllForUser(UUID userId, Instant when);

    /** Number of non-revoked refresh tokens for the user whose expiry is still in the future. */
    long countActiveByUser(UUID userId, Instant now);

    /**
     * Returns up to {@code limit} oldest active refresh tokens (by {@code issued_at})
     * for the user. Used to enforce the per-user session cap (RG-SAAS-12).
     */
    List<ActiveRefreshToken> findOldestActive(UUID userId, int limit, Instant now);

    /** Hard-deletes refresh tokens whose {@code expires_at} is older than {@code cutoff}. */
    int deleteExpired(Instant cutoff);

    /**
     * Returns {@code true} if {@code userId} already has a refresh token (revoked or not)
     * issued since {@code since} from the same {@code ipAddress} AND {@code userAgent}.
     * Used by {@link ma.jurika.auth.application.LoginUseCase} to detect logins from a
     * previously-unseen device (RG-SAAS-08 "login-new-device" email alert).
     *
     * <p>Both {@code ipAddress} and {@code userAgent} are compared case-insensitively;
     * a {@code null} or blank value on either side counts as "no match" (we never want
     * to suppress the alert when we don't actually know the client identity).
     */
    boolean hasRecentSessionFromDevice(UUID userId, String ipAddress, String userAgent, Instant since);

    record StoredRefreshToken(UUID userId, UUID workspaceId, Instant issuedAt,
                              Instant expiresAt, Instant revokedAt) {
        public boolean isUsable(Instant now) {
            return revokedAt == null && expiresAt.isAfter(now);
        }

        /**
         * Vrai si le token a depasse la fenetre glissante d'inactivite : aucun refresh
         * n'est intervenu depuis plus de {@code inactivityWindow}. {@code issuedAt} est
         * remis a maintenant a chaque rotation, donc il equivaut a "derniere activite".
         */
        public boolean isInactive(Instant now, java.time.Duration inactivityWindow) {
            return issuedAt != null && issuedAt.plus(inactivityWindow).isBefore(now);
        }
    }

    record ActiveRefreshToken(UUID id, String tokenHash, Instant issuedAt, Instant expiresAt) {}
}
