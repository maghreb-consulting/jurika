package ma.jurika.auth.infrastructure.persistence;

import ma.jurika.auth.domain.port.PasswordResetTokenRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepository {

    private final PasswordResetTokenJpaRepository jpa;

    public PasswordResetTokenRepositoryAdapter(PasswordResetTokenJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void store(UUID userId, UUID workspaceId, String tokenHash, Instant expiresAt) {
        PasswordResetTokenEntity e = new PasswordResetTokenEntity();
        e.setUserId(userId);
        e.setWorkspaceId(workspaceId);
        e.setTokenHash(tokenHash);
        e.setExpiresAt(expiresAt);
        jpa.save(e);
    }

    @Override
    public Optional<StoredResetToken> findByHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash)
                .map(e -> new StoredResetToken(e.getUserId(), e.getWorkspaceId(),
                        e.getExpiresAt(), e.getUsedAt()));
    }

    @Override
    public void markUsed(String tokenHash, Instant when) {
        jpa.markUsed(tokenHash, when);
    }
}
