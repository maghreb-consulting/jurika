package ma.jurika.auth.application;

import ma.jurika.auth.domain.port.RefreshTokenRepository;
import ma.jurika.auth.domain.port.RefreshTokenRepository.ActiveRefreshToken;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionLimitEnforcerTest {

    private final RefreshTokenRepository repo = mock(RefreshTokenRepository.class);
    // Mode cap (single-session=false) — comportement historique RG-SAAS-12.
    private final SessionLimitEnforcer enforcer = new SessionLimitEnforcer(repo, 5, false);
    private final Instant now = Instant.parse("2026-05-21T10:00:00Z");
    private final UUID userId = UUID.randomUUID();

    @Test
    void doesNothingWhenUserHasFewerThanMaxSessions() {
        when(repo.countActiveByUser(userId, now)).thenReturn(3L);

        enforcer.enforceBeforeIssuing(userId, now);

        verify(repo, never()).findOldestActive(any(), anyInt(), any());
        verify(repo, never()).revoke(any(), any());
    }

    @Test
    void doesNothingAtExactlyMaxMinusOne() {
        when(repo.countActiveByUser(userId, now)).thenReturn(4L);

        enforcer.enforceBeforeIssuing(userId, now);

        verify(repo, never()).findOldestActive(any(), anyInt(), any());
    }

    @Test
    void revokesOldestWhenAtMax() {
        when(repo.countActiveByUser(userId, now)).thenReturn(5L);
        ActiveRefreshToken oldest = new ActiveRefreshToken(UUID.randomUUID(), "hash-oldest",
                now.minusSeconds(86400), now.plusSeconds(86400));
        when(repo.findOldestActive(eq(userId), eq(1), eq(now))).thenReturn(List.of(oldest));

        enforcer.enforceBeforeIssuing(userId, now);

        verify(repo).revoke("hash-oldest", now);
    }

    @Test
    void revokesMultipleWhenOverMax() {
        when(repo.countActiveByUser(userId, now)).thenReturn(7L);
        ActiveRefreshToken o1 = new ActiveRefreshToken(UUID.randomUUID(), "h1", now, now.plusSeconds(86400));
        ActiveRefreshToken o2 = new ActiveRefreshToken(UUID.randomUUID(), "h2", now, now.plusSeconds(86400));
        ActiveRefreshToken o3 = new ActiveRefreshToken(UUID.randomUUID(), "h3", now, now.plusSeconds(86400));
        when(repo.findOldestActive(eq(userId), eq(3), eq(now))).thenReturn(List.of(o1, o2, o3));

        enforcer.enforceBeforeIssuing(userId, now);

        ArgumentCaptor<String> hashes = ArgumentCaptor.forClass(String.class);
        verify(repo, times(3)).revoke(hashes.capture(), eq(now));
        assertThat(hashes.getAllValues()).containsExactly("h1", "h2", "h3");
    }

    @Test
    void exposesConfiguredMax() {
        assertThat(enforcer.maxActiveSessions()).isEqualTo(5);
    }

    // --- Mode single-session (defaut prod) : durcissement "dernier login gagne" ---

    @Test
    void singleSessionRevokesAllActiveTokensBeforeIssuing() {
        SessionLimitEnforcer single = new SessionLimitEnforcer(repo, 5, true);

        single.enforceBeforeIssuing(userId, now);

        // Une seule requete : revoquer TOUTES les sessions actives de l'utilisateur.
        verify(repo).revokeAllForUser(userId, now);
        // On ne passe jamais par le comptage / cap en mode single-session.
        verify(repo, never()).countActiveByUser(any(), any());
        verify(repo, never()).findOldestActive(any(), anyInt(), any());
    }

    @Test
    void singleSessionReportsMaxOfOne() {
        SessionLimitEnforcer single = new SessionLimitEnforcer(repo, 5, true);
        assertThat(single.maxActiveSessions()).isEqualTo(1);
        assertThat(single.singleSession()).isTrue();
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
