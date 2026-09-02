package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.AuthTokens;
import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.PasswordHasher;
import ma.jurika.auth.domain.port.RefreshTokenRepository;
import ma.jurika.auth.domain.port.TokenIssuer;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.domain.service.PasswordPolicy;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.UnauthorizedException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Change le MDP d'un utilisateur authentifie.
 * <p>
 * Verifications :
 * <ol>
 *     <li>L'ancien MDP est correct</li>
 *     <li>Le nouveau respecte la PasswordPolicy (12 chars, complexite)</li>
 *     <li>Le nouveau est different de l'ancien</li>
 *     <li>Le nouveau correspond a la confirmation</li>
 * </ol>
 * Effet : update password_hash + must_change_password=FALSE.
 *
 * <p><b>Sprint hotfix 2026-06-04 — token refresh</b> : ce use case emet desormais
 * un NOUVEAU couple access+refresh token apres la mise a jour du MDP. Avant ce
 * fix, le caller continuait avec son ancien token (claim {@code mcp=true}) et
 * {@code ChangePasswordEnforcer} le bloquait a {@code 403 PASSWORD_CHANGE_REQUIRED}
 * sur l'etape suivante (setup 2FA) — meme si la DB avait deja
 * {@code must_change_password=FALSE}. Voir
 * {@link ma.jurika.auth.infrastructure.security.ChangePasswordEnforcer} qui lit
 * le flag depuis le claim JWT (pas la DB).
 */
@Service
public class ChangePasswordUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChangePasswordUseCase.class);
    private static final DateTimeFormatter CHANGED_AT_FMT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm 'GMT'")
            .withZone(ZoneId.of("Africa/Casablanca"));

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final AuditLogger auditLogger;
    private final EmailSender emailSender;
    private final TokenIssuer tokenIssuer;
    private final RefreshTokenRepository refreshTokenRepository;

    public ChangePasswordUseCase(UserRepository userRepository,
                                  PasswordHasher passwordHasher,
                                  PasswordPolicy passwordPolicy,
                                  AuditLogger auditLogger,
                                  EmailSender emailSender,
                                  TokenIssuer tokenIssuer,
                                  RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.auditLogger = auditLogger;
        this.emailSender = emailSender;
        this.tokenIssuer = tokenIssuer;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public record Command(
            UUID userId,
            UUID workspaceId,
            String oldPassword,
            String newPassword,
            String confirmPassword,
            String ipAddress,
            String userAgent
    ) {}

    /**
     * Execute le changement de MDP et renvoie un NOUVEAU couple de tokens
     * (claim {@code mcp=false} reflechi). Le front DOIT remplacer son token
     * stocke par celui-ci AVANT toute requete suivante — sinon le filtre
     * {@link ma.jurika.auth.infrastructure.security.ChangePasswordEnforcer}
     * relit le claim {@code mcp=true} de l'ancien token et renvoie 403.
     */
    @Transactional
    public AuthTokens execute(Command cmd) {
        if (!cmd.newPassword().equals(cmd.confirmPassword())) {
            throw new ValidationException("PASSWORD_CONFIRMATION_MISMATCH");
        }
        if (cmd.newPassword().equals(cmd.oldPassword())) {
            throw new ValidationException("NEW_PASSWORD_SAME_AS_OLD");
        }

        passwordPolicy.check(cmd.newPassword());

        TenantContext.set(cmd.workspaceId());

        User user = userRepository.findById(cmd.userId())
                .orElseThrow(() -> new NotFoundException("Utilisateur inconnu"));

        if (!passwordHasher.matches(cmd.oldPassword(), user.passwordHash())) {
            throw new UnauthorizedException("Ancien mot de passe incorrect");
        }

        // Snapshot du flag AVANT update — l'audit log doit dire si c'etait un
        // changement force (1er login) ou une rotation volontaire.
        boolean wasForced = user.mustChangePassword();

        String newHash = passwordHasher.hash(cmd.newPassword());
        userRepository.updatePasswordHashAndClearMustChange(user.id(), newHash);

        auditLogger.log(cmd.workspaceId(), cmd.userId(), "PASSWORD_CHANGED", "user",
                cmd.userId(), cmd.ipAddress(), cmd.userAgent(),
                Map.of("source", wasForced ? "FORCED_AT_FIRST_LOGIN" : "USER_INITIATED"));

        // Sprint 3 / TASK 2 — notification password-changed (best-effort)
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("firstName", user.firstName() != null ? user.firstName() : user.email());
            vars.put("changedAt", CHANGED_AT_FMT.format(Instant.now()));
            vars.put("ipAddress", cmd.ipAddress());
            vars.put("userAgent", truncate(cmd.userAgent(), 120));
            vars.put("unsubscribeUrl", null);
            // BUG 7 (chore 2026-06-08) — fragment identityFooter de _base.html
            vars.put("loginEmail", user.loginEmail());
            vars.put("contactEmail", user.contactEmail());
            // BUG 7 (2026-06-08) — notif vers contact_email (perso) pas login_email.
            emailSender.sendTemplated(user.contactEmail(),
                    "JURIKA - votre mot de passe a ete modifie",
                    "password-changed",
                    vars);
        } catch (RuntimeException ex) {
            log.warn("Echec envoi email password-changed pour user={} : {}",
                    user.id(), ex.getMessage());
        }

        // Hotfix 2026-06-04 — re-emettre les tokens avec mcp=false.
        // On re-charge le user pour que tokenIssuer.issue() lise les vraies
        // valeurs DB (must_change_password=FALSE) et non les flags de l'objet
        // en memoire dans cette transaction.
        User refreshed = userRepository.findById(user.id())
                .orElseThrow(() -> new NotFoundException("Utilisateur disparu apres update"));
        AuthTokens tokens = tokenIssuer.issue(refreshed);
        TokenIssuer.ParsedRefreshToken parsed = tokenIssuer.parseRefreshToken(tokens.refreshToken());
        refreshTokenRepository.store(refreshed.id(), refreshed.workspaceId(),
                parsed.hash(), tokens.refreshExpiresAt(), cmd.userAgent(), cmd.ipAddress());
        return tokens;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
