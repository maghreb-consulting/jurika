package ma.jurika.dashboard.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Sprint 10 -- Cache Redis pour les dashboards.
 * TTL court par defaut (60s) + caches differencies par scope.
 */
@Configuration
public class RedisCacheConfig {

    /**
     * ObjectMapper du service.
     *
     * <p><b>Bug dates "1970" (Lot AC).</b> Declarer un bean de type
     * {@link ObjectMapper} desactive l'auto-configuration Jackson de Spring Boot
     * ({@code @ConditionalOnMissingBean(ObjectMapper.class)}). Ce bean devient
     * donc AUSSI le mapper des reponses HTTP MVC. Un {@code new ObjectMapper()}
     * brut a {@code WRITE_DATES_AS_TIMESTAMPS} ACTIVE par defaut : un
     * {@link java.time.Instant} etait serialise en secondes epoch (nombre
     * ~1.75e9), que le front interpretait en millisecondes via {@code new Date(x)}
     * -> ~20 janvier 1970. On desactive donc explicitement ce flag pour rendre la
     * serialisation ISO-8601 (chaine {@code "2026-07-..."}), cote HTTP comme cote
     * cache Redis.
     */
    @Bean
    public ObjectMapper dashboardObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory cf, ObjectMapper objectMapper) {
        GenericJackson2JsonRedisSerializer json = new GenericJackson2JsonRedisSerializer(objectMapper);
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(60))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(json));

        return RedisCacheManager.builder(cf)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(Map.of(
                        "dashboard:super-admin", base.entryTtl(Duration.ofSeconds(120)),
                        "dashboard:superviseur", base.entryTtl(Duration.ofSeconds(60)),
                        "dashboard:employe",    base.entryTtl(Duration.ofSeconds(60)),
                        "dashboard:client",     base.entryTtl(Duration.ofSeconds(60))
                ))
                .transactionAware()
                .build();
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory cf, ObjectMapper objectMapper) {
        RedisTemplate<String, Object> t = new RedisTemplate<>();
        t.setConnectionFactory(cf);
        t.setKeySerializer(new StringRedisSerializer());
        t.setHashKeySerializer(new StringRedisSerializer());
        GenericJackson2JsonRedisSerializer json = new GenericJackson2JsonRedisSerializer(objectMapper);
        t.setValueSerializer(json);
        t.setHashValueSerializer(json);
        return t;
    }
}
