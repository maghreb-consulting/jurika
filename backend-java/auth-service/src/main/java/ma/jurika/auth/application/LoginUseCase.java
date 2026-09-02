package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.AuthTokens;
import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.model.Workspace;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.EventPublisher;
import ma.jurika.auth.domain.port.PasswordHasher;
import ma.jurika.auth.domain.port.RefreshTokenRepository;
import ma.jurika.auth.domain.port.TokenIssuer;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.domain.port.WorkspaceRepository;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.UnauthorizedException;
import ma.jurika.common.observability.BusinessMetrics;
import ma.jurika.common.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class LoginUseCase {

    private static final Logger log = LoggerFactory.getLogger(LoginUseCase.class);
    private static final DateTimeFormatter LOGIN_AT_FMT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm 'GMT'")
            .withZone(ZoneId.of("Africa/Casablanca"));

    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionLimitEnforcer sessionLimitEnforcer;
    private final EventPublisher eventPublisher;
    private final AuditLogger auditLogger;
    private final BusinessMetrics businessMetrics;
    private final EmailSender emailSender;
    private final int maxFailedAttempts;
    private final Duration lockDuration;
    private final Duration newDeviceLookback;
    private final String securityPageUrl;
    /** BUG 7 — feature flag {@code jurika.auth.login-email-enabled}. */
    private final boolean loginEmailEnabled;

    public LoginUseCase(WorkspaceRepository workspaceRepository,
                        UserRepository userRepository,
                        PasswordHasher passwordHasher,
                        TokenIssuer tokenIssuer,
                        RefreshTokenRepository refreshTokenRepository,
                        SessionLimitEnforcer sessionLimitEnforcer,
                        EventPublisher eventPublisher,
                        AuditLogger auditLogger,
                        BusinessMetrics businessMetrics,
                        EmailSender emailSender,
                        @Value("${jurika.auth.max-failed-attempts:5}") int maxFailedAttempts,
                        @Value("${jurika.auth.lock-duration-minutes:15}") int lockDurationMinutes,
                        @Value("${jurika.auth.new-device-lookback-days:30}") int newDeviceLookbackDays,
                        @Value("${jurika.email.security-page-url:${FRONTEND_URL:http://localhost:5173}/account/security}")
                        String securityPageUrl,
                        @Value("${jurika.auth.login-email-enabled:true}") boolean loginEmailEnabled) {
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.refreshTokenRepository = refreshTokenRepository;
        this.sessionLimitEnforcer = sessionLimitEnforcer;
        this.eventPublisher = eventPublisher;
        this.auditLogger = auditLogger;
        this.businessMetrics = businessMetrics;
        this.emailSender = emailSender;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDuration = Duration.ofMinutes(lockDurationMinutes);
        this.newDeviceLookback = Duration.ofDays(newDeviceLookbackDays);
        this.securityPageUrl = securityPageUrl;
        this.loginEmailEnabled = loginEmailEnabled;
    }

    public record Command(String workspaceCode, String email, String rawPassword,
                           String ipAddress, String userAgent) {}

    public record Result(boolean requires2fa, boolean requires2faSetup, boolean mustChangePassword,
                          String twofaMethod, AuthTokens tokens, UUID userId, UUID workspaceId) {}

    @Transactional
    public Result execute(Command cmd) {
        Workspace workspace = workspaceRepository.findByCode(cmd.workspaceCode())
                .orElseThrow(() -> new NotFoundException("Code workspace inconnu"));
        if (!workspace.isActive()) {
            throw new UnauthorizedException("Workspace inactif");
        }
        TenantContext.set(workspace.id());

        // BUG 7 (2026-06-08) — On essaie d'abord {@code login_email}. Si flag
        // off, on garde l'ancien comportement (lookup par email). En transition,
        // pour les comptes legacy backfillees en V28 (login_email = email), les
        // deux chemins convergent — l'utilisateur peut saisir son ancien
        // identifiant ET ca matche login_email = email. Pour les NOUVEAUX
        // comptes (signup/invite apres BUG 7), seul login_email matche, donc
        // un fallback par email permettrait theoriquement d'utiliser le
        // contact_email comme identifiant -- on l'evite explicitement quand le
        // flag est on.
        User user;
        if (loginEmailEnabled) {
            user = userRepository.findByWorkspaceAndLoginEmail(workspace.id(), cmd.email())
                    .orElseThrow(() -> new UnauthorizedException("Identifiants invalides"));
        } else {
            user = userRepository.findByWorkspaceAndEmail(workspace.id(), cmd.email())
                    .orElseThrow(() -> new UnauthorizedException("Identifiants invalides"));
        }

        if (!user.active()) {
            throw new UnauthorizedException("Compte desactive");
        }
        if (user.isLocked()) {
            throw new UnauthorizedException("Compte temporairement verrouille — reessayez plus tard");
        }

        if (!passwordHasher.matches(cmd.rawPassword(), user.passwordHash())) {
            short attempts = (short) (user.failedLoginAttempts() + 1);
            Instant lockUntil = attempts >= maxFailedAttempts
                    ? Instant.now().plus(lockDuration)
                    : null;
            userRepository.incrementFailedLogin(user.id(), lockUntil);
            auditLogger.log(workspace.id(), user.id(), "LOGIN_FAILED", "user", user.id(),
                    cmd.ipAddress(), cmd.userAgent(), Map.of("attempts", attempts));
            businessMetrics.loginFailed(lockUntil != null ? "locked" : "bad_password");
            throw new UnauthorizedException("Identifiants invalides");
        }

        userRepository.resetFailedLogin(user.id());

        // 2FA seulement si activee explicitement par l'utilisateur (optionnel, classique)
        if (user.totpEnabled() && "TOTP".equals(user.twofaMethod())) {
            auditLogger.log(workspace.id(), user.id(), "LOGIN_2FA_REQUIRED", "user", user.id(),
                    cmd.ipAddress(), cmd.userAgent(), Map.of("method", "TOTP"));
            return new Result(true, false, false, "TOTP",
                    null, user.id(), workspace.id());
        }

        if ("SMS".equals(user.twofaMethod()) && user.isPhoneVerified()) {
            auditLogger.log(workspace.id(), user.id(), "LOGIN_2FA_REQUIRED", "user", user.id(),
                    cmd.ipAddress(), cmd.userAgent(), Map.of("method", "SMS"));
            return new Result(true, false, false, "SMS",
                    null, user.id(), workspace.id());
        }

        Instant now = Instant.now();

        // Sprint 3 / TASK 2 — detection nouvelle IP/UA AVANT de stocker le nouveau
        // refresh token (sinon il compte comme "deja vu" pour lui-meme).
        boolean newDevice = isNewDevice(user.id(), cmd.ipAddress(), cmd.userAgent(), now);

        sessionLimitEnforcer.enforceBeforeIssuing(user.id(), now);

        AuthTokens tokens = tokenIssuer.issue(user);
        TokenIssuer.ParsedRefreshToken parsed = tokenIssuer.parseRefreshToken(tokens.refreshToken());
        refreshTokenRepository.store(user.id(), workspace.id(),
                parsed.hash(), tokens.refreshExpiresAt(), cmd.userAgent(), cmd.ipAddress());

        userRepository.registerSuccessfulLogin(user.id(), now);
        // BUG 6 (decision 2026-06-08 chore) — la transition PENDING -> ACTIVE
        // a ete deplacee a la fin de l'onboarding (Setup2faUseCase.confirm() +
        // VerifySmsOtpUseCase purpose=2FA_SETUP). Regle : PENDING tant que
        // must_change_password=true OU 2FA non configuree. Le 1er login reussi
        // n'est PAS la fin de l'onboarding — on attend la 2FA complete.
        eventPublisher.publishUserLoggedIn(workspace.id(), user.id());
        auditLogger.log(workspace.id(), user.id(), "LOGIN_SUCCESS", "user", user.id(),
                cmd.ipAddress(), cmd.userAgent(), Map.of());
        businessMetrics.loginSuccess();

        if (newDevice) {
            notifyNewDevice(workspace, user, cmd, now);
        }

        // CRIT-2 (audit) : requires2faSetup etait hardcode false -> un user sans 2FA
        // accedait a toute l'app sans jamais activer 2FA (RG-AU30 silencieusement
        // contournee). On expose maintenant la verite du domaine. Le frontend doit
        // rediriger /auth/setup-2fa si true, et le filtre serveur (Setup2faRequiredFilter)
        // bloque les autres routes tant que ce n'est pas fait.
        // Sprint 14 D-S3-01 : mustChangePassword aligne sur le domaine (etait hardcode false).
        return new Result(false, user.requires2faSetup(), user.mustChangePassword(),
                user.twofaMethod(), tokens, user.id(), workspace.id());
    }

    /**
     * Sprint 3 / TASK 2 — Vrai si {@code userId} n'a aucun refresh token actif ou
     * recemment expire emis depuis ce couple {@code ip + user-agent} dans la fenetre
     * de lookback (defaut 30 jours).
     */
    private boolean isNewDevice(UUID userId, String ip, String userAgent, Instant now) {
        if (ip == null || ip.isBlank() || userAgent == null || userAgent.isBlank()) {
            return false;
        }
        Instant since = now.minus(newDeviceLookback);
        return !refreshTokenRepository.hasRecentSessionFromDevice(userId, ip, userAgent, since);
    }

    /**
     * Envoie l'email "login-new-device" et trace dans l'audit log.
     * Toute exception est avalee (mailing best-effort, ne doit pas bloquer le login).
     */
    private void notifyNewDevice(Workspace workspace, User user, Command cmd, Instant now) {
        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("ipAddress", cmd.ipAddress());
        auditDetails.put("userAgent", truncate(cmd.userAgent(), 200));
        auditLogger.log(workspace.id(), user.id(), "LOGIN_NEW_DEVICE", "user", user.id(),
                cmd.ipAddress(), cmd.userAgent(), auditDetails);

        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("firstName", user.firstName() != null ? user.firstName() : user.email());
            vars.put("loginAt", LOGIN_AT_FMT.format(now));
            vars.put("ipAddress", cmd.ipAddress());
            vars.put("userAgent", truncate(cmd.userAgent(), 120));
            vars.put("location", null); // GeoIP differe a Sprint 14
            vars.put("securityUrl", securityPageUrl);
            vars.put("unsubscribeUrl", null);
            // BUG 7 (chore 2026-06-08) — fragment identityFooter
            vars.put("loginEmail", user.loginEmail());
            vars.put("contactEmail", user.contactEmail());
            // BUG 7 — destinataire = contact_email (notification, pas identifiant).
            emailSender.sendTemplated(user.contactEmail(),
                    "JURIKA - nouvelle connexion a votre compte",
                    "login-new-device",
                    vars);
        } catch (RuntimeException ex) {
            log.warn("Echec envoi email login-new-device pour user={} : {}",
                    user.id(), ex.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
