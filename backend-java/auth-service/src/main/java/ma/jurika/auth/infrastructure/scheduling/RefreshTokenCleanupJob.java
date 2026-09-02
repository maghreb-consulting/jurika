package ma.jurika.auth.infrastructure.scheduling;

import ma.jurika.auth.domain.port.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Daily cleanup of expired refresh tokens (RG-SAAS-12).
 *
 * <p>Refresh tokens that have been expired for longer than {@code retention-days}
 * are hard-deleted at 03:00 every day. Revoked-but-unexpired tokens are kept so
 * the {@code GET /api/v1/auth/sessions} endpoint can show recent activity.
 */
@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenRepository repository;
    private final int retentionDays;

    public RefreshTokenCleanupJob(RefreshTokenRepository repository,
                                  @Value("${jurika.auth.refresh-token-retention-days:30}") int retentionDays) {
        this.repository = repository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${jurika.auth.refresh-token-cleanup-cron:0 0 3 * * *}")
    public void run() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        int deleted = repository.deleteExpired(cutoff);
        if (deleted > 0) {
            log.info("Refresh token cleanup: deleted {} rows expired before {}", deleted, cutoff);
        } else {
            log.debug("Refresh token cleanup: nothing to delete (cutoff={})", cutoff);
        }
    }
}
