package ma.jurika.dashboard.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import ma.jurika.common.audit.Auditable;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dashboard.api.dto.DashboardDtos.*;
import ma.jurika.dashboard.application.aggregator.ClientDashboardAggregator;
import ma.jurika.dashboard.application.aggregator.EmployeDashboardAggregator;
import ma.jurika.dashboard.application.aggregator.SuperAdminDashboardAggregator;
import ma.jurika.dashboard.application.aggregator.SuperviseurDashboardAggregator;
import ma.jurika.dashboard.domain.DashboardScope;
import ma.jurika.dashboard.infrastructure.persistence.DashboardSnapshotEntity;
import ma.jurika.dashboard.infrastructure.persistence.DashboardSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Sprint 10 -- Facade de lecture des dashboards.
 * Pattern : Redis hot cache + table dashboard_snapshots warm cache + aggregator delegation.
 * Lock anti-stampede via Redis SET NX EX.
 */
@Service
public class DashboardQueryService {

    private static final Logger log = LoggerFactory.getLogger(DashboardQueryService.class);

    private final RedisTemplate<String, Object> redis;
    private final DashboardSnapshotRepository snapshots;
    private final ObjectMapper mapper;
    private final SuperAdminDashboardAggregator superAdmin;
    private final SuperviseurDashboardAggregator superviseur;
    private final EmployeDashboardAggregator employe;
    private final ClientDashboardAggregator client;
    private final MeterRegistry metrics;

    @Value("${jurika.dashboard.cache-ttl-seconds:60}")
    private long ttlSeconds;

    @Value("${jurika.dashboard.rebuild-lock-seconds:10}")
    private long lockSeconds;

    public DashboardQueryService(RedisTemplate<String, Object> redis,
                                  DashboardSnapshotRepository snapshots,
                                  ObjectMapper mapper,
                                  SuperAdminDashboardAggregator superAdmin,
                                  SuperviseurDashboardAggregator superviseur,
                                  EmployeDashboardAggregator employe,
                                  ClientDashboardAggregator client,
                                  MeterRegistry metrics) {
        this.redis = redis;
        this.snapshots = snapshots;
        this.mapper = mapper;
        this.superAdmin = superAdmin;
        this.superviseur = superviseur;
        this.employe = employe;
        this.client = client;
        this.metrics = metrics;
    }

    @Auditable(action = "DASHBOARD_ADMIN_VIEW", resourceType = "dashboard")
    public SuperAdminDashboardDto loadSuperAdmin() {
        return loadCached(DashboardScope.SUPER_ADMIN, null, null,
                SuperAdminDashboardDto.class, superAdmin::aggregate);
    }

    @Auditable(action = "DASHBOARD_VIEW", resourceType = "dashboard")
    public SuperviseurDashboardDto loadSuperviseur() {
        UUID ws = TenantContext.get();
        return loadCached(DashboardScope.SUPERVISEUR, ws, null,
                SuperviseurDashboardDto.class, () -> superviseur.aggregate(ws));
    }

    @Auditable(action = "DASHBOARD_VIEW", resourceType = "dashboard")
    public EmployeDashboardDto loadEmploye(UUID userId) {
        UUID ws = TenantContext.get();
        return loadCached(DashboardScope.EMPLOYE, ws, userId,
                EmployeDashboardDto.class, () -> employe.aggregate(ws, userId));
    }

    @Auditable(action = "DASHBOARD_VIEW", resourceType = "dashboard")
    public ClientDashboardDto loadClient(UUID userId) {
        UUID ws = TenantContext.get();
        return loadCached(DashboardScope.CLIENT, ws, userId,
                ClientDashboardDto.class, () -> client.aggregate(ws, userId));
    }

    public long invalidate(DashboardScope scope, UUID workspaceId, UUID actorId) {
        String pattern = buildKey(scope, workspaceId, actorId);
        Boolean deleted = redis.delete(pattern);
        log.info("Cache invalidated key={} deleted={}", pattern, deleted);
        return Boolean.TRUE.equals(deleted) ? 1 : 0;
    }

