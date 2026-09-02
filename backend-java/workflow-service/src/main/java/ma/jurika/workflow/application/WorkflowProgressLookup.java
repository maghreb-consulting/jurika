package ma.jurika.workflow.application;

import ma.jurika.workflow.domain.model.WorkflowProgress;
import ma.jurika.workflow.domain.port.WorkflowProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Helper de lecture du workflow_progress dans une transaction NOUVELLE.
 *
 * <p>Utilise par {@link WorkflowUseCases#startOrResume} pour recuperer le row
 * d'un workflow existant APRES une {@code DataIntegrityViolationException}
 * declenchee par une course (ex: React StrictMode double-invoke en dev).
 * La transaction outer est alors marquee rollback-only ; un find direct
 * dans la meme transaction ne voit pas le row insere par l'appel concurrent.
 * En forcant {@code REQUIRES_NEW}, on obtient une session JPA propre qui
 * voit l'etat commit le plus recent.
 */
@Service
public class WorkflowProgressLookup {

    private final WorkflowProgressRepository repository;

    public WorkflowProgressLookup(WorkflowProgressRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<WorkflowProgress> findInNewTransaction(UUID workspaceId, UUID ticketId) {
        return repository.findByTicket(workspaceId, ticketId);
    }

    /**
     * Fix 2026-06-11 — Cree le workflow_progress dans une transaction propre.
     * <p>
     * Sans REQUIRES_NEW, l'INSERT s'execute dans la meme transaction que le caller :
     * si une autre transaction concurrente a deja insere la meme paire
     * (workspace_id, ticket_id), la constraint violation est levee, attrapee plus
     * loin dans le caller, mais la transaction est marquee rollback-only -> au
     * commit, Spring leve UnexpectedRollbackException (impossible a catcher dans
     * le caller). En isolant l'INSERT, un conflit se manifeste comme une
     * DataIntegrityViolationException PROPRE qui rollback uniquement la sous-
     * transaction ; la transaction caller reste vivante et peut faire son lookup
     * de recuperation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WorkflowProgress createInNewTransaction(UUID workspaceId, UUID ticketId,
                                                   ma.jurika.workflow.domain.model.WorkflowType type,
                                                   int totalSteps, UUID userId) {
        return repository.create(workspaceId, ticketId, type, totalSteps, userId);
    }
}
