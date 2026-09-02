package ma.jurika.auth.api.dto;

import java.time.Instant;
import java.util.UUID;

public record SmsOtpSendResponse(UUID otpId, String maskedPhone, Instant expiresAt) {}
