package ma.jurika.auth.infrastructure.trial;

import ma.jurika.auth.infrastructure.persistence.WorkspaceEntity;
import ma.jurika.auth.infrastructure.persistence.WorkspaceJpaRepository;
import ma.jurika.common.trial.TrialAccessChecker;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation locale (JPA) de {@link TrialAccessChecker} pour auth-service.
 *
 * <p>Auth-service possede la table {@code workspaces} : pas besoin de Feign,
 * on lit directement les colonnes {@code trial_status / trial_ends_at /
 * selected_plan} de {@link WorkspaceEntity}. Marquee {@code @Component},
 * elle court-circuite la {@code RemoteTrialAccessChecker} de
 * {@code TrialRemoteAutoConfiguration} via le {@code @ConditionalOnMissingBean}
 * de ce dernier.
 *
 * <p>Workspaces legacy (trial_status NULL) -> Optional.empty(), ce qui
 * autorise par defaut cote {@code TrialSoftLockFilter}.
 */
@Component
public class TrialAccessCheckerJpaImpl implements TrialAccessChecker {

    private final WorkspaceJpaRepository workspaceRepo;

    public TrialAccessCheckerJpaImpl(WorkspaceJpaRepository workspaceRepo) {
        this.workspaceRepo = workspaceRepo;
    }

    @Override
    public Optional<TrialState> getState(UUID workspaceId) {
        return workspaceRepo.findById(workspaceId)
                .filter(ws -> ws.getTrialStatus() != null)
                .map(this::toState);
    }

    private TrialState toState(WorkspaceEntity ws) {
        Instant endsAt = ws.getTrialEndsAt();
        long daysRemaining = 0L;
        if (endsAt != null) {
            long seconds = Duration.between(Instant.now(), endsAt).getSeconds();
            daysRemaining = Math.max(0L, seconds / 86_400L);
        }
        return new TrialState(
                ws.getId(),
                ws.getTrialStatus(),
                daysRemaining,
                endsAt,
                ws.getSelectedPlan()
        );
    }
}
