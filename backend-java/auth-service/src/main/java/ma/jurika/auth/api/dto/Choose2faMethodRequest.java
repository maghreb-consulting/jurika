package ma.jurika.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record Choose2faMethodRequest(
        @NotBlank @Pattern(regexp = "SMS|TOTP", message = "M\u00e9thode inconnue : choisissez SMS ou application d\u2019authentification.") String method
) {}
