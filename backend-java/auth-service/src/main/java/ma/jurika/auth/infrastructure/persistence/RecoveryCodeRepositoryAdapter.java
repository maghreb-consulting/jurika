package ma.jurika.auth.infrastructure.persistence;

import ma.jurika.auth.domain.port.RecoveryCodeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class RecoveryCodeRepositoryAdapter implements RecoveryCodeRepository {

    private final RecoveryCodeJpaRepository repo;

    public RecoveryCodeRepositoryAdapter(RecoveryCodeJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void replaceAll(UUID workspaceId, UUID userId, List<String> codeHashes) {
        repo.deleteByUserId(userId);
        List<RecoveryCodeEntity> entities = codeHashes.stream().map(hash -> {
            RecoveryCodeEntity e = new RecoveryCodeEntity();
            e.setWorkspaceId(workspaceId);
            e.setUserId(userId);
            e.setCodeHash(hash);
            return e;
        }).toList();
        repo.saveAll(entities);
    }

    @Override
    public List<StoredRecoveryCode> findActive(UUID userId) {
        return repo.findActive(userId).stream().map(e -> new StoredRecoveryCode(
                e.getId(), e.getWorkspaceId(), e.getUserId(),
                e.getCodeHash(), e.getCreatedAt(), e.getUsedAt()
        )).toList();
    }

    @Override
    public void markUsed(UUID id, Instant usedAt) {
        repo.findById(id).ifPresent(e -> {
            e.setUsedAt(usedAt);
            repo.save(e);
        });
    }

    @Override
    @Transactional
    public void deleteAllForUser(UUID userId) {
        repo.deleteByUserId(userId);
    }
}
