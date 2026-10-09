package ma.jurika.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SmsOtpCodeJpaRepository extends JpaRepository<SmsOtpCodeEntity, UUID> {

    @Query("SELECT s FROM SmsOtpCodeEntity s WHERE s.userId = :userId AND s.purpose = :purpose " +
           "AND s.usedAt IS NULL ORDER BY s.createdAt DESC LIMIT 1")
    Optional<SmsOtpCodeEntity> findLatestActive(@Param("userId") UUID userId, @Param("purpose") String purpose);

    /** Lot L0 (E12b) : usage unique atomique ; 1 si consomme par cet appel, 0 sinon. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE SmsOtpCodeEntity t SET t.usedAt = :when WHERE t.id = :id AND t.usedAt IS NULL")
    int consommer(@Param("id") UUID id, @Param("when") java.time.Instant when);

    @Modifying
    @Query("UPDATE SmsOtpCodeEntity s SET s.attempts = s.attempts + 1 WHERE s.id = :id")
    void incrementAttempts(@Param("id") UUID id);
}
