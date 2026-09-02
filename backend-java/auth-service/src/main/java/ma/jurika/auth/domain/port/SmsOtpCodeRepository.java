package ma.jurika.auth.domain.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Port pour la persistence des codes OTP SMS.
 * Le code 6 chiffres est hashe (SHA-256) avant stockage.
 */
public interface SmsOtpCodeRepository {

    UUID create(UUID workspaceId, UUID userId, String phoneE164, String codeHash,
                String purpose, Instant expiresAt, int maxAttempts);

    /**
     * Recupere le code OTP actif (non utilise, non expire) le plus recent
     * pour cet user et ce purpose.
     */
    Optional<StoredOtp> findLatestActive(UUID userId, String purpose);

    void markUsed(UUID id, Instant usedAt);

    void incrementAttempts(UUID id);

    record StoredOtp(
            UUID id,
            UUID workspaceId,
            UUID userId,
            String codeHash,
            String phoneE164,
            String purpose,
            Instant expiresAt,
            short attempts,
            short maxAttempts,
            Instant usedAt
    ) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
        public boolean isUsed() {
            return usedAt != null;
        }
        public boolean isExhausted() {
            return attempts >= maxAttempts;
        }
    }
}
