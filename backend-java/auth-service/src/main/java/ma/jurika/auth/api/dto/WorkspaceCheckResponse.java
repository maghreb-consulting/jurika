package ma.jurika.auth.api.dto;

import java.util.UUID;

public record WorkspaceCheckResponse(UUID workspaceId, String name) {}
