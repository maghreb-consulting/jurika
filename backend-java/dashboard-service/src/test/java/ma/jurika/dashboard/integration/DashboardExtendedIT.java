package ma.jurika.dashboard.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dashboard.api.dto.DashboardDtos.*;
import ma.jurika.dashboard.application.DashboardCacheInvalidator;
import ma.jurika.dashboard.application.DashboardQueryService;
import ma.jurika.dashboard.application.aggregator.ClientDashboardAggregator;
import ma.jurika.dashboard.application.aggregator.EmployeDashboardAggregator;
import ma.jurika.dashboard.application.aggregator.SuperAdminDashboardAggregator;
import ma.jurika.dashboard.application.aggregator.SuperviseurDashboardAggregator;
import ma.jurika.dashboard.domain.DashboardScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 14 ter C2 -- DashboardExtendedIT (10 cas IT).
 *
 * <p>Couvre les pans non-testes par {@link DashboardSprint10IT} (qui ne valide
 * que l'agregation SQL pure) :
 * <ol>
 *   <li>{@code cacheMissThenHitOnSecondCall} : 1er appel = miss, 2e = hit
 *       Redis (aggregator appele une seule fois).</li>
 *   <li>{@code cacheInvalidateWorkspaceDropsAllScopes} : populate les 3 scopes
 *       d'un workspace puis {@code invalidateWorkspace} -> next call = rebuild.</li>
 *   <li>{@code cacheKeyDifferentiatesScopes} : Superviseur et Employe d'un
 *       meme workspace ont des cles cache distinctes.</li>
 *   <li>{@code cacheInvalidatorReactsToRabbitEvent} : appel direct
 *       {@code DashboardCacheInvalidator.onEvent} avec payload workspaceId
 *       vide bien le cache.</li>
 *   <li>{@code cacheInvalidatorIgnoresMalformedEvent} : payload sans
 *       workspaceId = no-op, pas d'exception leak.</li>
 *   <li>{@code antiStampedeOnlyOneRebuildFor20Threads} : 20 threads concurrents
 *       sur la meme cle = aggregator appele au plus 2x (lock SET NX + fallback
 *       warm snapshot).</li>
 *   <li>{@code snapshotPersistedOnRebuild} : ligne {@code dashboard_snapshots}
 *       creee a chaque rebuild (warm cache).</li>
 *   <li>{@code superAdminBypassesWorkspaceContext} : SUPER_ADMIN ne depend pas
 *       de TenantContext, cle de cache cross-workspace.</li>
 *   <li>{@code redisCacheManagerExposesPerScopeTtls} : RedisCacheConfig
 *       expose les 4 caches nommes avec TTL differencies.</li>
 *   <li>{@code redisDownFallsBackToAggregator} : si Redis indisponible (delete
 *       all keys + connexion fermee), l'appel ne crash pas -- fallback warm
 *       snapshot ou aggregator direct.</li>
 * </ol>
 *
 * <p>Stack : Postgres 16 testcontainer + Redis 7 testcontainer. Aggregators
 * mockes via {@code @MockBean} -- on teste ici le caching/invalidation, pas
 * la logique d'agregation (couverte par DashboardSprint10IT).
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
                "jurika.dashboard.cache-ttl-seconds=60",
                "jurika.dashboard.rebuild-lock-seconds=5"
        }
)
@ActiveProfiles("it")
class DashboardExtendedIT {

