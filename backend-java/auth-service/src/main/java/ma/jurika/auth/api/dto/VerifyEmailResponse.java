package ma.jurika.auth.api.dto;

import java.util.UUID;

public record VerifyEmailResponse(UUID workspaceId, UUID userId, String email, String message) {}
