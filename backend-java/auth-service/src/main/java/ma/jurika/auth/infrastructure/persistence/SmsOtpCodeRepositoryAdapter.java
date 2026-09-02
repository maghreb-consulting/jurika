package ma.jurika.auth.infrastructure.persistence;

import ma.jurika.auth.domain.port.SmsOtpCodeRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class SmsOtpCodeRepositoryAdapter implements SmsOtpCodeRepository {

    private final SmsOtpCodeJpaRepository repo;

    public SmsOtpCodeRepositoryAdapter(SmsOtpCodeJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public UUID create(UUID workspaceId, UUID userId, String phoneE164, String codeHash,
                       String purpose, Instant expiresAt, int maxAttempts) {
        SmsOtpCodeEntity e = new SmsOtpCodeEntity();
        e.setWorkspaceId(workspaceId);
        e.setUserId(userId);
        e.setPhoneE164(phoneE164);
        e.setCodeHash(codeHash);
        e.setPurpose(purpose);
        e.setExpiresAt(expiresAt);
        e.setAttempts((short) 0);
        e.setMaxAttempts((short) maxAttempts);
        return repo.save(e).getId();
    }

    @Override
    public Optional<StoredOtp> findLatestActive(UUID userId, String purpose) {
        return repo.findLatestActive(userId, purpose).map(e -> new StoredOtp(
                e.getId(), e.getWorkspaceId(), e.getUserId(), e.getCodeHash(),
                e.getPhoneE164(), e.getPurpose(), e.getExpiresAt(),
                e.getAttempts(), e.getMaxAttempts(), e.getUsedAt()
        ));
    }

    @Override
    public void markUsed(UUID id, Instant usedAt) {
        repo.findById(id).ifPresent(e -> {
            e.setUsedAt(usedAt);
            repo.save(e);
        });
    }

    @Override
    public void incrementAttempts(UUID id) {
        repo.incrementAttempts(id);
    }
}
