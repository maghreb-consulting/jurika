package ma.jurika.workflow.domain.strategy;

import java.util.Map;

public record StepResult(
        Map<String, Object> stepData,
        boolean canAdvance,
        String message
) {
    public static StepResult ok(Map<String, Object> data) {
        return new StepResult(data, true, null);
    }

    public static StepResult blocked(String message) {
        return new StepResult(Map.of(), false, message);
    }
}
