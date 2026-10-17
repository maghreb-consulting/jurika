package ma.jurika.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Recette R2 (2026-10-17) : {@code 2FA_SETUP} admis (activation de la double authentification par SMS). */
public record SmsOtpVerifyRequest(
        @NotBlank @Pattern(regexp = "PHONE_VERIFICATION|2FA_SETUP|2FA_LOGIN") String purpose,
        @NotBlank @Pattern(regexp = "\\d{4,10}", message = "Le code ne contient que des chiffres.") String code
) {}
