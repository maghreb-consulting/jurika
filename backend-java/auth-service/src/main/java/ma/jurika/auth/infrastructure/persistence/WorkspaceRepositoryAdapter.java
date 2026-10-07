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
        return saveWithStatus(null, code, name, contactEmail, subscriptionId, WorkspaceStatus.ACTIVE);
    }

    @Override
    public Workspace createPending(UUID id, String code, String name, String contactEmail, UUID subscriptionId) {
        return saveWithStatus(id, code, name, contactEmail, subscriptionId, WorkspaceStatus.PENDING_VERIFICATION);
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
        // Lot L0 (E13a) : unicite GLOBALE du code, hors RLS (fonction auth V34).
        return jpa.codeExisteGlobalement(code);
    }

    private Workspace saveWithStatus(UUID id, String code, String name, String contactEmail,
                                      UUID subscriptionId, WorkspaceStatus status) {
        WorkspaceEntity entity = new WorkspaceEntity();
        if (id != null) {
            entity.setId(id);
        }
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
