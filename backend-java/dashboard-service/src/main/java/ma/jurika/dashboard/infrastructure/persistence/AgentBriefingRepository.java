package ma.jurika.dashboard.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Lot IA-1 — Repo des briefings copilote. Un employe n'accede qu'a SON dernier
 * briefing (scoping applique dans le service + RLS/workspace).
 */
public interface AgentBriefingRepository extends JpaRepository<AgentBriefingEntity, UUID> {

    Optional<AgentBriefingEntity> findFirstByWorkspaceIdAndEmployeeIdOrderByCreatedAtDesc(
            UUID workspaceId, UUID employeeId);
}
