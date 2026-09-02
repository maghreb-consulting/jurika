package ma.jurika.workflow.infrastructure.persistence;

import ma.jurika.workflow.domain.model.WorkflowProgress;
import ma.jurika.workflow.domain.model.WorkflowStatut;
import ma.jurika.workflow.domain.model.WorkflowType;
import ma.jurika.workflow.domain.port.WorkflowProgressRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class WorkflowProgressAdapter implements WorkflowProgressRepository {

    private final WorkflowProgressJpaRepository jpa;

    public WorkflowProgressAdapter(WorkflowProgressJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<WorkflowProgress> findByTicket(UUID workspaceId, UUID ticketId) {
        return jpa.findByWorkspaceIdAndTicketId(workspaceId, ticketId).map(this::toDomain);
    }

    @Override
    public WorkflowProgress create(UUID workspaceId, UUID ticketId, WorkflowType type,
                                    int totalSteps, UUID startedById) {
        WorkflowProgressEntity e = new WorkflowProgressEntity();
        e.setWorkspaceId(workspaceId);
        e.setTicketId(ticketId);
        e.setWorkflowType(type.name());
        e.setTotalSteps((short) totalSteps);
        e.setStartedById(startedById);
        return toDomain(jpa.save(e));
    }

    @Override
    public WorkflowProgress save(UUID id, int currentStep, Map<String, Object> data,
                                  WorkflowStatut statut, Instant completedAt) {
        WorkflowProgressEntity e = jpa.findById(id).orElseThrow();
        e.setCurrentStep((short) currentStep);
        e.setData(data);
        e.setStatut(statut.name());
        e.setCompletedAt(completedAt);
        return toDomain(jpa.save(e));
    }

    private WorkflowProgress toDomain(WorkflowProgressEntity e) {
        WorkflowType type = WorkflowType.valueOf(e.getWorkflowType());
        // 2026-08-11 — Migration en vol du nombre d'etapes : on rapporte TOUJOURS le
        // total courant de l'enum (source de verite), et non le snapshot persiste a
        // la creation. Un workflow demarre sous MODIFICATION(4) est ainsi vu sur 5
        // etapes des la reprise, sans migration de donnees ni incoherence de
        // finalisation (finalStepValidated = step == totalSteps).
        int totalSteps = type.totalSteps();
        // 2026-08-13 (lot DIVERS) — les refontes SUCCURSALE_MA (11 -> 5) et
        // SUCCURSALE_ETR (13 -> 5) font DIMINUER le total : un workflow en cours
        // pouvait se retrouver avec currentStep > totalSteps, donc bloque hors du
        // parcours (« Etape inconnue ») et impossible a finaliser
        // (finalStepValidated = step == totalSteps ne serait jamais vrai). On borne
        // la position a la derniere etape du parcours courant : l'employe reprend a
        // la synthese et re-valide les etapes dont le format a change.
        int currentStep = Math.min(e.getCurrentStep(), totalSteps);
        return new WorkflowProgress(e.getId(), e.getWorkspaceId(), e.getTicketId(),
                type, currentStep, totalSteps,
                e.getData(), WorkflowStatut.valueOf(e.getStatut()), e.getStartedById(),
                e.getCompletedAt(), e.getUpdatedAt());
    }
}
