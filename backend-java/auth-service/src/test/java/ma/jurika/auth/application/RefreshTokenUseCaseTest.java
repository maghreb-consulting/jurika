package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.AuthTokens;
import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.port.RefreshTokenRepository;
import ma.jurika.auth.domain.port.RefreshTokenRepository.StoredRefreshToken;
import ma.jurika.auth.domain.port.TokenIssuer;
import ma.jurika.auth.domain.port.TokenIssuer.ParsedRefreshToken;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.common.exception.UnauthorizedException;
import ma.jurika.common.observability.BusinessMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Durcissement securite sessions (2026-07) — couvre :
 *  - rotation stricte : un refresh consomme est revoque puis remplace ;
 *  - rejeu d'un token revoque -> 401 SESSION_REVOKED (session invalidee par un autre login) ;
 *  - fenetre glissante d'inactivite depassee -> 401 SESSION_INACTIVITY + revocation du token idle ;
 *  - expiration absolue -> 401 SESSION_EXPIRED.
 */
class RefreshTokenUseCaseTest {

    private final TokenIssuer tokenIssuer = mock(TokenIssuer.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final SessionLimitEnforcer sessionLimitEnforcer = mock(SessionLimitEnforcer.class);
    private final BusinessMetrics businessMetrics = mock(BusinessMetrics.class);

    private final Duration inactivityWindow = Duration.ofMinutes(30);
    private final RefreshTokenUseCase useCase = new RefreshTokenUseCase(
            tokenIssuer, refreshTokenRepository, userRepository, sessionLimitEnforcer,
            businessMetrics, inactivityWindow);

    private final UUID userId = UUID.randomUUID();
    private final UUID workspaceId = UUID.randomUUID();
    private User user;

    @BeforeEach
    void setUp() {
        user = mock(User.class);
        when(user.id()).thenReturn(userId);
        when(user.workspaceId()).thenReturn(workspaceId);
        when(user.active()).thenReturn(true);
        when(tokenIssuer.parseRefreshToken("raw-old"))
                .thenReturn(new ParsedRefreshToken("raw-old", "hash-old"));
    }

    private StoredRefreshToken stored(Instant issuedAt, Instant expiresAt, Instant revokedAt) {
        return new StoredRefreshToken(userId, workspaceId, issuedAt, expiresAt, revokedAt);
    }

    @Test
    void rotatesRefreshTokenOnSuccessfulRefresh() {
        Instant now = Instant.now();
        when(refreshTokenRepository.findByHash("hash-old"))
                .thenReturn(Optional.of(stored(now.minusSeconds(60), now.plusSeconds(86400), null)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        AuthTokens fresh = new AuthTokens("access-new", "raw-new",
                now.plusSeconds(900), now.plusSeconds(604800), userId, workspaceId);
        when(tokenIssuer.issue(user)).thenReturn(fresh);
        when(tokenIssuer.parseRefreshToken("raw-new"))
                .thenReturn(new ParsedRefreshToken("raw-new", "hash-new"));

        AuthTokens result = useCase.execute("raw-old", "UA", "1.2.3.4");

        assertThat(result.refreshToken()).isEqualTo("raw-new");
        // Rotation stricte : ancien revoque, nouveau stocke.
        verify(refreshTokenRepository).revoke(eq("hash-old"), any());
        verify(sessionLimitEnforcer).enforceBeforeIssuing(eq(userId), any());
        verify(refreshTokenRepository).store(eq(userId), eq(workspaceId), eq("hash-new"),
                any(), eq("UA"), eq("1.2.3.4"));
    }

    @Test
    void replayOfRevokedTokenIsRejectedAsSessionRevoked() {
        Instant now = Instant.now();
        when(refreshTokenRepository.findByHash("hash-old"))
                .thenReturn(Optional.of(stored(now.minusSeconds(60), now.plusSeconds(86400), now.minusSeconds(1))));

        assertThatThrownBy(() -> useCase.execute("raw-old", "UA", "1.2.3.4"))
                .isInstanceOf(UnauthorizedException.class)
                .satisfies(ex -> assertThat(((UnauthorizedException) ex).code()).isEqualTo("SESSION_REVOKED"));

        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    void expiredTokenIsRejectedAsSessionExpired() {
        Instant now = Instant.now();
        when(refreshTokenRepository.findByHash("hash-old"))
                .thenReturn(Optional.of(stored(now.minusSeconds(700000), now.minusSeconds(10), null)));

        assertThatThrownBy(() -> useCase.execute("raw-old", "UA", "1.2.3.4"))
                .isInstanceOf(UnauthorizedException.class)
                .satisfies(ex -> assertThat(((UnauthorizedException) ex).code()).isEqualTo("SESSION_EXPIRED"));

        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    void idleBeyondInactivityWindowIsRejectedAndRevoked() {
        Instant now = Instant.now();
        // Emis il y a 40 min (> fenetre 30 min), pas encore expire en absolu.
        when(refreshTokenRepository.findByHash("hash-old"))
                .thenReturn(Optional.of(stored(now.minus(Duration.ofMinutes(40)),
                        now.plusSeconds(86400), null)));

        assertThatThrownBy(() -> useCase.execute("raw-old", "UA", "1.2.3.4"))
                .isInstanceOf(UnauthorizedException.class)
                .satisfies(ex -> assertThat(((UnauthorizedException) ex).code()).isEqualTo("SESSION_INACTIVITY"));

        // Le token idle est revoque pour ne plus etre rejouable.
        verify(refreshTokenRepository).revoke(eq("hash-old"), any());
        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    void refreshWithinInactivityWindowSucceeds() {
        Instant now = Instant.now();
        when(refreshTokenRepository.findByHash("hash-old"))
                .thenReturn(Optional.of(stored(now.minus(Duration.ofMinutes(20)),
                        now.plusSeconds(86400), null)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        AuthTokens fresh = new AuthTokens("access-new", "raw-new",
                now.plusSeconds(900), now.plusSeconds(604800), userId, workspaceId);
        when(tokenIssuer.issue(user)).thenReturn(fresh);
        when(tokenIssuer.parseRefreshToken("raw-new"))
                .thenReturn(new ParsedRefreshToken("raw-new", "hash-new"));

        AuthTokens result = useCase.execute("raw-old", "UA", "1.2.3.4");

        assertThat(result.accessToken()).isEqualTo("access-new");
    }

    @Test
    void refreshJustBeforeInactivityBoundarySucceeds() {
        // issuedAt = now - (30m - 2s) : juste EN DECA de la fenetre => encore actif.
        Instant now = Instant.now();
        when(refreshTokenRepository.findByHash("hash-old"))
                .thenReturn(Optional.of(stored(now.minus(inactivityWindow).plusSeconds(2),
                        now.plusSeconds(86400), null)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        AuthTokens fresh = new AuthTokens("access-new", "raw-new",
                now.plusSeconds(900), now.plusSeconds(604800), userId, workspaceId);
        when(tokenIssuer.issue(user)).thenReturn(fresh);
        when(tokenIssuer.parseRefreshToken("raw-new"))
                .thenReturn(new ParsedRefreshToken("raw-new", "hash-new"));

        AuthTokens result = useCase.execute("raw-old", "UA", "1.2.3.4");

        assertThat(result.accessToken()).isEqualTo("access-new");
    }

    @Test
    void refreshOneSecondPastInactivityBoundaryIsRejected() {
        // issuedAt = now - (30m + 1s) : juste au-dela de la fenetre => inactivite.
        Instant now = Instant.now();
        when(refreshTokenRepository.findByHash("hash-old"))
                .thenReturn(Optional.of(stored(now.minus(inactivityWindow).minusSeconds(1),
                        now.plusSeconds(86400), null)));

        assertThatThrownBy(() -> useCase.execute("raw-old", "UA", "1.2.3.4"))
                .isInstanceOf(UnauthorizedException.class)
                .satisfies(ex -> assertThat(((UnauthorizedException) ex).code()).isEqualTo("SESSION_INACTIVITY"));

        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    void rotationPreservesAbsoluteExpiryCap() {
        // Le plafond ABSOLU (expiresAt d'origine, ici login + 1h) ne doit PAS etre
        // repousse a now + refresh-ttl (7j) a la rotation, sinon un utilisateur actif
        // ne finirait jamais sa session.
        Instant now = Instant.now();
        Instant originalCap = now.plusSeconds(3600); // plafond absolu pose au login
        when(refreshTokenRepository.findByHash("hash-old"))
                .thenReturn(Optional.of(stored(now.minusSeconds(60), originalCap, null)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        AuthTokens fresh = new AuthTokens("access-new", "raw-new",
                now.plusSeconds(900), now.plusSeconds(604800), userId, workspaceId);
        when(tokenIssuer.issue(user)).thenReturn(fresh);
        when(tokenIssuer.parseRefreshToken("raw-new"))
                .thenReturn(new ParsedRefreshToken("raw-new", "hash-new"));

        AuthTokens result = useCase.execute("raw-old", "UA", "1.2.3.4");

        ArgumentCaptor<Instant> expiryCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(refreshTokenRepository).store(eq(userId), eq(workspaceId), eq("hash-new"),
                expiryCaptor.capture(), eq("UA"), eq("1.2.3.4"));
        // Stocke avec le plafond D'ORIGINE, pas now + 7j.
        assertThat(expiryCaptor.getValue()).isEqualTo(originalCap);
        // Le token renvoye au client reflete lui aussi le plafond absolu preserve.
        assertThat(result.refreshExpiresAt()).isEqualTo(originalCap);
    }
}
