package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.AuthTokens;
import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.model.Workspace;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.EventPublisher;
import ma.jurika.auth.domain.port.PasswordHasher;
import ma.jurika.auth.domain.port.RecoveryCodeRateLimiter;
import ma.jurika.auth.domain.port.RecoveryCodeRepository;
import ma.jurika.auth.domain.port.RecoveryCodeRepository.StoredRecoveryCode;
import ma.jurika.auth.domain.port.RefreshTokenRepository;
import ma.jurika.auth.domain.port.TokenIssuer;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.domain.port.WorkspaceRepository;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.TooManyRequestsException;
import ma.jurika.common.exception.UnauthorizedException;
import ma.jurika.common.observability.BusinessMetrics;
import ma.jurika.common.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sprint 14 bis / TASK B4 — verifier un code de recuperation 2FA et emettre
 * directement les tokens d'auth (court-circuit du TOTP/SMS) (RG-AU41, RG-AU42).
 *
 * <p><b>RG-AU41</b> (single-use) : chaque code n'est valide qu'une fois ; un code
 * deja consomme renvoie 401 sans information distinctive (eviter user enum).
 *
 * <p><b>RG-AU42</b> (rate limit) : 5 tentatives par fenetre glissante de 15 min,
 * scope par {@code workspaceCode + email + ip}. Au-dela : 429.
 *
 * <p>Audit : {@code RECOVERY_CODE_USED} (succes) ou {@code RECOVERY_CODE_FAILED}
 * (echec OU 429). Si succes : email "recovery-code-used" envoye au user (alerte
 * de securite, RG-SAAS-08).
 */
@Service
public class VerifyRecoveryCodeUseCase {

    private static final Logger log = LoggerFactory.getLogger(VerifyRecoveryCodeUseCase.class);
    private static final DateTimeFormatter USED_AT_FMT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm 'GMT'")
            .withZone(ZoneId.of("Africa/Casablanca"));
    /** Reponse generique : meme message pour code inconnu, deja utilise, email inconnu. */
    private static final String GENERIC_AUTH_ERROR = "Code de recuperation invalide";

    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final RecoveryCodeRateLimiter rateLimiter;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionLimitEnforcer sessionLimitEnforcer;
    private final EventPublisher eventPublisher;
    private final AuditLogger auditLogger;
    private final BusinessMetrics businessMetrics;
    private final EmailSender emailSender;
    private final String securityPageUrl;

    public VerifyRecoveryCodeUseCase(WorkspaceRepository workspaceRepository,
                                     UserRepository userRepository,
                                     RecoveryCodeRepository recoveryCodeRepository,
                                     RecoveryCodeRateLimiter rateLimiter,
                                     PasswordHasher passwordHasher,
                                     TokenIssuer tokenIssuer,
                                     RefreshTokenRepository refreshTokenRepository,
                                     SessionLimitEnforcer sessionLimitEnforcer,
                                     EventPublisher eventPublisher,
                                     AuditLogger auditLogger,
                                     BusinessMetrics businessMetrics,
                                     EmailSender emailSender,
                                     @Value("${jurika.email.security-page-url:${FRONTEND_URL:http://localhost:5173}/account/security}")
                                     String securityPageUrl) {
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.rateLimiter = rateLimiter;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.refreshTokenRepository = refreshTokenRepository;
        this.sessionLimitEnforcer = sessionLimitEnforcer;
        this.eventPublisher = eventPublisher;
        this.auditLogger = auditLogger;
        this.businessMetrics = businessMetrics;
        this.emailSender = emailSender;
        this.securityPageUrl = securityPageUrl;
    }

    public record Command(String workspaceCode, String email, String code,
                          String ipAddress, String userAgent) {}

