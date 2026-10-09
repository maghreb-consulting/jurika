package ma.jurika.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenJpaRepository extends JpaRepository<PasswordResetTokenEntity, UUID> {

    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

    /** Lot L0 (E12b) : usage unique atomique ; 1 si consomme par cet appel, 0 sinon. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PasswordResetTokenEntity p SET p.usedAt = :when WHERE p.tokenHash = :hash AND p.usedAt IS NULL")
    int markUsed(@Param("hash") String hash, @Param("when") Instant when);
}
