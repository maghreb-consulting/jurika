package ma.jurika.auth.infrastructure.ratelimit;

import ma.jurika.auth.domain.port.IpRateLimiter;
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
 * Sprint 11 — In-memory sliding window IP rate limiter.
 *
 * <p>Pattern aligné sur {@code InMemoryRecoveryCodeRateLimiter} (Sprint 14 bis) :
 * un {@code ConcurrentHashMap<bucketKey, Deque<Instant>>} stocke les timestamps
 * des requêtes "consommées". Avant chaque {@code tryAcquire}, on purge les
 * entrées plus anciennes que la fenêtre du bucket.
 *
 * <p>Quotas par bucket configurables via properties :
 * <pre>
 *   jurika.ratelimit.demo-request.max-per-hour=10  (RG-MK02)
 *   jurika.ratelimit.signup-cabinet.max-per-hour=5  (RG-SU03, Sprint 11 TASK 2)
 *   jurika.ratelimit.public-events.max-per-minute=100  (Sprint 11 TASK 6)
 * </pre>
 *
 * <p>Hypothèse single-instance (auth-service = 1 replica). Migration Redis quand
 * scale-out > 1 — la signature {@link IpRateLimiter} ne change pas.
 */
@Component
public class InMemoryIpRateLimiter implements IpRateLimiter {

    private static final class BucketConfig {
        final int max;
        final Duration window;
        BucketConfig(int max, Duration window) { this.max = max; this.window = window; }
    }

    private final Map<String, BucketConfig> bucketConfigs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Instant>> counters = new ConcurrentHashMap<>();

    public InMemoryIpRateLimiter(
            @Value("${jurika.ratelimit.demo-request.max-per-hour:10}") int demoMaxPerHour,
            @Value("${jurika.ratelimit.signup-cabinet.max-per-hour:5}") int signupMaxPerHour,
            @Value("${jurika.ratelimit.public-events.max-per-minute:100}") int eventsMaxPerMinute) {
        bucketConfigs.put("demo-request",   new BucketConfig(demoMaxPerHour,   Duration.ofHours(1)));
        bucketConfigs.put("signup-cabinet", new BucketConfig(signupMaxPerHour, Duration.ofHours(1)));
        bucketConfigs.put("public-events",  new BucketConfig(eventsMaxPerMinute, Duration.ofMinutes(1)));
    }

    @Override
    public boolean tryAcquire(String bucket, String key) {
        BucketConfig cfg = bucketConfigs.get(bucket);
        if (cfg == null) return true; // bucket inconnu = pas de limite (fail-open)
        Instant now = Instant.now();
        String composite = bucket + ":" + key;
        Deque<Instant> queue = counters.computeIfAbsent(composite, k -> new ArrayDeque<>());
        synchronized (queue) {
            purge(queue, now, cfg.window);
            if (queue.size() >= cfg.max) return false;
            queue.addLast(now);
            return true;
        }
    }

    private void purge(Deque<Instant> queue, Instant now, Duration window) {
        Instant cutoff = now.minus(window);
        Iterator<Instant> it = queue.iterator();
        while (it.hasNext()) {
            if (it.next().isBefore(cutoff)) it.remove();
            else break;
        }
    }

    // Test-only accessors
    int currentSize(String bucket, String key) {
        Deque<Instant> q = counters.get(bucket + ":" + key);
        return q == null ? 0 : q.size();
    }
}
