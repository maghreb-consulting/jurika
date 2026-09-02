package ma.jurika.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record WorkspaceCheckRequest(
        @NotBlank
        @Pattern(regexp = "^JUR-[A-Z0-9]{5}$", message = "Format attendu : JUR-XXXXX")
        String workspaceCode
) {}
