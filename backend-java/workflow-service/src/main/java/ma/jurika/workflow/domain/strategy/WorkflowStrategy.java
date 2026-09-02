package ma.jurika.workflow.domain.strategy;

import ma.jurika.workflow.domain.model.WorkflowType;

public interface WorkflowStrategy {

    WorkflowType type();

    StepResult executeStep(StepContext context);

    default int totalSteps() {
        return type().totalSteps();
    }
}
