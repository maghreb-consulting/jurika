package ma.jurika.dataroom.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepotJpaRepository extends JpaRepository<DepotEntity, UUID> {

    List<DepotEntity> findByDossierIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID dossierId);

    Optional<DepotEntity> findByIdAndDeletedAtIsNull(UUID id);

    @Modifying
    @Query("""
        UPDATE DepotEntity d SET d.deletedAt = :when
        WHERE d.id = :id AND d.deletedAt IS NULL
    """)
    int softDelete(@Param("id") UUID id, @Param("when") Instant when);
}
