package ma.jurika.auth.domain.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository {

    void store(UUID userId, UUID workspaceId, String tokenHash, Instant expiresAt);

    Optional<StoredResetToken> findByHash(String tokenHash);

    void markUsed(String tokenHash, Instant when);

    record StoredResetToken(UUID userId, UUID workspaceId, Instant expiresAt, Instant usedAt) {
        public boolean isUsable(Instant now) {
            return usedAt == null && expiresAt.isAfter(now);
        }
    }
}
