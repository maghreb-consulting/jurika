package ma.jurika.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface WorkspaceAllowedOriginJpaRepository extends JpaRepository<WorkspaceAllowedOriginEntity, UUID> {

    @Query("SELECT DISTINCT o.origin FROM WorkspaceAllowedOriginEntity o ORDER BY o.origin")
    List<String> findAllDistinctOrigins();

    List<WorkspaceAllowedOriginEntity> findByWorkspaceIdOrderByCreatedAtAsc(UUID workspaceId);

    long deleteByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
