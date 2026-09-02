package ma.jurika.auth.infrastructure.persistence;

import ma.jurika.auth.domain.model.Workspace;
import ma.jurika.auth.domain.model.WorkspaceStatus;
import ma.jurika.auth.domain.port.WorkspaceRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class WorkspaceRepositoryAdapter implements WorkspaceRepository {

    private final WorkspaceJpaRepository jpa;

    public WorkspaceRepositoryAdapter(WorkspaceJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Workspace> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Workspace> findByCode(String code) {
        return jpa.findByCode(code).map(this::toDomain);
    }

    @Override
    public Workspace create(String code, String name, String contactEmail, UUID subscriptionId) {
        return saveWithStatus(code, name, contactEmail, subscriptionId, WorkspaceStatus.ACTIVE);
    }

    @Override
    public Workspace createPending(String code, String name, String contactEmail, UUID subscriptionId) {
        return saveWithStatus(code, name, contactEmail, subscriptionId, WorkspaceStatus.PENDING_VERIFICATION);
    }

    @Override
    public void activate(UUID workspaceId) {
        jpa.findById(workspaceId).ifPresent(e -> {
            e.setStatus(WorkspaceStatus.ACTIVE.name());
            jpa.save(e);
        });
    }

    @Override
    public boolean codeExists(String code) {
        return jpa.existsByCode(code);
    }

    private Workspace saveWithStatus(String code, String name, String contactEmail,
                                      UUID subscriptionId, WorkspaceStatus status) {
        WorkspaceEntity entity = new WorkspaceEntity();
        entity.setCode(code);
        entity.setName(name);
        entity.setContactEmail(contactEmail);
        entity.setSubscriptionId(subscriptionId);
        entity.setStatus(status.name());
        return toDomain(jpa.save(entity));
    }

    private Workspace toDomain(WorkspaceEntity e) {
        return new Workspace(
                e.getId(), e.getCode(), e.getName(), e.getContactEmail(),
                e.getSubscriptionId(), WorkspaceStatus.valueOf(e.getStatus()),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
