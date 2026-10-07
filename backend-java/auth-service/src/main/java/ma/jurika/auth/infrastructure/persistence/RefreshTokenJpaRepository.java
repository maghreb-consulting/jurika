package ma.jurika.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.revokedAt = :when WHERE r.tokenHash = :hash AND r.revokedAt IS NULL")
    void revoke(@Param("hash") String hash, @Param("when") Instant when);

    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.revokedAt = :when WHERE r.userId = :uid AND r.revokedAt IS NULL")
    void revokeAllForUser(@Param("uid") UUID userId, @Param("when") Instant when);

    @Query("SELECT COUNT(r) FROM RefreshTokenEntity r " +
           "WHERE r.userId = :uid AND r.revokedAt IS NULL AND r.expiresAt > :now")
    long countActiveByUser(@Param("uid") UUID userId, @Param("now") Instant now);

    @Query("SELECT r FROM RefreshTokenEntity r " +
           "WHERE r.userId = :uid AND r.revokedAt IS NULL AND r.expiresAt > :now " +
           "ORDER BY r.issuedAt ASC")
    java.util.List<RefreshTokenEntity> findOldestActive(@Param("uid") UUID userId,
                                                        @Param("now") Instant now,
                                                        org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Query("DELETE FROM RefreshTokenEntity r WHERE r.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);

    /** Lot L0 (E13b) : purge transverse, hors RLS (fonction SECURITY DEFINER auth V34). */
    @Query(value = "SELECT auth_purge_jetons_refresh(:cutoff)", nativeQuery = true)
    int purgerExpires(@Param("cutoff") Instant cutoff);

    /**
     * Sprint 3 / TASK 2 — detection nouvelle IP/UA. Recherche un refresh_token deja
     * emis depuis le meme {@code ip + user-agent} depuis {@code since}. Comparaison
     * case-insensitive sur user-agent (les navigateurs renvoient toujours la meme
     * casse, mais on se protege contre les divergences mineures).
     */
    @Query("SELECT COUNT(r) FROM RefreshTokenEntity r " +
           "WHERE r.userId = :uid " +
           "AND r.ipAddress = :ip " +
           "AND LOWER(r.userAgent) = LOWER(:ua) " +
           "AND r.issuedAt >= :since")
    long countByUserAndDeviceSince(@Param("uid") UUID userId,
                                    @Param("ip") String ipAddress,
                                    @Param("ua") String userAgent,
                                    @Param("since") Instant since);
}
