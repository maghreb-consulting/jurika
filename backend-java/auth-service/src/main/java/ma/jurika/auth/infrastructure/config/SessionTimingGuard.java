package ma.jurika.auth.infrastructure.config;

import ma.jurika.common.security.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Garde-fou de configuration (fail-fast) sur les temporalites de session.
 *
 * <p>Invariant impose : la fenetre glissante d'inactivite doit rester
 * STRICTEMENT superieure a la duree de vie de l'access token, avec une marge.
 * Sinon le tout premier refresh (qui n'intervient qu'a l'expiration de l'access,
 * cote front reactif) constate systematiquement une "inactivite" — et un
 * utilisateur pourtant ACTIF est deconnecte avec "Session expiree pour
 * inactivite". C'est exactement le bug rencontre quand
 * {@code JWT_ACCESS_EXPIRATION} a ete monte a 8h alors que
 * {@code inactivity-window} restait a 30m.
 *
 * <p>Le raisonnement : la fenetre d'inactivite est reamorcee a chaque rotation
 * du refresh token, et une rotation n'a lieu (au plus tot) qu'au moment ou
 * l'access expire. Pour que la fenetre puisse "glisser" avant d'etre depassee,
 * il faut donc {@code inactivity-window >= access-ttl + marge}. Le refresh
 * proactif cote front rafraichit bien avant l'expiration, ce qui donne encore
 * plus de marge, mais on protege ici le cas nominal contre toute mauvaise
 * valeur d'environnement.
 *
 * <p>En cas de violation, le contexte Spring refuse de demarrer : impossible de
 * mettre en service une configuration qui deconnecterait les utilisateurs actifs.
 */
@Component
public class SessionTimingGuard implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(SessionTimingGuard.class);

    /** Marge minimale exigee entre access-ttl et la fenetre d'inactivite. */
    static final Duration REQUIRED_MARGIN = Duration.ofMinutes(5);

    private final Duration accessTtl;
    private final Duration inactivityWindow;

    public SessionTimingGuard(JwtProperties jwtProperties,
                              @Value("${jurika.auth.inactivity-window:30m}") Duration inactivityWindow) {
        this.accessTtl = Duration.ofMillis(jwtProperties.accessTtlMs());
        this.inactivityWindow = inactivityWindow;
    }

    @Override
    public void afterPropertiesSet() {
        validate(accessTtl, inactivityWindow);
        log.info("Session timing OK — access-ttl={}, inactivity-window={} (marge requise {})",
                accessTtl, inactivityWindow, REQUIRED_MARGIN);
    }

    /**
     * Leve {@link IllegalStateException} si l'invariant n'est pas respecte.
     * Extrait pour etre testable unitairement sans contexte Spring.
     */
    static void validate(Duration accessTtl, Duration inactivityWindow) {
        Duration minimumWindow = accessTtl.plus(REQUIRED_MARGIN);
        if (inactivityWindow.compareTo(minimumWindow) < 0) {
            throw new IllegalStateException(String.format(
                    "Configuration de session invalide : jurika.auth.inactivity-window (%s) doit etre "
                            + ">= access-ttl (%s) + marge (%s) = %s. Sinon un utilisateur ACTIF est "
                            + "deconnecte a tort pour 'inactivite' des le premier refresh. "
                            + "Corrigez JURIKA_AUTH_INACTIVITY_WINDOW ou JWT_ACCESS_TTL_MS / "
                            + "JWT_ACCESS_EXPIRATION.",
                    inactivityWindow, accessTtl, REQUIRED_MARGIN, minimumWindow));
        }
    }
}
