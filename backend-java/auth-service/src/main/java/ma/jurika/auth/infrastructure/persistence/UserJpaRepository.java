package ma.jurika.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByWorkspaceIdAndEmail(UUID workspaceId, String email);

    /** BUG 7 (2026-06-08) — Lookup auth principal sur login_email. */
    Optional<UserEntity> findByWorkspaceIdAndLoginEmail(UUID workspaceId, String loginEmail);

    /** BUG 7 (2026-06-08) — Utilise pour la generation avec suffixe collisions. */
    boolean existsByWorkspaceIdAndLoginEmail(UUID workspaceId, String loginEmail);

    /** BUG 7 (2026-06-08) — Mise a jour ciblee du contact_email seul. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserEntity u SET u.contactEmail = :contactEmail WHERE u.id = :id")
    void updateContactEmail(@Param("id") UUID id, @Param("contactEmail") String contactEmail);

    /**
     * Sprint 11 TASK 5 — Premier SUPERVISEUR du workspace (l'admin cree au signup).
     * Utilise par TrialEmailScheduler comme destinataire des emails de sequence trial.
     */
    @Query("SELECT u.email FROM UserEntity u WHERE u.workspaceId = :workspaceId AND u.role = 'SUPERVISEUR' ORDER BY u.createdAt ASC")
    java.util.List<String> findSuperviseurEmailsByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    @Modifying
    @Query("UPDATE UserEntity u SET u.passwordHash = :hash WHERE u.id = :id")
    void updatePasswordHash(@Param("id") UUID id, @Param("hash") String hash);

    // Fix BUG4 (memoire 2026-06-07) : sans clearAutomatically+flushAutomatically,
    // tokenIssuer.issue(refreshed) relit l'entite cachee (mcp=true) -> boucle 403
    // PASSWORD_CHANGE_REQUIRED apres /change-password.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserEntity u SET u.passwordHash = :hash, u.mustChangePassword = false WHERE u.id = :id")
    void updatePasswordHashAndClearMustChange(@Param("id") UUID id, @Param("hash") String hash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserEntity u SET u.mustChangePassword = :value WHERE u.id = :id")
    void setMustChangePassword(@Param("id") UUID id, @Param("value") boolean value);

    @Modifying
    @Query("UPDATE UserEntity u SET u.emailVerifiedAt = :when WHERE u.id = :id")
    void markEmailVerified(@Param("id") UUID id, @Param("when") Instant when);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserEntity u SET u.twofaMethod = :method WHERE u.id = :id")
    void updateTwofaMethod(@Param("id") UUID id, @Param("method") String method);

    @Modifying
    @Query("UPDATE UserEntity u SET u.phoneVerifiedAt = :when WHERE u.id = :id")
    void markPhoneVerified(@Param("id") UUID id, @Param("when") Instant when);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserEntity u SET u.totpSecretEncrypted = :secret, u.totpEnabled = :enabled WHERE u.id = :id")
    void updateTotpSecret(@Param("id") UUID id, @Param("secret") String secret, @Param("enabled") boolean enabled);

    @Modifying
    @Query("UPDATE UserEntity u SET u.lastLoginAt = :when, u.failedLoginAttempts = 0, u.lockedUntil = null WHERE u.id = :id")
    void registerSuccessfulLogin(@Param("id") UUID id, @Param("when") Instant when);

    @Modifying
    @Query("UPDATE UserEntity u SET u.failedLoginAttempts = u.failedLoginAttempts + 1, u.lockedUntil = :lockUntil WHERE u.id = :id")
    void incrementFailedLogin(@Param("id") UUID id, @Param("lockUntil") Instant lockUntil);

    @Modifying
    @Query("UPDATE UserEntity u SET u.failedLoginAttempts = 0, u.lockedUntil = null WHERE u.id = :id")
    void resetFailedLogin(@Param("id") UUID id);

    /**
     * BUG 6 (2026-06-07) — Met a jour le statut (PENDING / ACTIVE / INACTIVE).
     * Le trigger Postgres (V27) recalcule {@code is_active} automatiquement.
     * Flush + clear pour eviter le piege L1 cache deja documente sur les autres
     * mutations (voir memoire fix-auth-onboarding-emails-2026-06-07 § BUG4).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserEntity u SET u.status = :status WHERE u.id = :id")
    void setStatus(@Param("id") UUID id, @Param("status") String status);

    /**
     * BUG 6 (2026-06-07) — Liste les membres internes d'un workspace pour la
     * page Equipe. La policy RLS filtre deja {@code workspace_id}, mais on
     * garde le predicat explicite (defense-in-depth, cf memoire
     * multi-tenant-defense-in-depth-2026-06-05).
     */
    @Query("SELECT u FROM UserEntity u WHERE u.workspaceId = :workspaceId "
            + "AND u.role IN :roles ORDER BY u.createdAt ASC")
    List<UserEntity> findByWorkspaceIdAndRoleIn(@Param("workspaceId") UUID workspaceId,
                                                @Param("roles") Collection<String> roles);
}
