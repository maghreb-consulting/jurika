package ma.jurika.auth.api.dto;

import ma.jurika.auth.application.ListWorkspaceUsersUseCase.Member;

import java.time.Instant;
import java.util.UUID;

/**
 * BUG 6 (2026-06-07) — Reponse de GET /api/v1/auth/users (vue Equipe).
 */
public record WorkspaceUserDto(UUID userId, String email, String firstName, String lastName,
                                String phone, String role, String status,
                                boolean mustChangePassword,
                                Instant lastLoginAt, Instant createdAt) {

    public static WorkspaceUserDto from(Member m) {
        return new WorkspaceUserDto(m.userId(), m.email(), m.firstName(), m.lastName(),
                m.phone(), m.role().name(), m.status().name(), m.mustChangePassword(),
                m.lastLoginAt(), m.createdAt());
    }
}
