package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.SmsOtpCodeRepository;
import ma.jurika.auth.domain.port.SmsSender;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.domain.service.VerificationTokenHasher;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Genere un code OTP 6 chiffres + l'envoie par SMS au numero du user.
 * <p>
 * Purposes supportes :
 * <ul>
 *     <li>{@code PHONE_VERIFICATION} : 1ere verif du numero lors du setup 2FA SMS</li>
 *     <li>{@code 2FA_LOGIN} : code 2FA au login (apres MDP)</li>
 * </ul>
 * Rate limit : 1 envoi par minute par user (anti-spam).
 */
@Service
public class SendSmsOtpUseCase {

    private final UserRepository userRepository;
    private final SmsOtpCodeRepository otpRepository;
    private final VerificationTokenHasher hasher;
    private final SmsSender smsSender;
    private final AuditLogger auditLogger;

    private final long otpTtlMinutes;
    private final int maxAttempts;
    private final int digits;

    public SendSmsOtpUseCase(UserRepository userRepository,
                              SmsOtpCodeRepository otpRepository,
                              VerificationTokenHasher hasher,
                              SmsSender smsSender,
                              AuditLogger auditLogger,
                              @Value("${jurika.auth.sms-otp-ttl-minutes:5}") long otpTtlMinutes,
                              @Value("${jurika.auth.sms-otp-max-attempts:5}") int maxAttempts,
                              @Value("${jurika.auth.sms-otp-digits:6}") int digits) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.hasher = hasher;
        this.smsSender = smsSender;
        this.auditLogger = auditLogger;
        this.otpTtlMinutes = otpTtlMinutes;
        this.maxAttempts = maxAttempts;
        this.digits = digits;
    }

    public record Command(UUID userId, UUID workspaceId, String purpose,
                           String ipAddress, String userAgent) {}

    public record Result(UUID otpId, String maskedPhone, Instant expiresAt) {}

    @Transactional
    public Result execute(Command cmd) {
        TenantContext.set(cmd.workspaceId());

        User user = userRepository.findById(cmd.userId())
                .orElseThrow(() -> new NotFoundException("Utilisateur inconnu"));

        if (user.phone() == null || user.phone().isBlank()) {
            throw new ValidationException("PHONE_NOT_SET");
        }

        // Rate limit basique : si OTP existant non utilise et cree il y a moins de 60s, refuser
        otpRepository.findLatestActive(cmd.userId(), cmd.purpose()).ifPresent(existing -> {
            Instant minNext = existing.expiresAt().minus(Duration.ofMinutes(otpTtlMinutes - 1));
            if (Instant.now().isBefore(minNext)) {
                throw new ValidationException("SMS_RATE_LIMIT_WAIT_60_SECONDS");
            }
        });

        String rawCode = hasher.generateNumericCode(digits);
        String codeHash = hasher.hash(rawCode);
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(otpTtlMinutes));

        UUID otpId = otpRepository.create(
                cmd.workspaceId(), cmd.userId(), user.phone(), codeHash,
                cmd.purpose(), expiresAt, maxAttempts);

        String body = String.format("JURIKA : votre code de verification est %s. Valable %d minutes.",
                rawCode, otpTtlMinutes);
        smsSender.send(user.phone(), body);

        auditLogger.log(cmd.workspaceId(), cmd.userId(), "SMS_OTP_SENT", "user",
                cmd.userId(), cmd.ipAddress(), cmd.userAgent(),
                Map.of("purpose", cmd.purpose(), "otpId", otpId.toString()));

        return new Result(otpId, maskPhone(user.phone()), expiresAt);
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) return "***";
        return phone.substring(0, phone.length() - 4).replaceAll("\\d", "*") + phone.substring(phone.length() - 4);
    }
}
