package ma.jurika.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(min = 8, max = 100) String oldPassword,
        @NotBlank @Size(min = 12, max = 100) String newPassword,
        @NotBlank @Size(min = 12, max = 100) String confirmPassword
) {}
