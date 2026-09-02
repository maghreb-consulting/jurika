package fr.maghreb.gje.events;

import org.springframework.context.ApplicationEvent;
import java.util.UUID;

public class WorkspaceNear90PercentEvent extends ApplicationEvent {
    private final UUID workspaceId;
    private final long currentUsageBytes;
    private final long limitBytes;
    private final double percentageUsed;

    public WorkspaceNear90PercentEvent(
            Object source, 
            UUID workspaceId, 
            long currentUsageBytes, 
            long limitBytes, 
            double percentageUsed) {
        super(source);
        this.workspaceId = workspaceId;
        this.currentUsageBytes = currentUsageBytes;
        this.limitBytes = limitBytes;
        this.percentageUsed = percentageUsed;
    }

    public UUID getWorkspaceId() { return workspaceId; }
    public long getCurrentUsageBytes() { return currentUsageBytes; }
    public long getLimitBytes() { return limitBytes; }
    public double getPercentageUsed() { return percentageUsed; }
}
