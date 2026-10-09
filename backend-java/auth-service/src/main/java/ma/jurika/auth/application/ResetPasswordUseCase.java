package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.model.Workspace;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.PasswordHasher;
import ma.jurika.auth.domain.port.PasswordResetTokenRepository;
import ma.jurika.auth.domain.port.PasswordResetTokenRepository.StoredResetToken;
import ma.jurika.auth.domain.port.RefreshTokenRepository;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.domain.port.WorkspaceRepository;
import ma.jurika.auth.domain.service.PasswordPolicy;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.UnauthorizedException;
import ma.jurika.common.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

@Service
public class ResetPasswordUseCase {

    private static final Logger log = LoggerFactory.getLogger(ResetPasswordUseCase.class);
    private static final SecureRandom RNG = new SecureRandom();

    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final AuditLogger auditLogger;
    private final EmailSender emailSender;
    private final Duration tokenTtl;
    private final int ttlMinutesConfigured;
    private final String resetBaseUrl;

    public ResetPasswordUseCase(WorkspaceRepository workspaceRepository,
                                 UserRepository userRepository,
                                 PasswordResetTokenRepository resetTokenRepository,
                                 RefreshTokenRepository refreshTokenRepository,
                                 PasswordHasher passwordHasher,
                                 PasswordPolicy passwordPolicy,
                                 AuditLogger auditLogger,
                                 EmailSender emailSender,
                                 @Value("${jurika.auth.reset-token-ttl-minutes:30}") int ttlMinutes,
                                 @Value("${jurika.email.reset-base-url:${FRONTEND_URL:http://localhost:5173}/reset-password}") String resetBaseUrl) {
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.auditLogger = auditLogger;
        this.emailSender = emailSender;
        this.tokenTtl = Duration.ofMinutes(ttlMinutes);
        this.ttlMinutesConfigured = ttlMinutes;
        this.resetBaseUrl = resetBaseUrl;
    }

    public record RequestResult(String resetToken) {}

    @Transactional
    public Optional<RequestResult> request(String workspaceCode, String email, String ip, String ua) {
        Workspace workspace = workspaceRepository.findByCode(workspaceCode)
                .orElseThrow(() -> new NotFoundException("Code workspace inconnu"));
        TenantContext.set(workspace.id());

        Optional<User> userOpt = userRepository.findByWorkspaceAndEmail(workspace.id(), email);
        if (userOpt.isEmpty()) {
            // RG-AU silent : pas de leak email/workspace cote API
            return Optional.empty();
        }
        User user = userOpt.get();

        byte[] raw = new byte[32];
        RNG.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String hash = sha256Hex(token);

        resetTokenRepository.store(user.id(), workspace.id(), hash, Instant.now().plus(tokenTtl));
        auditLogger.log(workspace.id(), user.id(), "PASSWORD_RESET_REQUESTED", "user", user.id(),
                ip, ua, Map.of());

        // CRIT-1 (audit) : envoyer le lien de reset par email. Avant ce fix, le token etait
        // genere et stocke en base mais aucun email n'etait envoye -> flow forgot-password
        // totalement casse en production (user definitivement bloque sur "mot de passe oublie").
        String resetUrl = resetBaseUrl + "?token=" + token;
        Map<String, Object> emailVars = new HashMap<>();
        emailVars.put("brand", "JURIKA");
        emailVars.put("firstName", user.firstName() == null ? user.email() : user.firstName());
        emailVars.put("workspaceCode", workspace.code());
        emailVars.put("resetUrl", resetUrl);
        emailVars.put("ttlMinutes", String.valueOf(ttlMinutesConfigured));
        emailVars.put("ipAddress", ip == null ? "-" : ip);
        emailVars.put("userAgent", ua == null ? "-" : ua);
        emailVars.put("subject", "Reinitialiser votre mot de passe JURIKA");
        // BUG 7 (chore 2026-06-08) — fragment identityFooter + notif vers contact_email.
        emailVars.put("loginEmail", user.loginEmail());
        emailVars.put("contactEmail", user.contactEmail());
        try {
            emailSender.sendTemplated(
                    user.contactEmail(),
                    "Reinitialiser votre mot de passe JURIKA",
                    "password-reset-link",
                    emailVars);
        } catch (RuntimeException ex) {
            // Best-effort : on log mais on ne fail pas la requete (l'utilisateur a quand meme
            // recu un 202 Accepted silencieux, le token existe en base). Sentry alertera.
            log.error("Envoi email password-reset echoue pour {} (workspace={}) : {}",
                    user.email(), workspace.id(), ex.getMessage());
        }

        return Optional.of(new RequestResult(token));
    }

    @Transactional
    public void confirm(String resetToken, String newPassword, String ip, String ua) {
        passwordPolicy.check(newPassword);
        String hash = sha256Hex(resetToken);
        StoredResetToken stored = resetTokenRepository.findByHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Token invalide"));
        if (!stored.isUsable(Instant.now())) {
            throw new UnauthorizedException("Token expire ou deja utilise");
        }

        TenantContext.set(stored.workspaceId());
        // Lot L0 (E12b) : le jeton est consomme de facon ATOMIQUE et AVANT tout
        // effet ; deux confirmations simultanees ne changent le mot de passe qu'une fois.
        if (!resetTokenRepository.markUsed(hash, Instant.now())) {
            throw new UnauthorizedException("Token expire ou deja utilise");
        }
        userRepository.updatePasswordHash(stored.userId(), passwordHasher.hash(newPassword));
        refreshTokenRepository.revokeAllForUser(stored.userId(), Instant.now());

        auditLogger.log(stored.workspaceId(), stored.userId(), "PASSWORD_RESET_CONFIRMED", "user",
                stored.userId(), ip, ua, Map.of());
    }

    /** Empreinte du jeton de reinitialisation, telle que stockee (lot L0 : reutilisee par ContexteWorkspacePublic). */
    static String empreinte(String jeton) {
        return sha256Hex(jeton);
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
