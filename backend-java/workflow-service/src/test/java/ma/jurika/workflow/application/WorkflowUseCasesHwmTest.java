package ma.jurika.workflow.application;

import ma.jurika.workflow.domain.model.WorkflowProgress;
import ma.jurika.workflow.domain.model.WorkflowStatut;
import ma.jurika.workflow.domain.model.WorkflowType;
import ma.jurika.workflow.domain.port.WorkflowProgressRepository;
import ma.jurika.workflow.domain.strategy.WorkflowOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires de la logique High-Water Mark (PARTIE A 2026-06-04).
 * <p>
 * Garantit que :
 *  - {@link WorkflowUseCases#save} ne regresse jamais {@code currentStep}.
 *  - La navigation arriere (re-soumission d'une etape anterieure) ne casse pas la progression.
 *  - Les donnees d'etapes ulterieures sont preservees pendant une edition arriere.
 *
 * On utilise un fake in-memory du repository pour isoler la logique HWM
 * du JPA / Postgres.
 */
class WorkflowUseCasesHwmTest {

    private final UUID ws = UUID.randomUUID();
    private final UUID ticket = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();

    @Test
    @DisplayName("save() avec currentStep < HWM ne fait pas regresser la progression")
    void saveDoesNotRegressCurrentStep() {
        var initialState = new WorkflowProgress(
                UUID.randomUUID(), ws, ticket, WorkflowType.CREATION,
                3, 9, new HashMap<>(Map.of("step1", "data1", "step2", "data2")),
                WorkflowStatut.EN_COURS, user, null, Instant.now());
        FakeRepo repo = new FakeRepo(initialState);
        WorkflowUseCases uc = new WorkflowUseCases(repo, new WorkflowOrchestrator(java.util.List.of()), null, null, null, null);

        // L'utilisateur revient a step 1 et sauve un brouillon.
        WorkflowProgress after = uc.save(ws, ticket, 1, Map.of("step1", "data1-modifiee"), null);

        // HWM preservee a 3 -- pas de regression a 1.
        assertThat(after.currentStep()).isEqualTo(3);
        // Donnee step1 mise a jour.
        assertThat(after.data().get("step1")).isEqualTo("data1-modifiee");
        // Donnees step2 toujours la.
        assertThat(after.data().get("step2")).isEqualTo("data2");
    }

    @Test
    @DisplayName("save() avec currentStep > HWM fait avancer normalement")
    void saveAdvancesWhenHigher() {
        var initialState = new WorkflowProgress(
                UUID.randomUUID(), ws, ticket, WorkflowType.CREATION,
                2, 9, new HashMap<>(), WorkflowStatut.EN_COURS, user, null, Instant.now());
        FakeRepo repo = new FakeRepo(initialState);
        WorkflowUseCases uc = new WorkflowUseCases(repo, new WorkflowOrchestrator(java.util.List.of()), null, null, null, null);

        WorkflowProgress after = uc.save(ws, ticket, 4, Map.of("step3", "X"), null);

        assertThat(after.currentStep()).isEqualTo(4);
    }

    /** Fake in-memory repo pour isoler la logique HWM des couches infra. */
    static class FakeRepo implements WorkflowProgressRepository {
        private WorkflowProgress state;

        FakeRepo(WorkflowProgress initial) { this.state = initial; }

        @Override
        public Optional<WorkflowProgress> findByTicket(UUID workspaceId, UUID ticketId) {
            return Optional.of(state);
        }

        @Override
        public WorkflowProgress create(UUID workspaceId, UUID ticketId, WorkflowType type,
                                        int totalSteps, UUID startedById) {
            throw new UnsupportedOperationException("Not used in HWM tests");
        }

        @Override
        public WorkflowProgress save(UUID id, int currentStep, Map<String, Object> data,
                                      WorkflowStatut statut, Instant completedAt) {
            this.state = new WorkflowProgress(id, state.workspaceId(), state.ticketId(),
                    state.type(), currentStep, state.totalSteps(), data, statut,
                    state.startedById(), completedAt, Instant.now());
            return this.state;
        }
    }
}
