package ma.jurika.auth.infrastructure.persistence;

import ma.jurika.auth.domain.port.RefreshTokenRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpa;

    public RefreshTokenRepositoryAdapter(RefreshTokenJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void store(UUID userId, UUID workspaceId, String tokenHash, Instant expiresAt,
                      String userAgent, String ipAddress) {
        RefreshTokenEntity e = new RefreshTokenEntity();
        e.setUserId(userId);
        e.setWorkspaceId(workspaceId);
        e.setTokenHash(tokenHash);
        e.setExpiresAt(expiresAt);
        e.setUserAgent(userAgent);
        e.setIpAddress(ipAddress);
        jpa.save(e);
    }

    @Override
    public Optional<StoredRefreshToken> findByHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash)
                .map(e -> new StoredRefreshToken(e.getUserId(), e.getWorkspaceId(),
                        e.getIssuedAt(), e.getExpiresAt(), e.getRevokedAt()));
    }

    @Override
    public void revoke(String tokenHash, Instant when) {
        jpa.revoke(tokenHash, when);
    }

    @Override
    public void revokeAllForUser(UUID userId, Instant when) {
        jpa.revokeAllForUser(userId, when);
    }

    @Override
    public long countActiveByUser(UUID userId, Instant now) {
        return jpa.countActiveByUser(userId, now);
    }

    @Override
    public List<ActiveRefreshToken> findOldestActive(UUID userId, int limit, Instant now) {
        if (limit <= 0) {
            return List.of();
        }
        return jpa.findOldestActive(userId, now, PageRequest.of(0, limit)).stream()
                .map(e -> new ActiveRefreshToken(e.getId(), e.getTokenHash(),
                        e.getIssuedAt(), e.getExpiresAt()))
                .toList();
    }

    @Override
    @Transactional
    public int deleteExpired(Instant cutoff) {
        // Lot L0 (E13b) : entretien sans workspace, transverse par nature ;
        // fonction SECURITY DEFINER auth_purge_jetons_refresh (auth V34).
        return jpa.purgerExpires(cutoff);
    }

    @Override
    public boolean hasRecentSessionFromDevice(UUID userId, String ipAddress, String userAgent, Instant since) {
        if (ipAddress == null || ipAddress.isBlank()
                || userAgent == null || userAgent.isBlank()) {
            return false;
        }
        return jpa.countByUserAndDeviceSince(userId, ipAddress, userAgent, since) > 0L;
    }
}
