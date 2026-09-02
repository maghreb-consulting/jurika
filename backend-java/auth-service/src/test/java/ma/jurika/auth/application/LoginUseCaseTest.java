package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.AuthTokens;
import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.model.Workspace;
import ma.jurika.auth.domain.model.WorkspaceStatus;
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
import ma.jurika.common.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

    @Mock WorkspaceRepository workspaceRepository;
    @Mock UserRepository userRepository;
    @Mock PasswordHasher passwordHasher;
    @Mock TokenIssuer tokenIssuer;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock EventPublisher eventPublisher;
    @Mock AuditLogger auditLogger;
    @Mock EmailSender emailSender;

    private LoginUseCase useCase;
    private SessionLimitEnforcer sessionLimitEnforcer;
    private ma.jurika.common.observability.BusinessMetrics businessMetrics;
    private Workspace workspace;
    private User user;

    @BeforeEach
    void setUp() {
        sessionLimitEnforcer = new SessionLimitEnforcer(refreshTokenRepository, 5, false);
        businessMetrics = new ma.jurika.common.observability.BusinessMetrics(
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        useCase = new LoginUseCase(workspaceRepository, userRepository, passwordHasher,
                tokenIssuer, refreshTokenRepository, sessionLimitEnforcer,
                eventPublisher, auditLogger, businessMetrics, emailSender,
                5, 15, 30, "http://localhost/account/security",
                /* loginEmailEnabled */ false /* BUG 7 — tests historiques utilisent email = identifiant */);

        workspace = new Workspace(UUID.randomUUID(), "JUR-AB123", "Cabinet Test",
                "test@jurika.ma", UUID.randomUUID(), WorkspaceStatus.ACTIVE,
                Instant.now(), Instant.now());

        user = new User(UUID.randomUUID(), workspace.id(), "user@jurika.ma", "$2a$12$hash",
                "Karim", "Benali", null, Role.EMPLOYE,
                null, false, false, "TOTP", Instant.now(), null, null,
                (short) 0, null, null, ma.jurika.auth.domain.model.UserStatus.ACTIVE, Instant.now());
    }

    @Test
    @DisplayName("login reussi sans 2FA retourne tokens")
    void loginSuccessNoTwoFactor() {
        when(workspaceRepository.findByCode("JUR-AB123")).thenReturn(Optional.of(workspace));
        when(userRepository.findByWorkspaceAndEmail(workspace.id(), "user@jurika.ma"))
                .thenReturn(Optional.of(user));
        when(passwordHasher.matches("Secret@2026", user.passwordHash())).thenReturn(true);
        AuthTokens issued = new AuthTokens("access", "refresh.token",
                Instant.now().plusSeconds(1800), Instant.now().plusSeconds(86400),
                user.id(), workspace.id());
        when(tokenIssuer.issue(user)).thenReturn(issued);
        when(tokenIssuer.parseRefreshToken("refresh.token"))
                .thenReturn(new TokenIssuer.ParsedRefreshToken("refresh.token", "hash"));

        var result = useCase.execute(new LoginUseCase.Command(
                "JUR-AB123", "user@jurika.ma", "Secret@2026", "127.0.0.1", "test"));

        assertThat(result.requires2fa()).isFalse();
        assertThat(result.tokens().accessToken()).isEqualTo("access");
        verify(refreshTokenRepository, times(1)).store(eq(user.id()), eq(workspace.id()),
                eq("hash"), any(), eq("test"), eq("127.0.0.1"));
        verify(eventPublisher).publishUserLoggedIn(workspace.id(), user.id());
    }

    @Test
    @DisplayName("login avec 2FA active retourne flag requires2fa")
    void loginRequiresTwoFactor() {
        User userWith2fa = new User(user.id(), user.workspaceId(), user.email(), user.passwordHash(),
                user.firstName(), user.lastName(), null, user.role(),
                "encryptedSecret", true, false, "TOTP", Instant.now(), null, null,
                (short) 0, null, null, ma.jurika.auth.domain.model.UserStatus.ACTIVE, Instant.now());

        when(workspaceRepository.findByCode("JUR-AB123")).thenReturn(Optional.of(workspace));
        when(userRepository.findByWorkspaceAndEmail(any(), anyString()))
                .thenReturn(Optional.of(userWith2fa));
        when(passwordHasher.matches(anyString(), anyString())).thenReturn(true);

        var result = useCase.execute(new LoginUseCase.Command(
                "JUR-AB123", "user@jurika.ma", "Secret@2026", "127.0.0.1", "test"));

        assertThat(result.requires2fa()).isTrue();
        assertThat(result.tokens()).isNull();
        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    @DisplayName("workspace inconnu leve NotFoundException")
    void unknownWorkspace() {
        when(workspaceRepository.findByCode("JUR-XXXXX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new LoginUseCase.Command(
                "JUR-XXXXX", "u@j.ma", "p", "ip", "ua")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("mauvais mot de passe leve UnauthorizedException et incremente compteur")
    void wrongPasswordIncrementsCounter() {
        when(workspaceRepository.findByCode(anyString())).thenReturn(Optional.of(workspace));
        when(userRepository.findByWorkspaceAndEmail(any(), anyString())).thenReturn(Optional.of(user));
        when(passwordHasher.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(new LoginUseCase.Command(
                "JUR-AB123", "user@jurika.ma", "wrong", "ip", "ua")))
                .isInstanceOf(UnauthorizedException.class);

        verify(userRepository).incrementFailedLogin(eq(user.id()), any());
    }

    @Test
    @DisplayName("workspace suspendu leve UnauthorizedException")
    void suspendedWorkspace() {
        Workspace suspended = new Workspace(workspace.id(), workspace.code(), workspace.name(),
                workspace.contactEmail(), workspace.subscriptionId(), WorkspaceStatus.SUSPENDED,
                Instant.now(), Instant.now());
        when(workspaceRepository.findByCode(anyString())).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> useCase.execute(new LoginUseCase.Command(
                "JUR-AB123", "u@j.ma", "p", "ip", "ua")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Workspace inactif");
    }
}
