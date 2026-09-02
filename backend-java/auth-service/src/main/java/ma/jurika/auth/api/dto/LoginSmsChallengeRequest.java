package ma.jurika.auth.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * CRIT-3 (audit 2026-06-02) : permet a un user mid-login (post-credentials,
 * pre-2FA) de demander l'envoi de son code SMS sans JWT.
 *
 * <p>Caller a deja recu {@code userId} et {@code workspaceId} en reponse de
 * {@code POST /auth/login} quand {@code requires2fa=true} et {@code twofaMethod=SMS}.
 */
public record LoginSmsChallengeRequest(
        @NotNull UUID userId,
        @NotNull UUID workspaceId
) {}
