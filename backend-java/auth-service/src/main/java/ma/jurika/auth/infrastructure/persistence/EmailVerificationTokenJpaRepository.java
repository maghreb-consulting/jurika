package ma.jurika.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenJpaRepository
        extends JpaRepository<EmailVerificationTokenEntity, UUID> {

    Optional<EmailVerificationTokenEntity> findByTokenHash(String tokenHash);

    /** Lot L0 (E12b) : usage unique atomique ; 1 si consomme par cet appel, 0 sinon. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EmailVerificationTokenEntity t SET t.usedAt = :when WHERE t.id = :id AND t.usedAt IS NULL")
    int consommer(@Param("id") UUID id, @Param("when") java.time.Instant when);
}
