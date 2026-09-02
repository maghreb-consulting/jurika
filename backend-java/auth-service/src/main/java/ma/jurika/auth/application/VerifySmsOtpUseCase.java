package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.AuthTokens;
import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.model.UserStatus;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.RefreshTokenRepository;
import ma.jurika.auth.domain.port.SmsOtpCodeRepository;
import ma.jurika.auth.domain.port.SmsOtpCodeRepository.StoredOtp;
import ma.jurika.auth.domain.port.TokenIssuer;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.domain.service.VerificationTokenHasher;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.UnauthorizedException;
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
 * Verifie un code SMS OTP saisi par l'utilisateur.
 * <p>
 * Selon le {@code purpose} :
 * <ul>
 *     <li>{@code PHONE_VERIFICATION} : marque {@code phone_verified_at} + active {@code twofa_method=SMS}</li>
 *     <li>{@code 2FA_LOGIN} : valide le 2eme facteur a la connexion (le caller doit ensuite emettre les JWT)</li>
 * </ul>
 */
@Service
public class VerifySmsOtpUseCase {

    private final UserRepository userRepository;
    private final SmsOtpCodeRepository otpRepository;
    private final VerificationTokenHasher hasher;
    private final AuditLogger auditLogger;
    private final TokenIssuer tokenIssuer;
    private final RefreshTokenRepository refreshTokenRepository;
    private final int maxFailedAttempts;
    private final Duration lockDuration;

    public VerifySmsOtpUseCase(UserRepository userRepository,
                                SmsOtpCodeRepository otpRepository,
                                VerificationTokenHasher hasher,
                                AuditLogger auditLogger,
                                TokenIssuer tokenIssuer,
                                RefreshTokenRepository refreshTokenRepository,
                                @Value("${jurika.auth.max-failed-attempts:5}") int maxFailedAttempts,
                                @Value("${jurika.auth.lock-duration-minutes:15}") int lockDurationMinutes) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.hasher = hasher;
        this.auditLogger = auditLogger;
        this.tokenIssuer = tokenIssuer;
        this.refreshTokenRepository = refreshTokenRepository;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDuration = Duration.ofMinutes(lockDurationMinutes);
    }

    public record Command(UUID userId, UUID workspaceId, String purpose, String code,
                           String ipAddress, String userAgent) {}

    /**
     * Renvoie un NOUVEAU couple de tokens (r2s=false) UNIQUEMENT quand
     * {@code purpose=2FA_SETUP}. Pour les autres purposes (PHONE_VERIFICATION,
     * 2FA_LOGIN, PASSWORD_RESET), renvoie {@code null} — le caller (login flow,
     * profile, password reset) gere l'emission de tokens lui-meme.
     *
     * <p>Hotfix 2026-06-04 (meme classe que ChangePasswordUseCase + Setup2faUseCase) :
     * sans ce nouveau token, le user qui vient de configurer SMS comme 2FA continue
     * avec son JWT initial (claim {@code r2s=true}) et
     * {@link ma.jurika.auth.infrastructure.security.Setup2faRequiredEnforcer} le
     * bloque a 403 sur tous les endpoints metier.
     */
    @Transactional
    public AuthTokens execute(Command cmd) {
        TenantContext.set(cmd.workspaceId());

        StoredOtp otp = otpRepository.findLatestActive(cmd.userId(), cmd.purpose())
                .orElseThrow(() -> new NotFoundException("Aucun code SMS actif"));

        if (otp.isUsed()) {
            throw new ValidationException("SMS_OTP_ALREADY_USED");
        }
        if (otp.isExpired()) {
            throw new ValidationException("SMS_OTP_EXPIRED");
        }
        if (otp.isExhausted()) {
            throw new ValidationException("SMS_OTP_TOO_MANY_ATTEMPTS");
        }

        String submittedHash = hasher.hash(cmd.code());
        if (!submittedHash.equals(otp.codeHash())) {
            otpRepository.incrementAttempts(otp.id());
            // RG-AU37 : compter dans failed_login_attempts pour locker le compte
            // apres maxFailedAttempts echecs successifs (login OU 2FA confondus).
            Instant lockUntil = (otp.attempts() + 1 >= maxFailedAttempts)
                    ? Instant.now().plus(lockDuration)
                    : null;
            userRepository.incrementFailedLogin(cmd.userId(), lockUntil);
            auditLogger.log(cmd.workspaceId(), cmd.userId(), "SMS_OTP_FAILED", "user",
                    cmd.userId(), cmd.ipAddress(), cmd.userAgent(),
                    Map.of("attempts", String.valueOf(otp.attempts() + 1),
                            "locked", String.valueOf(lockUntil != null)));
            throw new UnauthorizedException(lockUntil != null
                    ? "Compte verrouille temporairement (trop d'echecs 2FA)"
                    : "Code SMS invalide");
        }

        otpRepository.markUsed(otp.id(), Instant.now());
        userRepository.resetFailedLogin(cmd.userId());

        // Effets metier selon purpose
        if ("PHONE_VERIFICATION".equals(cmd.purpose())) {
            userRepository.markPhoneVerified(cmd.userId(), Instant.now());
            userRepository.updateTwofaMethod(cmd.userId(), "SMS");
        }

        auditLogger.log(cmd.workspaceId(), cmd.userId(), "SMS_OTP_VERIFIED", "user",
                cmd.userId(), cmd.ipAddress(), cmd.userAgent(),
                Map.of("purpose", cmd.purpose()));

        // Hotfix 2026-06-04 — emettre les nouveaux tokens UNIQUEMENT pour le
        // setup 2FA. Les autres flows (PHONE_VERIFICATION, 2FA_LOGIN,
        // PASSWORD_RESET) ne touchent pas le claim r2s ou ont leur propre
        // mecanisme d'emission.
        if ("2FA_SETUP".equals(cmd.purpose())) {
            User refreshed = userRepository.findById(cmd.userId())
                    .orElseThrow(() -> new NotFoundException("Utilisateur disparu apres setup SMS"));

            // BUG 6 (decision 2026-06-08 chore) — Fin d'onboarding via SMS : si
            // le user est encore PENDING ET mcp=false ET 2FA configuree (SMS),
            // on bascule ACTIVE. Symetrique de Setup2faUseCase.confirm() pour
            // le path TOTP.
            if (refreshed.status() == UserStatus.PENDING
                    && !refreshed.mustChangePassword()
                    && !refreshed.requires2faSetup()) {
                userRepository.setStatus(refreshed.id(), UserStatus.ACTIVE);
                auditLogger.log(refreshed.workspaceId(), refreshed.id(),
                        "USER_ACTIVATED_BY_ONBOARDING", "user", refreshed.id(),
                        cmd.ipAddress(), cmd.userAgent(),
                        Map.of("previousStatus", "PENDING", "onboardingStep", "2fa_confirm_sms"));
                refreshed = userRepository.findById(refreshed.id())
                        .orElseThrow(() -> new NotFoundException("Utilisateur disparu apres setStatus"));
            }

            AuthTokens tokens = tokenIssuer.issue(refreshed);
            TokenIssuer.ParsedRefreshToken parsed = tokenIssuer.parseRefreshToken(tokens.refreshToken());
            refreshTokenRepository.store(refreshed.id(), refreshed.workspaceId(),
                    parsed.hash(), tokens.refreshExpiresAt(), cmd.userAgent(), cmd.ipAddress());
            return tokens;
        }
        return null;
    }
}
