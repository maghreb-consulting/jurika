package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.AuthTokens;
import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.port.RefreshTokenRepository;
import ma.jurika.auth.domain.port.RefreshTokenRepository.StoredRefreshToken;
import ma.jurika.auth.domain.port.TokenIssuer;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.common.audit.Auditable;
import ma.jurika.common.exception.UnauthorizedException;
import ma.jurika.common.observability.BusinessMetrics;
import ma.jurika.common.security.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class RefreshTokenUseCase {

    private final TokenIssuer tokenIssuer;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final SessionLimitEnforcer sessionLimitEnforcer;
    private final BusinessMetrics businessMetrics;
    private final Duration inactivityWindow;

    public RefreshTokenUseCase(TokenIssuer tokenIssuer,
                               RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               SessionLimitEnforcer sessionLimitEnforcer,
                               BusinessMetrics businessMetrics,
                               @Value("${jurika.auth.inactivity-window:30m}") Duration inactivityWindow) {
        this.tokenIssuer = tokenIssuer;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.sessionLimitEnforcer = sessionLimitEnforcer;
        this.businessMetrics = businessMetrics;
        this.inactivityWindow = inactivityWindow;
    }

    @Transactional
    @Auditable(action = "REFRESH_TOKEN_USED", resourceType = "user")
    public AuthTokens execute(String rawRefreshToken, String userAgent, String ipAddress) {
        TokenIssuer.ParsedRefreshToken parsed = tokenIssuer.parseRefreshToken(rawRefreshToken);
        StoredRefreshToken stored = refreshTokenRepository.findByHash(parsed.hash())
                .orElseThrow(() -> new UnauthorizedException("Refresh token inconnu"));

        Instant now = Instant.now();
        // Motifs distincts pour que le frontend affiche le bon message :
        // - SESSION_REVOKED   : session invalidee (autre login en mode single-session, ou logout)
        // - SESSION_INACTIVITY: fenetre glissante d'inactivite depassee (defaut 30 min)
        // - SESSION_EXPIRED   : refresh token arrive a son terme absolu
        if (stored.revokedAt() != null) {
            throw new UnauthorizedException("SESSION_REVOKED",
                    "Votre session a ete ouverte sur un autre appareil");
        }
        if (!stored.expiresAt().isAfter(now)) {
            throw new UnauthorizedException("SESSION_EXPIRED",
                    "Session expiree, veuillez vous reconnecter");
        }
        if (stored.isInactive(now, inactivityWindow)) {
            // On revoque explicitement pour que le token idle ne reste pas rejouable.
            refreshTokenRepository.revoke(parsed.hash(), now);
            throw new UnauthorizedException("SESSION_INACTIVITY",
                    "Session expiree pour inactivite");
        }

        TenantContext.set(stored.workspaceId());
        User user = userRepository.findById(stored.userId())
                .orElseThrow(() -> new UnauthorizedException("Utilisateur introuvable"));
        if (!user.active()) {
            throw new UnauthorizedException("Compte desactive");
        }

        refreshTokenRepository.revoke(parsed.hash(), now);
        sessionLimitEnforcer.enforceBeforeIssuing(user.id(), now);

        AuthTokens tokens = tokenIssuer.issue(user);
        TokenIssuer.ParsedRefreshToken newParsed = tokenIssuer.parseRefreshToken(tokens.refreshToken());
        // Chaque ligne refresh_tokens porte DEUX bornes independantes :
        //  - issuedAt (@PrePersist = now) : reamorce la fenetre GLISSANTE d'inactivite ;
        //  - expiresAt : PLAFOND ABSOLU de la session. On PRESERVE l'echeance d'origine
        //    (celle du login initial) a chaque rotation au lieu de la repousser a
        //    now + refresh-ttl. Sinon un utilisateur continuellement actif verrait son
        //    plafond glisser indefiniment => la session ne se terminerait jamais.
        //    Ainsi refresh-ttl = duree de vie MAX absolue d'une session, quelle que
        //    soit l'activite, et le controle expiresAt ci-dessus (SESSION_EXPIRED) en
        //    devient l'application effective.
        Instant absoluteExpiry = stored.expiresAt();
        refreshTokenRepository.store(user.id(), user.workspaceId(),
                newParsed.hash(), absoluteExpiry, userAgent, ipAddress);
        businessMetrics.refreshTokenIssued();

        return new AuthTokens(tokens.accessToken(), tokens.refreshToken(),
                tokens.accessExpiresAt(), absoluteExpiry, user.id(), user.workspaceId());
    }
}
