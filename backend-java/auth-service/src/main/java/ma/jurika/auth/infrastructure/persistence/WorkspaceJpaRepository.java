package ma.jurika.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceJpaRepository extends JpaRepository<WorkspaceEntity, UUID> {
    Optional<WorkspaceEntity> findByCode(String code);
    boolean existsByCode(String code);

    /** Lot L0 (E13a) : unicite globale du code, hors RLS (fonction SECURITY DEFINER auth V34). */
    @org.springframework.data.jpa.repository.Query(value = "SELECT auth_code_workspace_existe(:code)", nativeQuery = true)
    boolean codeExisteGlobalement(@org.springframework.data.repository.query.Param("code") String code);

    /**
     * Sprint 11 TASK 3 — Workspaces dont le trial actif est echu.
     * Utilise l'index partiel idx_workspaces_trial_ends_at WHERE trial_status='TRIAL_ACTIVE'.
     */
    @Query("""
        SELECT w FROM WorkspaceEntity w
        WHERE w.trialStatus = 'TRIAL_ACTIVE'
          AND w.trialEndsAt IS NOT NULL
          AND w.trialEndsAt < :now
        """)
    List<WorkspaceEntity> findExpiredActiveTrials(@Param("now") Instant now);
}
