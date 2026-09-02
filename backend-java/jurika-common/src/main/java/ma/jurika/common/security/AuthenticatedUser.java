package ma.jurika.common.security;

import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        UUID workspaceId,
        String email,
        Role role
) {}
