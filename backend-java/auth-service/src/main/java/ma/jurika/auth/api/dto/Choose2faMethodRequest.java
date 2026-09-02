package ma.jurika.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record Choose2faMethodRequest(
        @NotBlank @Pattern(regexp = "SMS|TOTP", message = "method doit etre SMS ou TOTP") String method
) {}
