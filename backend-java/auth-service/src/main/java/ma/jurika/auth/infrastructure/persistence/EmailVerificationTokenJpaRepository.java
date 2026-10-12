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

    /** Lot L5 (E10) : retrait des jetons ouverts d'un utilisateur pour un motif. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EmailVerificationTokenEntity t SET t.usedAt = :when "
            + "WHERE t.userId = :userId AND t.purpose = :purpose AND t.usedAt IS NULL")
    int retirerOuverts(@Param("userId") UUID userId, @Param("purpose") String purpose,
                       @Param("when") java.time.Instant when);

    /** Lot L5 (E10) : demande en attente la plus recente. */
    Optional<EmailVerificationTokenEntity>
        findFirstByUserIdAndPurposeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID userId, String purpose, java.time.Instant now);
}
