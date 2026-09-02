package ma.jurika.auth.infrastructure.ratelimit;

import ma.jurika.auth.domain.port.RecoveryCodeRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation in-memory du rate limiter recovery (RG-AU42).
 *
 * <p>Sliding window simple : pour chaque cle on memorise les timestamps des
 * echecs recents. Avant chaque tentative on purge les entrees plus anciennes
 * que la fenetre, puis on compare la taille au quota.
 *
 * <p>Trade-off prod : single-instance auth-service est l'hypothese courante
 * (1 replica). Si scale-out >1, migrer vers Redis (cle = bucket, TTL = window,
 * SADD ZADD ZCARD). La signature du port ne change pas.
 */
@Component
public class InMemoryRecoveryCodeRateLimiter implements RecoveryCodeRateLimiter {

    private final ConcurrentHashMap<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration window;

    public InMemoryRecoveryCodeRateLimiter(
            @Value("${jurika.auth.recovery-code.max-attempts:5}") int maxAttempts,
            @Value("${jurika.auth.recovery-code.window-minutes:15}") int windowMinutes) {
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    @Override
    public boolean tryAcquire(String key) {
        Instant now = Instant.now();
        Deque<Instant> queue = attempts.get(key);
        if (queue == null) return true;
        synchronized (queue) {
            purge(queue, now);
            return queue.size() < maxAttempts;
        }
    }

    @Override
    public void recordFailure(String key) {
        Instant now = Instant.now();
        Deque<Instant> queue = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (queue) {
            purge(queue, now);
            queue.addLast(now);
        }
    }

    @Override
    public void reset(String key) {
        attempts.remove(key);
    }

    private void purge(Deque<Instant> queue, Instant now) {
        Instant cutoff = now.minus(window);
        Iterator<Instant> it = queue.iterator();
        while (it.hasNext()) {
            if (it.next().isBefore(cutoff)) it.remove();
            else break;
        }
    }

    // Test-only accessors
    Map<String, Deque<Instant>> snapshot() {
        return Map.copyOf(attempts);
    }
}
