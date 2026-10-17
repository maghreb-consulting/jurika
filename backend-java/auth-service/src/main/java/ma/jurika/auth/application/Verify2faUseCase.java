package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.AuthTokens;
import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.model.Workspace;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.EventPublisher;
import ma.jurika.auth.domain.port.RefreshTokenRepository;
import ma.jurika.auth.domain.port.TokenIssuer;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.domain.port.WorkspaceRepository;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.UnauthorizedException;
import ma.jurika.common.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class Verify2faUseCase {

    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final TokenIssuer tokenIssuer;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EventPublisher eventPublisher;
    private final AuditLogger auditLogger;
    private final VerifySmsOtpUseCase verifySmsOtpUseCase;
    private final SessionLimitEnforcer sessionLimitEnforcer;
    private final ControleMethodeActuelle controleMethodeActuelle;

    public Verify2faUseCase(WorkspaceRepository workspaceRepository,
                            UserRepository userRepository,
                            TokenIssuer tokenIssuer,
                            RefreshTokenRepository refreshTokenRepository,
                            EventPublisher eventPublisher,
                            AuditLogger auditLogger,
                            VerifySmsOtpUseCase verifySmsOtpUseCase,
                            SessionLimitEnforcer sessionLimitEnforcer,
                            ControleMethodeActuelle controleMethodeActuelle) {
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.tokenIssuer = tokenIssuer;
        this.refreshTokenRepository = refreshTokenRepository;
        this.eventPublisher = eventPublisher;
        this.auditLogger = auditLogger;
        this.verifySmsOtpUseCase = verifySmsOtpUseCase;
        this.sessionLimitEnforcer = sessionLimitEnforcer;
        this.controleMethodeActuelle = controleMethodeActuelle;
    }

    /**
     * CRIT-3 (audit 2026-06-02) — {@code code} String (anciennement int totpCode) pour
     * supporter TOTP (6 chiffres) ET SMS (6 chiffres mais traite en string via le hasher
     * dedie). Le branchement TOTP/SMS se fait selon {@code user.twofaMethod()}.
     */
    public record Command(UUID userId, UUID workspaceId, String code,
                           String ipAddress, String userAgent) {}

    // Recette R2 (2026-10-17) : un echec (UnauthorizedException) ne doit pas annuler le compte
    // des echecs ecrit juste avant (sinon la limite des essais, RG-AU37, etait inoperante :
    // compteur remis a l'etat d'avant a chaque refus).
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthTokens execute(Command cmd) {
        Workspace workspace = workspaceRepository.findById(cmd.workspaceId())
                .orElseThrow(() -> new NotFoundException("Workspace introuvable."));
        TenantContext.set(workspace.id());

        User user = userRepository.findById(cmd.userId())
                .orElseThrow(() -> new UnauthorizedException("Utilisateur introuvable."));

        // HIGH-5 (audit) : refuse si compte deja verrouille par accumulation d'echecs
        // (login OU 2FA confondus, RG-AU37).
        if (user.isLocked()) {
            auditLogger.log(workspace.id(), user.id(), "LOGIN_2FA_LOCKED", "user", user.id(),
                    cmd.ipAddress(), cmd.userAgent(), Map.of());
            throw new UnauthorizedException("Compte verrouill\u00e9 temporairement : r\u00e9essayez plus tard.");
        }

        // CRIT-3 fix : dispatch selon la methode 2FA reelle du user (avant ce fix,
        // seul TOTP etait gere -> les users SMS etaient bloques en boucle).
        // Recette R2 (2026-10-17) : methode REELLEMENT active (SMS verifie, TOTP confirme),
        // meme regle que LoginUseCase ; le simple choix d'une methode n'en active aucune.
        String method = user.methodeDeuxFacteursActive();
        if ("SMS".equals(method)) {
            // Delegue au pipeline SMS commun (verifie code, gere lockout failed_login_attempts,
            // marque OTP used, audit SMS_OTP_VERIFIED).
            verifySmsOtpUseCase.execute(new VerifySmsOtpUseCase.Command(
                    user.id(), workspace.id(), "2FA_LOGIN", cmd.code(),
                    cmd.ipAddress(), cmd.userAgent()));
        } else if ("TOTP".equals(method)) {
            controleMethodeActuelle.verifierTotp(user, cmd.code(), cmd.ipAddress(), cmd.userAgent());
        } else {
            throw new UnauthorizedException("La double authentification n\u2019est pas activ\u00e9e pour ce compte.");
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
