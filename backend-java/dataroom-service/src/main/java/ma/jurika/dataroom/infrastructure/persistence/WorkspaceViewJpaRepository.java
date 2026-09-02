package ma.jurika.dataroom.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkspaceViewJpaRepository extends JpaRepository<WorkspaceViewEntity, UUID> {
}
