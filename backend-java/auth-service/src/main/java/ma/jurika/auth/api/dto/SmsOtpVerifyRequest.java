package ma.jurika.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SmsOtpVerifyRequest(
        @NotBlank @Pattern(regexp = "PHONE_VERIFICATION|2FA_LOGIN") String purpose,
        @NotBlank @Pattern(regexp = "\\d{4,10}", message = "code doit etre numerique") String code
) {}