    public long invalidateWorkspace(UUID workspaceId) {
        long count = 0;
        for (DashboardScope s : DashboardScope.values()) {
            if (s == DashboardScope.SUPER_ADMIN) continue;
            String pattern = "dashboard:" + s.name() + ":" + workspaceId + ":*";
            var keys = redis.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                Long n = redis.delete(keys);
                if (n != null) count += n;
            }
        }
        log.info("Workspace cache invalidated workspaceId={} count={}", workspaceId, count);
        return count;
    }

    // -----------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private <T> T loadCached(DashboardScope scope, UUID ws, UUID actor,
                              Class<T> type, Supplier<T> supplier) {
        String key = buildKey(scope, ws, actor);
        long start = System.nanoTime();
        // 1. Redis hot read
        Object hit = redis.opsForValue().get(key);
        if (hit != null) {
            metrics.counter("jurika_dashboard_cache_hit_total", "scope", scope.name()).increment();
            recordRenderTime(scope, start, true);
            return mapper.convertValue(hit, type);
        }
        metrics.counter("jurika_dashboard_cache_miss_total", "scope", scope.name()).increment();

        // 2. Anti-stampede : SET NX EX
        String lockKey = "dashboard:lock:" + scope.name() + ":" + ws + ":" + (actor == null ? "_" : actor);
        Boolean acquired = redis.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(lockSeconds));
        if (!Boolean.TRUE.equals(acquired)) {
            // Un autre thread reconstruit -- fallback warm cache
            return loadFromSnapshotOrRebuild(scope, ws, actor, type, supplier);
        }

        try {
            T fresh = supplier.get();
            redis.opsForValue().set(key, fresh, Duration.ofSeconds(ttlSeconds));
            persistSnapshot(scope, ws, actor, fresh);
            metrics.counter("jurika_dashboard_rebuild_total", "scope", scope.name()).increment();
            recordRenderTime(scope, start, false);
            return fresh;
        } finally {
            redis.delete(lockKey);
        }
    }

    private <T> T loadFromSnapshotOrRebuild(DashboardScope scope, UUID ws, UUID actor,
                                              Class<T> type, Supplier<T> supplier) {
        // Tentative lecture warm cache
        try {
            String scopeName = scope.name();
            var snap = snapshots.findLatestValid(ws, scopeName, actor, Instant.now())
                    .orElse(null);
            if (snap != null) {
                return mapper.readValue(snap.getPayload(), type);
            }
        } catch (Exception e) {
            log.warn("Snapshot warm cache read failed : {}", e.getMessage());
        }
        return supplier.get();
    }

    @Transactional
    protected <T> void persistSnapshot(DashboardScope scope, UUID ws, UUID actor, T value) {
        if (ws == null) return;
        try {
            DashboardSnapshotEntity e = new DashboardSnapshotEntity();
            e.setWorkspaceId(ws);
            e.setScope(scope.name());
            e.setActorId(actor);
            e.setPayload(mapper.writeValueAsString(value));
            e.setGeneratedAt(Instant.now());
            e.setValidUntil(Instant.now().plus(Duration.ofMinutes(15)));
            snapshots.save(e);
        } catch (Exception ex) {
            log.warn("Snapshot persist failed (best-effort) : {}", ex.getMessage());
        }
    }

    private void recordRenderTime(DashboardScope scope, long startNanos, boolean cacheHit) {
        Timer t = Timer.builder("jurika_dashboard_render_seconds")
                .tag("scope", scope.name())
                .tag("cache_hit", String.valueOf(cacheHit))
                .register(metrics);
        t.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    private String buildKey(DashboardScope scope, UUID ws, UUID actor) {
        return "dashboard:" + scope.name()
                + ":" + (ws == null ? "_global" : ws.toString())
                + ":" + (actor == null ? "_" : actor.toString());
    }
}
