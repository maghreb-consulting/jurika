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

    @Modifying
    @Query("UPDATE SmsOtpCodeEntity s SET s.attempts = s.attempts + 1 WHERE s.id = :id")
    void incrementAttempts(@Param("id") UUID id);
}
