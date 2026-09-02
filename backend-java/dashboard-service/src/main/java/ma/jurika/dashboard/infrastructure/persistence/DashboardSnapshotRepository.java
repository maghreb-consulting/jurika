package ma.jurika.dashboard.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DashboardSnapshotRepository extends JpaRepository<DashboardSnapshotEntity, UUID> {

    @Query("""
            SELECT d FROM DashboardSnapshotEntity d
            WHERE d.workspaceId = :ws
              AND d.scope = :scope
              AND (:actor IS NULL OR d.actorId = :actor)
              AND d.validUntil > :now
            ORDER BY d.generatedAt DESC
            """)
    Optional<DashboardSnapshotEntity> findLatestValid(@Param("ws") UUID workspaceId,
                                                      @Param("scope") String scope,
                                                      @Param("actor") UUID actorId,
                                                      @Param("now") Instant now);
}
