package ma.jurika.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetRequest(
        @NotBlank @Pattern(regexp = "^JUR-[A-Z0-9]{5}$") String workspaceCode,
        @NotBlank @Email String email
) {}
