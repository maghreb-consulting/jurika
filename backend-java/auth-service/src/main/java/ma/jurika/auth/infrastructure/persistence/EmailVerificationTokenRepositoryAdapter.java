package ma.jurika.auth.infrastructure.persistence;

import ma.jurika.auth.domain.port.EmailVerificationTokenRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class EmailVerificationTokenRepositoryAdapter implements EmailVerificationTokenRepository {

    private final EmailVerificationTokenJpaRepository repo;

    public EmailVerificationTokenRepositoryAdapter(EmailVerificationTokenJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public UUID create(UUID workspaceId, UUID userId, String email, String tokenHash,
                       String purpose, Instant expiresAt, String ipAddress, String userAgent) {
        EmailVerificationTokenEntity e = new EmailVerificationTokenEntity();
        e.setWorkspaceId(workspaceId);
        e.setUserId(userId);
        e.setEmail(email);
        e.setTokenHash(tokenHash);
        e.setPurpose(purpose);
        e.setExpiresAt(expiresAt);
        e.setIpAddress(ipAddress);
        e.setUserAgent(userAgent);
        return repo.save(e).getId();
    }

    @Override
    public Optional<StoredToken> findByHash(String tokenHash) {
        return repo.findByTokenHash(tokenHash).map(EmailVerificationTokenRepositoryAdapter::versDomaine);
    }

    private static StoredToken versDomaine(EmailVerificationTokenEntity e) {
        return new StoredToken(
                e.getId(),
                e.getWorkspaceId(),
                e.getUserId(),
                e.getEmail(),
                e.getPurpose(),
                e.getExpiresAt(),
                e.getUsedAt()
        );
    }

    @Override
    public int revokePending(UUID userId, String purpose, Instant when) {
        return repo.retirerOuverts(userId, purpose, when);
    }

    @Override
    public Optional<StoredToken> findPending(UUID userId, String purpose, Instant now) {
        return repo.findFirstByUserIdAndPurposeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                userId, purpose, now).map(EmailVerificationTokenRepositoryAdapter::versDomaine);
    }

    @Override
    public boolean markUsed(UUID id, Instant usedAt) {
        return repo.consommer(id, usedAt) == 1;
    }
}
