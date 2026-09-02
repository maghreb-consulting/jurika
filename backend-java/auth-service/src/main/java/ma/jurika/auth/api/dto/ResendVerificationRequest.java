package ma.jurika.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendVerificationRequest(
        @NotBlank @Size(max = 12) String workspaceCode,
        @NotBlank @Email @Size(max = 150) String email
) {}
