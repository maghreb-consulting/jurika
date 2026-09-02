package ma.jurika.auth.domain.port;

import java.util.UUID;

public interface EventPublisher {

    void publishUserRegistered(UUID workspaceId, UUID userId, String email);

    void publishUserLoggedIn(UUID workspaceId, UUID userId);

    void publishUser2faEnabled(UUID workspaceId, UUID userId);

    void publishWorkspaceCreated(UUID workspaceId, String code);
}
