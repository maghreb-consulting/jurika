package ma.jurika.auth.application;

import ma.jurika.auth.domain.port.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Enforces the active-session policy right before a new refresh token is persisted.
 *
 * <p>Two modes, controlled by {@code jurika.auth.single-session} :
 * <ul>
 *   <li><b>single-session (defaut, RG-SAAS-12 durci)</b> : une seule session active par
 *       utilisateur. Tous les refresh tokens actifs sont revoques avant emission du
 *       nouveau ("dernier login gagne"). L'ancienne session tombe en 401 a son prochain
 *       refresh (code {@code SESSION_REVOKED}).</li>
 *   <li><b>cap</b> : au plus {@code max-active-sessions} sessions ; les plus anciennes
 *       sont revoquees pour laisser la place a la nouvelle (comportement historique).</li>
 * </ul>
 *
 * <p>Appele de facon uniforme par les 8 points d'emission (login, verify-2fa,
 * verify-sms-otp, verify-recovery-code, refresh, change-password, setup-2fa/confirm),
 * donc le durcissement s'applique a tout le cycle d'authentification et d'onboarding.
 */
@Component
public class SessionLimitEnforcer {

    private final RefreshTokenRepository refreshTokenRepository;
    private final int maxActiveSessions;
    private final boolean singleSession;

    public SessionLimitEnforcer(RefreshTokenRepository refreshTokenRepository,
                                @Value("${jurika.auth.max-active-sessions:5}") int maxActiveSessions,
                                @Value("${jurika.auth.single-session:true}") boolean singleSession) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.maxActiveSessions = maxActiveSessions;
        this.singleSession = singleSession;
    }

    /**
     * En mode single-session : revoque toutes les sessions actives (le caller insere
     * ensuite l'unique nouvelle). Sinon : revoque les plus anciennes pour que, apres
     * insertion, l'utilisateur detienne au plus {@link #maxActiveSessions} sessions.
     */
    public void enforceBeforeIssuing(UUID userId, Instant now) {
        if (singleSession) {
            refreshTokenRepository.revokeAllForUser(userId, now);
            return;
        }
        long active = refreshTokenRepository.countActiveByUser(userId, now);
        int excess = (int) (active - (maxActiveSessions - 1L));
        if (excess <= 0) {
            return;
        }
        refreshTokenRepository.findOldestActive(userId, excess, now)
                .forEach(t -> refreshTokenRepository.revoke(t.tokenHash(), now));
    }

    public int maxActiveSessions() {
        return singleSession ? 1 : maxActiveSessions;
    }

    public boolean singleSession() {
        return singleSession;
    }
}
