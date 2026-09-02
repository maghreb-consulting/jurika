package ma.jurika.workflow.domain.strategy;

import ma.jurika.common.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractWorkflow implements WorkflowStrategy {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public final StepResult executeStep(StepContext ctx) {
        validateStepRange(ctx.step());
        beforeStep(ctx);
        StepResult result = doExecuteStep(ctx);
        afterStep(ctx, result);
        return result;
    }

    protected abstract StepResult doExecuteStep(StepContext ctx);

    protected void beforeStep(StepContext ctx) {
        log.debug("Workflow {} - executing step {} for ticket {}", type(), ctx.step(), ctx.ticketId());
    }

    protected void afterStep(StepContext ctx, StepResult result) {
        log.debug("Workflow {} - step {} result canAdvance={}", type(), ctx.step(), result.canAdvance());
    }

    private void validateStepRange(int step) {
        if (step < 1 || step > totalSteps()) {
            throw new ValidationException(
                    "Etape " + step + " hors plage (1.." + totalSteps() + ")");
        }
    }
}
