package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.AuthTokens;
import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.model.Workspace;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.EncryptionService;
import ma.jurika.auth.domain.port.EventPublisher;
import ma.jurika.auth.domain.port.RefreshTokenRepository;
import ma.jurika.auth.domain.port.TokenIssuer;
import ma.jurika.auth.domain.port.TotpService;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.domain.port.WorkspaceRepository;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.UnauthorizedException;
import ma.jurika.common.security.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

@Service
public class Verify2faUseCase {

    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final TotpService totpService;
    private final EncryptionService encryptionService;
    private final TokenIssuer tokenIssuer;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EventPublisher eventPublisher;
    private final AuditLogger auditLogger;
    private final VerifySmsOtpUseCase verifySmsOtpUseCase;
    private final SessionLimitEnforcer sessionLimitEnforcer;
    private final int maxFailedAttempts;
    private final Duration lockDuration;

    public Verify2faUseCase(WorkspaceRepository workspaceRepository,
                            UserRepository userRepository,
                            TotpService totpService,
                            EncryptionService encryptionService,
                            TokenIssuer tokenIssuer,
                            RefreshTokenRepository refreshTokenRepository,
                            EventPublisher eventPublisher,
                            AuditLogger auditLogger,
                            VerifySmsOtpUseCase verifySmsOtpUseCase,
                            SessionLimitEnforcer sessionLimitEnforcer,
                            @Value("${jurika.auth.max-failed-attempts:5}") int maxFailedAttempts,
                            @Value("${jurika.auth.lock-duration-minutes:15}") int lockDurationMinutes) {
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.totpService = totpService;
        this.encryptionService = encryptionService;
        this.tokenIssuer = tokenIssuer;
        this.refreshTokenRepository = refreshTokenRepository;
        this.eventPublisher = eventPublisher;
        this.auditLogger = auditLogger;
        this.verifySmsOtpUseCase = verifySmsOtpUseCase;
        this.sessionLimitEnforcer = sessionLimitEnforcer;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDuration = Duration.ofMinutes(lockDurationMinutes);
    }

    /**
     * CRIT-3 (audit 2026-06-02) — {@code code} String (anciennement int totpCode) pour
     * supporter TOTP (6 chiffres) ET SMS (6 chiffres mais traite en string via le hasher
     * dedie). Le branchement TOTP/SMS se fait selon {@code user.twofaMethod()}.
     */
    public record Command(UUID userId, UUID workspaceId, String code,
                           String ipAddress, String userAgent) {}

    @Transactional
    public AuthTokens execute(Command cmd) {
        Workspace workspace = workspaceRepository.findById(cmd.workspaceId())
                .orElseThrow(() -> new NotFoundException("Workspace inconnu"));
        TenantContext.set(workspace.id());

        User user = userRepository.findById(cmd.userId())
                .orElseThrow(() -> new UnauthorizedException("Utilisateur inconnu"));

        // HIGH-5 (audit) : refuse si compte deja verrouille par accumulation d'echecs
        // (login OU 2FA confondus, RG-AU37).
        if (user.isLocked()) {
            auditLogger.log(workspace.id(), user.id(), "LOGIN_2FA_LOCKED", "user", user.id(),
                    cmd.ipAddress(), cmd.userAgent(), Map.of());
            throw new UnauthorizedException("Compte temporairement verrouille — reessayez plus tard");
        }

        // CRIT-3 fix : dispatch selon la methode 2FA reelle du user (avant ce fix,
        // seul TOTP etait gere -> les users SMS etaient bloques en boucle).
        String method = user.twofaMethod();
        if ("SMS".equals(method)) {
            // Delegue au pipeline SMS commun (verifie code, gere lockout failed_login_attempts,
            // marque OTP used, audit SMS_OTP_VERIFIED).
            verifySmsOtpUseCase.execute(new VerifySmsOtpUseCase.Command(
                    user.id(), workspace.id(), "2FA_LOGIN", cmd.code(),
                    cmd.ipAddress(), cmd.userAgent()));
        } else if ("TOTP".equals(method) || user.totpEnabled()) {
            if (user.totpSecretEncrypted() == null) {
                throw new UnauthorizedException("2FA TOTP non activee pour ce compte");
            }
            int totpCode;
            try {
                totpCode = Integer.parseInt(cmd.code().replaceAll("\\s+", ""));
            } catch (NumberFormatException e) {
                throw new UnauthorizedException("Code 2FA invalide");
            }
            String secret = encryptionService.decrypt(user.totpSecretEncrypted());
            // Lot L0 (E10d), anti-rejeu (RFC 6238, section 5.2) : le code n'est
            // accepte que si son pas de temps est enregistre ici, de facon
            // atomique, comme strictement posterieur au dernier pas accepte.
            OptionalLong pas = totpService.pasDuCode(secret, totpCode);
            boolean rejeu = pas.isPresent() && !userRepository.consommerPasTotp(user.id(), pas.getAsLong());
            if (pas.isEmpty() || rejeu) {
                // HIGH-5 (audit 2026-06-02) : incrementer failed_login_attempts pour
                // bloquer le brute-force online sur 10^6 codes TOTP. Apres N echecs,
                // lock le compte pour lock-duration-minutes (RG-AU37).
                short newAttempts = (short) (user.failedLoginAttempts() + 1);
                Instant lockUntil = newAttempts >= maxFailedAttempts
                        ? Instant.now().plus(lockDuration)
                        : null;
                userRepository.incrementFailedLogin(user.id(), lockUntil);
                auditLogger.log(workspace.id(), user.id(), "LOGIN_2FA_FAILED", "user", user.id(),
                        cmd.ipAddress(), cmd.userAgent(),
                        Map.of("method", "TOTP", "attempts", newAttempts,
                                "locked", lockUntil != null, "rejeu", rejeu));
                throw new UnauthorizedException(lockUntil != null
                        ? "Compte verrouille temporairement (trop d'echecs 2FA)"
                        : "Code 2FA invalide");
            }
            // Succes TOTP : reset le compteur d'echecs (SMS le fait deja dans VerifySmsOtpUseCase).
            userRepository.resetFailedLogin(user.id());
        } else {
            throw new UnauthorizedException("2FA non activee pour ce compte");
        }

        // HIGH-6 (audit 2026-06-02) : appliquer le quota max 5 sessions/user (RG-SAAS-12).
        // Avant ce fix, le chemin 2FA emettait des tokens sans cleanup -> bypass du quota
        // dispo via le chemin login direct (sans 2FA) qui lui applique enforce.
        Instant now = Instant.now();
        sessionLimitEnforcer.enforceBeforeIssuing(user.id(), now);

        AuthTokens tokens = tokenIssuer.issue(user);
        TokenIssuer.ParsedRefreshToken parsed = tokenIssuer.parseRefreshToken(tokens.refreshToken());
        refreshTokenRepository.store(user.id(), workspace.id(),
                parsed.hash(), tokens.refreshExpiresAt(), cmd.userAgent(), cmd.ipAddress());

        userRepository.registerSuccessfulLogin(user.id(), now);
        eventPublisher.publishUserLoggedIn(workspace.id(), user.id());
        auditLogger.log(workspace.id(), user.id(), "LOGIN_SUCCESS_2FA", "user", user.id(),
                cmd.ipAddress(), cmd.userAgent(), Map.of("method", method == null ? "TOTP" : method));

        return tokens;
    }
}
