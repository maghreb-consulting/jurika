package fr.maghreb.gje.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionActivityService {

    private final StringRedisTemplate redisTemplate;
    private static final String ACTIVITY_KEY_PREFIX = "last_activity:";
    private static final long INACTIVITY_TIMEOUT_SECONDS = 1800; // 30 minutes

    /**
     * Updates the last activity timestamp and refreshes the TTL.
     */
    public void touch(String userId) {
        try {
            String key = ACTIVITY_KEY_PREFIX + userId;
            redisTemplate.opsForValue().set(key, LocalDateTime.now().toString(), INACTIVITY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug("Session activity touched for user: {}", userId);
        } catch (Exception e) {
            log.warn("Redis est indisponible pour 'touch' de l'activité (user: {}). Fail-open.", userId);
        }
    }

    /**
     * Checks if the session is still active based on Redis activity key.
     */
    public boolean isActive(String userId) {
        try {
            String key = ACTIVITY_KEY_PREFIX + userId;
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.warn("Redis est indisponible pour 'isActive' check (user: {}). Fail-open.", userId);
            // Fail-open: allow access if Redis is down
            return true;
        }
    }

    /**
     * Invalidates the session activity on logout or browser close.
     */
    public void invalidate(String userId) {
        try {
            String key = ACTIVITY_KEY_PREFIX + userId;
            redisTemplate.delete(key);
            log.debug("Session activity invalidated for user: {}", userId);
        } catch (Exception e) {
            log.warn("Redis est indisponible pour 'invalidate' (user: {}).", userId);
        }
    }
}