    @Transactional
    public AuthTokens execute(Command cmd) {
        String rateKey = buildKey(cmd);

        // 1. Rate limit AVANT toute lookup (evite oracle d'enum + brute-force)
        if (!rateLimiter.tryAcquire(rateKey)) {
            // Audit best-effort sans workspace si on n'arrive pas a resoudre.
            auditRateLimited(cmd);
            throw new TooManyRequestsException("RECOVERY_CODE_RATE_LIMITED",
                    "Trop de tentatives. Reessayez dans 15 minutes.");
        }

        Workspace workspace = workspaceRepository.findByCode(cmd.workspaceCode()).orElse(null);
        if (workspace == null || !workspace.isActive()) {
            rateLimiter.recordFailure(rateKey);
            throw new UnauthorizedException(GENERIC_AUTH_ERROR);
        }
        TenantContext.set(workspace.id());

        User user = userRepository.findByWorkspaceAndEmail(workspace.id(), cmd.email()).orElse(null);
        if (user == null || !user.active()) {
            rateLimiter.recordFailure(rateKey);
            auditLogger.log(workspace.id(), null, "RECOVERY_CODE_FAILED", "user", null,
                    cmd.ipAddress(), cmd.userAgent(), Map.of("reason", "USER_UNKNOWN"));
            throw new UnauthorizedException(GENERIC_AUTH_ERROR);
        }

        // 2. Match incremental sur les codes actifs (non utilises).
        //    Le code est normalise (uppercase, sans espaces) pour aligner la saisie UI.
        String normalized = normalize(cmd.code());
        List<StoredRecoveryCode> active = recoveryCodeRepository.findActive(user.id());
        StoredRecoveryCode hit = null;
        for (StoredRecoveryCode candidate : active) {
            if (candidate.isUsed()) continue; // ceinture + bretelles
            if (passwordHasher.matches(normalized, candidate.codeHash())) {
                hit = candidate;
                break;
            }
        }

        if (hit == null) {
            rateLimiter.recordFailure(rateKey);
            auditLogger.log(workspace.id(), user.id(), "RECOVERY_CODE_FAILED", "user", user.id(),
                    cmd.ipAddress(), cmd.userAgent(),
                    Map.of("reason", active.isEmpty() ? "NO_ACTIVE_CODES" : "CODE_MISMATCH"));
            businessMetrics.loginFailed("recovery_code_invalid");
            throw new UnauthorizedException(GENERIC_AUTH_ERROR);
        }

        // 3. Code valide : marque used (single-use), audit, emet les tokens.
        Instant now = Instant.now();
        // Lot L0 (E10d) : usage unique ATOMIQUE. Deux requetes simultanees avec le
        // meme code trouvent toutes deux le code actif ; une seule le consomme.
        if (!recoveryCodeRepository.markUsed(hit.id(), now)) {
            rateLimiter.recordFailure(rateKey);
            auditLogger.log(workspace.id(), user.id(), "RECOVERY_CODE_FAILED", "user", user.id(),
                    cmd.ipAddress(), cmd.userAgent(), Map.of("reason", "ALREADY_USED"));
            businessMetrics.loginFailed("recovery_code_reused");
            throw new UnauthorizedException(GENERIC_AUTH_ERROR);
        }
        rateLimiter.reset(rateKey);

        sessionLimitEnforcer.enforceBeforeIssuing(user.id(), now);
        AuthTokens tokens = tokenIssuer.issue(user);
        TokenIssuer.ParsedRefreshToken parsed = tokenIssuer.parseRefreshToken(tokens.refreshToken());
        refreshTokenRepository.store(user.id(), workspace.id(),
                parsed.hash(), tokens.refreshExpiresAt(), cmd.userAgent(), cmd.ipAddress());

        userRepository.registerSuccessfulLogin(user.id(), now);
        eventPublisher.publishUserLoggedIn(workspace.id(), user.id());

        int remaining = (int) active.stream().filter(c -> !c.isUsed()).count() - 1;
        auditLogger.log(workspace.id(), user.id(), "RECOVERY_CODE_USED", "user", user.id(),
                cmd.ipAddress(), cmd.userAgent(),
                Map.of("remaining", String.valueOf(Math.max(remaining, 0))));
        businessMetrics.loginSuccess();

        sendUsedAlertEmail(user, cmd, now, remaining);

        return tokens;
    }

    private String buildKey(Command cmd) {
        return (safe(cmd.workspaceCode()) + ":" + safe(cmd.email()) + ":" + safe(cmd.ipAddress()))
                .toLowerCase();
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String normalize(String code) {
        if (code == null) return "";
        return code.trim().replace(" ", "").toUpperCase();
    }

    private void auditRateLimited(Command cmd) {
        // Le workspace peut etre inconnu (typo) : on resout best-effort pour scope
        // le log multi-tenant, sinon on log avec workspace_id=NULL via RLS bypass.
        Workspace ws = workspaceRepository.findByCode(cmd.workspaceCode()).orElse(null);
        UUID workspaceId = ws != null ? ws.id() : null;
        UUID userId = null;
        if (ws != null) {
            TenantContext.set(ws.id());
            userId = userRepository.findByWorkspaceAndEmail(ws.id(), cmd.email())
                    .map(User::id).orElse(null);
        }
        auditLogger.log(workspaceId, userId, "RECOVERY_CODE_FAILED", "user", userId,
                cmd.ipAddress(), cmd.userAgent(),
                Map.of("reason", "RATE_LIMITED"));
    }

    private void sendUsedAlertEmail(User user, Command cmd, Instant when, int remaining) {
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("firstName", user.firstName() != null ? user.firstName() : user.email());
            vars.put("usedAt", USED_AT_FMT.format(when));
            vars.put("ipAddress", cmd.ipAddress());
            vars.put("userAgent", cmd.userAgent());
            vars.put("remaining", Math.max(remaining, 0));
            vars.put("securityUrl", securityPageUrl);
            vars.put("unsubscribeUrl", null);
            // BUG 7 (chore 2026-06-08) — fragment identityFooter
            vars.put("loginEmail", user.loginEmail());
            vars.put("contactEmail", user.contactEmail());
            // BUG 7 (2026-06-08) — notif vers contact_email.
            emailSender.sendTemplated(user.contactEmail(),
                    "JURIKA - code de recuperation 2FA utilise",
                    "recovery-code-used",
                    vars);
        } catch (RuntimeException ex) {
            log.warn("Echec envoi email recovery-code-used user={} : {}",
                    user.id(), ex.getMessage());
        }
    }
}
