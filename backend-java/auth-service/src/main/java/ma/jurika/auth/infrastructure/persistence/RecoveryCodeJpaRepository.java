package ma.jurika.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RecoveryCodeJpaRepository extends JpaRepository<RecoveryCodeEntity, UUID> {

    @Query("SELECT r FROM RecoveryCodeEntity r WHERE r.userId = :userId AND r.usedAt IS NULL ORDER BY r.createdAt")
    List<RecoveryCodeEntity> findActive(@Param("userId") UUID userId);

    /** Lot L0 (E10d) : usage unique atomique ; 1 si consomme par cet appel, 0 sinon. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RecoveryCodeEntity r SET r.usedAt = :when WHERE r.id = :id AND r.usedAt IS NULL")
    int consommer(@Param("id") UUID id, @Param("when") java.time.Instant when);

    @Modifying
    @Query("DELETE FROM RecoveryCodeEntity r WHERE r.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
