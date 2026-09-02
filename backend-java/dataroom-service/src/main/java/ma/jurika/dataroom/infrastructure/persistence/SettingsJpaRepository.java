package ma.jurika.dataroom.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SettingsJpaRepository extends JpaRepository<SettingsEntity, UUID> {

    Optional<SettingsEntity> findByClientLinkToken(UUID token);

    @Modifying
    @Query("""
        UPDATE SettingsEntity s
           SET s.accessCount = s.accessCount + 1, s.lastAccessedAt = :when
         WHERE s.dossierId = :id
    """)
    int incrementAccess(@Param("id") UUID id, @Param("when") Instant when);
}
