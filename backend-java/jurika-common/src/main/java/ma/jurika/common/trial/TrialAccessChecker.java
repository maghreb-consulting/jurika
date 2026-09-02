package ma.jurika.common.trial;

import java.util.Optional;
import java.util.UUID;

/**
 * Sprint 11 TASK 3 — Port abstrait pour la verification de l'etat trial
 * d'un workspace, utilise par {@link TrialSoftLockFilter} dans n'importe
 * quel microservice qui veut activer le soft-lock 402.
 *
 * <p>L'implementation par defaut vit dans auth-service (qui possede la table
 * workspaces). Les autres microservices peuvent :
 *  - importer le filter via jurika-common
 *  - injecter un impl qui appelle l'auth-service par REST (latence)
 *  - OU injecter un impl qui lit un cache local Redis populated by Rabbit
 *    event {@code workspace.trial.expired}
 *
 * <p>Le scope Sprint 11 active le filter UNIQUEMENT dans auth-service
 * (TrialAccessCheckerJpaImpl + filter @Bean dans WebMvc config). Les autres
 * microservices wireront le filter Sprint 12 quand Billing sera en place.
 */
public interface TrialAccessChecker {

    /**
     * Retourne l'etat trial courant du workspace, OU {@link Optional#empty()}
     * si le workspace n'a pas de trial actif (ex: workspaces legacy
     * pre-Sprint 11 → {@code trial_status IS NULL} → autorise par defaut).
     */
    Optional<TrialState> getState(UUID workspaceId);

    /**
     * Etat compact du trial, immutable.
     */
    record TrialState(
            UUID workspaceId,
            String status,          // TRIAL_ACTIVE | TRIAL_EXPIRED | CONVERTED | CANCELLED
            long daysRemaining,
            java.time.Instant endsAt,
            String selectedPlan
    ) {
        public boolean isExpired() {
            return "TRIAL_EXPIRED".equals(status);
        }
        public boolean isActive() {
            return "TRIAL_ACTIVE".equals(status);
        }
    }
}
