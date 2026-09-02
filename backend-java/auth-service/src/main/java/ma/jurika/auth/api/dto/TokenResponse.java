package ma.jurika.auth.api.dto;

import java.time.Instant;
import java.util.UUID;

public record TokenResponse(
        UUID userId,
        UUID workspaceId,
        String accessToken,
        String refreshToken,
        Instant accessExpiresAt,
        Instant refreshExpiresAt
) {}
