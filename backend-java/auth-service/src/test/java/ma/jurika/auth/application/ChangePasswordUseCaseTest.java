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
import ma.jurika.common.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class ChangePasswordUseCaseTest {

    @Mock UserRepository userRepository;
    @Mock PasswordHasher passwordHasher;
    @Mock AuditLogger auditLogger;
    @Mock EmailSender emailSender;
    @Mock TokenIssuer tokenIssuer;
    @Mock RefreshTokenRepository refreshTokenRepository;

    private final PasswordPolicy passwordPolicy = new PasswordPolicy();
    private ChangePasswordUseCase useCase;
    private User user;
    private final UUID workspaceId = UUID.randomUUID();
    private final String oldPassword = "OldStrong@2026";
    private final String newPassword = "NewStrong@2026!";

    @BeforeEach
    void setUp() {
        useCase = new ChangePasswordUseCase(userRepository, passwordHasher, passwordPolicy,
                auditLogger, emailSender, tokenIssuer, refreshTokenRepository);
        user = new User(UUID.randomUUID(), workspaceId, "u@jurika.ma", "$2a$12$oldHash",
                "Karim", "Benali", null, Role.EMPLOYE,
                null, false, true, "TOTP", Instant.now(), null, null,
                (short) 0, null, null, ma.jurika.auth.domain.model.UserStatus.ACTIVE, Instant.now());
    }

    @Test
    @DisplayName("change-password succes : update hash + clear mustChange + audit log + tokens emis")
    void changePasswordSuccess() {
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user));
        when(passwordHasher.matches(oldPassword, user.passwordHash())).thenReturn(true);
        when(passwordHasher.hash(newPassword)).thenReturn("$2a$12$newHash");
        AuthTokens fakeTokens = new AuthTokens("access.jwt", "refresh.opaque",
                Instant.now().plusSeconds(900), Instant.now().plusSeconds(604800),
                user.id(), workspaceId);
        when(tokenIssuer.issue(any(User.class))).thenReturn(fakeTokens);
        when(tokenIssuer.parseRefreshToken(anyString()))
                .thenReturn(new TokenIssuer.ParsedRefreshToken("tid", "rhash"));

        AuthTokens result = useCase.execute(new ChangePasswordUseCase.Command(
                user.id(), workspaceId, oldPassword, newPassword, newPassword, "127.0.0.1", "test-ua"));

        verify(userRepository, times(1))
                .updatePasswordHashAndClearMustChange(user.id(), "$2a$12$newHash");
        verify(auditLogger, times(1))
                .log(eq(workspaceId), eq(user.id()), eq("PASSWORD_CHANGED"),
                        anyString(), eq(user.id()), anyString(), anyString(), any());
        // Hotfix 2026-06-04 — verifier que les NOUVEAUX tokens sont emis +
        // que la session refresh est persistee (sinon /refresh ne marchera plus).
        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isEqualTo("access.jwt");
        assertThat(result.refreshToken()).isEqualTo("refresh.opaque");
        ArgumentCaptor<UUID> uidCap = ArgumentCaptor.forClass(UUID.class);
        verify(refreshTokenRepository, times(1)).store(uidCap.capture(), eq(workspaceId),
                eq("rhash"), any(Instant.class), eq("test-ua"), eq("127.0.0.1"));
        assertThat(uidCap.getValue()).isEqualTo(user.id());
        // tokenIssuer.issue() doit etre appele apres le re-load du user (donc 2 findById attendus).
        verify(userRepository, times(2)).findById(user.id());
    }

    @Test
    @DisplayName("confirmation differente -> ValidationException")
    void mismatchConfirmation() {
        assertThatThrownBy(() -> useCase.execute(new ChangePasswordUseCase.Command(
                user.id(), workspaceId, oldPassword, newPassword, "OtherPwd@2026", "ip", "ua")))
                .isInstanceOf(ValidationException.class);

        verify(userRepository, never()).updatePasswordHashAndClearMustChange(any(), any());
        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    @DisplayName("nouveau == ancien -> ValidationException")
    void sameAsOld() {
        assertThatThrownBy(() -> useCase.execute(new ChangePasswordUseCase.Command(
                user.id(), workspaceId, newPassword, newPassword, newPassword, "ip", "ua")))
                .isInstanceOf(ValidationException.class);
        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    @DisplayName("nouveau MDP faible -> ValidationException via PasswordPolicy")
    void weakNewPassword() {
        assertThatThrownBy(() -> useCase.execute(new ChangePasswordUseCase.Command(
                user.id(), workspaceId, oldPassword, "weak", "weak", "ip", "ua")))
                .isInstanceOf(ValidationException.class);
        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    @DisplayName("user inconnu -> NotFoundException")
    void userNotFound() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new ChangePasswordUseCase.Command(
                user.id(), workspaceId, oldPassword, newPassword, newPassword, "ip", "ua")))
                .isInstanceOf(NotFoundException.class);
        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    @DisplayName("ancien MDP incorrect -> UnauthorizedException + aucun token emis")
    void wrongOldPassword() {
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user));
        when(passwordHasher.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(new ChangePasswordUseCase.Command(
                user.id(), workspaceId, oldPassword, newPassword, newPassword, "ip", "ua")))
                .isInstanceOf(UnauthorizedException.class);

        verify(userRepository, never()).updatePasswordHashAndClearMustChange(any(), any());
        verify(tokenIssuer, never()).issue(any());
        verify(refreshTokenRepository, never()).store(any(), any(), any(), any(), any(), any());
    }
}
