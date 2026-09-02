package ma.jurika.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SmsOtpSendRequest(
        @NotBlank @Pattern(regexp = "PHONE_VERIFICATION|2FA_LOGIN") String purpose
) {}
