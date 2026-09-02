package ma.jurika.workflow.domain.strategy;

import ma.jurika.common.exception.NotFoundException;
import ma.jurika.workflow.domain.model.WorkflowType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class WorkflowOrchestrator {

    private final Map<WorkflowType, WorkflowStrategy> strategies;

    public WorkflowOrchestrator(List<WorkflowStrategy> all) {
        EnumMap<WorkflowType, WorkflowStrategy> map = new EnumMap<>(WorkflowType.class);
        for (WorkflowStrategy s : all) {
            map.put(s.type(), s);
        }
        this.strategies = map;
    }

    public WorkflowStrategy strategyFor(WorkflowType type) {
        WorkflowStrategy s = strategies.get(type);
        if (s == null) {
            throw new NotFoundException("Aucune strategie pour workflow " + type);
        }
        return s;
    }
}
