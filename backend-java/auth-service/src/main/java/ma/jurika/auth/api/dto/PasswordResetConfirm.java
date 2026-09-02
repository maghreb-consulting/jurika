package ma.jurika.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirm(
        @NotBlank String token,
        @NotBlank @Size(min = 10, max = 100) String newPassword
) {}
