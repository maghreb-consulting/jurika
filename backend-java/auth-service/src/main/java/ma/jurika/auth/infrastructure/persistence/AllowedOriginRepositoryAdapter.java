package ma.jurika.auth.infrastructure.persistence;

import ma.jurika.auth.domain.port.AllowedOriginRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public class AllowedOriginRepositoryAdapter implements AllowedOriginRepository {

    private final WorkspaceAllowedOriginJpaRepository jpa;

    public AllowedOriginRepositoryAdapter(WorkspaceAllowedOriginJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<String> findAllDistinct() {
        return jpa.findAllDistinctOrigins();
    }

    @Override
    public List<AllowedOrigin> findByWorkspace(UUID workspaceId) {
        return jpa.findByWorkspaceIdOrderByCreatedAtAsc(workspaceId).stream()
                .map(e -> new AllowedOrigin(e.getId(), e.getWorkspaceId(), e.getOrigin()))
                .toList();
    }

    @Override
    @Transactional
    public AllowedOrigin add(UUID workspaceId, String origin, UUID createdBy) {
        WorkspaceAllowedOriginEntity entity = new WorkspaceAllowedOriginEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setOrigin(origin);
        entity.setCreatedBy(createdBy);
        WorkspaceAllowedOriginEntity saved = jpa.save(entity);
        return new AllowedOrigin(saved.getId(), saved.getWorkspaceId(), saved.getOrigin());
    }

    @Override
    @Transactional
    public boolean delete(UUID workspaceId, UUID originId) {
        return jpa.deleteByIdAndWorkspaceId(originId, workspaceId) > 0;
    }
}
