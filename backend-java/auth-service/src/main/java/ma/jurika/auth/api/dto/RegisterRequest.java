package ma.jurika.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Inscription classique : utilisateur saisit son mot de passe.
 * Workspace cree en ACTIVE, email pre-verifie, pas de force-change-password.
 */
public record RegisterRequest(
        @NotBlank @Size(max = 150) String workspaceName,
        @NotBlank @Email @Size(max = 150) String contactEmail,
        @NotNull UUID subscriptionId,
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @Size(max = 30) String phone,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 8, max = 100) String password
) {}
