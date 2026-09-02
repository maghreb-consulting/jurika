package ma.jurika.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

public record AuthTokens(
        String accessToken,
        String refreshToken,
        Instant accessExpiresAt,
        Instant refreshExpiresAt,
        UUID userId,
        UUID workspaceId
) {}
