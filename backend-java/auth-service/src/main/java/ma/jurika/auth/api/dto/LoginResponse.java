package ma.jurika.auth.api.dto;

import java.time.Instant;
import java.util.UUID;

public record LoginResponse(
        boolean requires2fa,
        boolean requires2faSetup,
        boolean mustChangePassword,
        String twofaMethod,
        UUID userId,
        UUID workspaceId,
        String accessToken,
        String refreshToken,
        Instant accessExpiresAt,
        Instant refreshExpiresAt
) {
    public static LoginResponse twoFactorRequired(UUID userId, UUID workspaceId, String method) {
        return new LoginResponse(true, false, false, method, userId, workspaceId, null, null, null, null);
    }

    public static LoginResponse mustSetup2fa(UUID userId, UUID workspaceId,
                                              boolean mustChangePassword,
                                              String access, String refresh,
                                              Instant accessExp, Instant refreshExp) {
        return new LoginResponse(false, true, mustChangePassword, null, userId, workspaceId,
                access, refresh, accessExp, refreshExp);
    }

    public static LoginResponse fromTokens(UUID userId, UUID workspaceId,
                                            boolean mustChangePassword, String twofaMethod,
                                            String access, String refresh,
                                            Instant accessExp, Instant refreshExp) {
        return new LoginResponse(false, false, mustChangePassword, twofaMethod,
                userId, workspaceId, access, refresh, accessExp, refreshExp);
    }
}
