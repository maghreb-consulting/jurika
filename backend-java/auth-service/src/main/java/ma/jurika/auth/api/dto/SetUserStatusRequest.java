package ma.jurika.auth.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * BUG 6 (2026-06-07) — Body du PATCH /api/v1/auth/users/{userId}/status.
 * {@code active=true} -> statut ACTIVE ; {@code active=false} -> statut INACTIVE.
 */
public record SetUserStatusRequest(@NotNull Boolean active) {}