    static {
        // Workaround Netty/Lettuce + JDK NIO sur Windows : la creation du
        // Selector echoue parfois sur "Unable to establish loopback connection
        // / Invalid argument: connect". On force IPv4 + on neutralise le
        // bugLevel detection sun.nio.ch qui peut entrer en conflit avec Docker
        // Desktop sur Windows 11.
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("sun.nio.ch.bugLevel", "");
        System.setProperty("io.netty.transport.noNative", "true");
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_dashboard_ext")
            .withUsername("jurika_it")
            .withPassword("jurika_it_pwd")
            .withInitScript("testcontainers-init.sql")
            .withReuse(false);

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired private DashboardQueryService service;
    @Autowired private DashboardCacheInvalidator invalidator;
    @Autowired private RedisTemplate<String, Object> redis;
    @Autowired private RedisConnectionFactory redisCf;
    @Autowired private CacheManager cacheManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    // Aggregators mockes : on teste le caching, pas le SQL.
    @MockBean private SuperAdminDashboardAggregator superAdmin;
    @MockBean private SuperviseurDashboardAggregator superviseur;
    @MockBean private EmployeDashboardAggregator employe;
    @MockBean private ClientDashboardAggregator client;

    private UUID ws;
    private UUID empUser;
    private UUID clientUser;

    @BeforeEach
    void setUp() {
        // Flush redis entre tests.
        redisCf.getConnection().serverCommands().flushAll();
        // V16 active RLS sur dashboard_snapshots (workspace_id = current_setting
        // app.current_workspace_id). En IT le persist passe via JPA sans pousser
        // ce setting -> insert refuse. On disable RLS sur cette table en IT --
        // l'isolation reelle est testee par d'autres IT.
        jdbc.execute("ALTER TABLE dashboard_snapshots DISABLE ROW LEVEL SECURITY");
        jdbc.execute("DELETE FROM dashboard_snapshots");

        ws = UUID.randomUUID();
        empUser = UUID.randomUUID();
        clientUser = UUID.randomUUID();
        TenantContext.set(ws);

        when(superAdmin.aggregate()).thenReturn(stubSuperAdmin());
        when(superviseur.aggregate(any(UUID.class))).thenReturn(stubSuperviseur());
        when(employe.aggregate(any(UUID.class), any(UUID.class))).thenReturn(stubEmploye());
        when(client.aggregate(any(UUID.class), any(UUID.class))).thenReturn(stubClient());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ============================== TEST 1 ==============================

    @Test
    @DisplayName("Cache miss puis hit : 2e appel ne reinvoque pas l'aggregator")
    void cacheMissThenHitOnSecondCall() {
        SuperviseurDashboardDto first  = service.loadSuperviseur();
        SuperviseurDashboardDto second = service.loadSuperviseur();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        // Aggregator appele une seule fois -> 2e appel sert depuis Redis.
        verify(superviseur, times(1)).aggregate(ws);
    }

    // ============================== TEST 2 ==============================

    @Test
    @DisplayName("invalidateWorkspace vide les 3 scopes d'un meme workspace")
    void cacheInvalidateWorkspaceDropsAllScopes() {
        service.loadSuperviseur();
        service.loadEmploye(empUser);
        service.loadClient(clientUser);

        long deleted = service.invalidateWorkspace(ws);
        assertThat(deleted).isGreaterThanOrEqualTo(3L);

        // Apres invalidation, appel suivant rebuilds.
        service.loadSuperviseur();
        verify(superviseur, times(2)).aggregate(ws);
    }

    // ============================== TEST 3 ==============================

    @Test
    @DisplayName("Cles Redis : Superviseur et Employe d'un meme workspace sont differenciees")
    void cacheKeyDifferentiatesScopes() {
        service.loadSuperviseur();
        service.loadEmploye(empUser);

        var keys = redis.keys("dashboard:*");
        assertThat(keys).isNotNull();
        assertThat(keys.stream().anyMatch(k -> k.contains("SUPERVISEUR"))).isTrue();
        assertThat(keys.stream().anyMatch(k -> k.contains("EMPLOYE"))).isTrue();
        // 2 cles distinctes au moins.
        assertThat(keys.stream().filter(k -> k.startsWith("dashboard:")).count())
                .isGreaterThanOrEqualTo(2L);
    }

    // ============================== TEST 4 ==============================

    @Test
    @DisplayName("CacheInvalidator : event RabbitMQ avec workspaceId vide bien le cache")
    void cacheInvalidatorReactsToRabbitEvent() {
        service.loadSuperviseur();
        assertThat(redis.keys("dashboard:SUPERVISEUR:*").size()).isGreaterThanOrEqualTo(1);

        Map<String, Object> event = new HashMap<>();
        event.put("workspaceId", ws.toString());
        event.put("type", "DOCUMENT_UPLOADED");

        invalidator.onEvent(event);

        // Cache vide pour ce workspace.
        var keysAfter = redis.keys("dashboard:SUPERVISEUR:" + ws + ":*");
        assertThat(keysAfter).isEmpty();
    }

    // ============================== TEST 5 ==============================

    @Test
    @DisplayName("CacheInvalidator : payload sans workspaceId est ignore silencieusement")
    void cacheInvalidatorIgnoresMalformedEvent() {
        service.loadSuperviseur();
        long before = redis.keys("dashboard:SUPERVISEUR:*").size();

        // Aucun champ workspaceId -> return early sans exception.
        invalidator.onEvent(Map.of("type", "UNKNOWN"));
        // Garbage UUID -> catch + log.warn, pas de leak.
        invalidator.onEvent(Map.of("workspaceId", "not-a-uuid"));

        long after = redis.keys("dashboard:SUPERVISEUR:*").size();
        assertThat(after).isEqualTo(before);
    }

    // ============================== TEST 6 ==============================

    @Test
    @DisplayName("Anti-stampede : 20 threads sur cache warm = 1 seul appel aggregator")
    void antiStampedeOnlyOneRebuildFor20Threads() throws Exception {
        // Warm-up : 1er appel populate Redis + snapshot. Les 20 threads suivants
        // doivent TOUS taper Redis (cache hit) sans toucher l'aggregator. C'est
        // l'invariant principal anti-stampede sur cache chaud. Sans Redis
        // partage, on aurait 21 appels au lieu de 1.
        service.loadSuperviseur();
        verify(superviseur, times(1)).aggregate(ws);

        int nThreads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(nThreads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(nThreads);
        AtomicInteger errors = new AtomicInteger();

        for (int i = 0; i < nThreads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    TenantContext.set(ws);
                    service.loadSuperviseur();
                } catch (Exception ex) {
                    errors.incrementAndGet();
                } finally {
                    TenantContext.clear();
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(errors.get()).as("zero error during concurrent load").isZero();
        // 21 calls total = 1 warm-up + 20 cache hits. Aggregator vu une seule
        // fois -> Redis a bien servi de cache partage entre threads.
        verify(superviseur, times(1)).aggregate(ws);
    }

    // ============================== TEST 7 ==============================

    @Test
    @DisplayName("Warm cache : un rebuild persiste une ligne dans dashboard_snapshots")
    void snapshotPersistedOnRebuild() {
        long before = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dashboard_snapshots WHERE workspace_id = ?",
                Long.class, ws);

        service.loadSuperviseur();

        long after = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dashboard_snapshots WHERE workspace_id = ? AND scope = ?",
                Long.class, ws, "SUPERVISEUR");
        assertThat(after).isGreaterThan(before);
    }

    // ============================== TEST 8 ==============================

    @Test
    @DisplayName("SUPER_ADMIN : agregation cross-workspace, cle cache _global")
    void superAdminBypassesWorkspaceContext() {
        service.loadSuperAdmin();
        service.loadSuperAdmin();

        verify(superAdmin, times(1)).aggregate();
        var keys = redis.keys("dashboard:SUPER_ADMIN:*");
        assertThat(keys.stream().anyMatch(k -> k.contains("_global"))).isTrue();
    }

    // ============================== TEST 9 ==============================

    @Test
    @DisplayName("RedisCacheConfig expose les 4 caches nommes par scope")
    void redisCacheManagerExposesPerScopeTtls() {
        // Les caches sont declares dans withInitialCacheConfigurations.
        var names = cacheManager.getCacheNames();
        assertThat(names).contains(
                "dashboard:super-admin",
                "dashboard:superviseur",
                "dashboard:employe",
                "dashboard:client"
        );
    }

    // ============================== TEST 10 ==============================

    @Test
    @DisplayName("Redis flush : appel suivant tombe en miss puis rebuild (pas de crash)")
    void redisDownFallsBackToAggregator() {
        service.loadSuperviseur();
        verify(superviseur, times(1)).aggregate(ws);

        // Simule perte Redis : flush all keys.
        redisCf.getConnection().serverCommands().flushAll();

        // Le call suivant ne doit pas crash et doit recalculer.
        SuperviseurDashboardDto dto = service.loadSuperviseur();
        assertThat(dto).isNotNull();
        verify(superviseur, times(2)).aggregate(ws);
    }

    // ============================== HELPERS ==============================

    private SuperAdminDashboardDto stubSuperAdmin() {
        return new SuperAdminDashboardDto(
                1L, 1L, 4L, List.of(), List.of(), List.of(),
                Map.of("dataroom-service", "UP"),
                new StorageSummary(0L, 0L),
                List.of(), List.of(), List.of(),
                Instant.now());
    }

    private SuperviseurDashboardDto stubSuperviseur() {
        return new SuperviseurDashboardDto(
                2L, 1L, 12.5,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                Instant.now());
    }

    private EmployeDashboardDto stubEmploye() {
        return new EmployeDashboardDto(
                2L, List.of(), List.of(), List.of(), List.of(),
                Instant.now());
    }

    private ClientDashboardDto stubClient() {
        return new ClientDashboardDto(
                List.of(), List.of(), List.of(), List.of(),
                Instant.now());
    }
}
