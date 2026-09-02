package fr.maghreb.gje.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class TokenSessionService {

    private final StringRedisTemplate redisTemplate;
    // Fallback cache en mémoire si Redis bugge sous Windows Docker
    private static final Map<String, String> memoryCache = new ConcurrentHashMap<>();

    @Autowired
    public TokenSessionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String generateHash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 introuvable", e);
        }
    }

    public void registerSession(String userId, String accessToken, String refreshToken) {
        try {
            String key = "active_token:" + userId;
            String value = generateHash(accessToken) + ":" + generateHash(refreshToken);
            
            // Set directly in Redis with a 24-hour TTL
            redisTemplate.opsForValue().set(key, value, 24, TimeUnit.HOURS);
            log.debug("Session registered in Redis for user: {}", userId);
        } catch (Exception e) {
            log.warn("Redis injoignable (Enregistrement session user {}). Fallback sur cache mémoire.", userId);
            memoryCache.put("active_token:" + userId, generateHash(accessToken) + ":" + generateHash(refreshToken));
        }
    }

    public void invalidateSession(String userId) {
        try {
            String key = "active_token:" + userId;
            redisTemplate.delete(key);
            log.debug("Session invalidated in Redis for user: {}", userId);
        } catch (Exception e) {
            log.warn("Redis injoignable (Invalidation session user {}). Fallback sur cache mémoire.", userId);
        } finally {
            // Toujours nettoyer la mémoire
            memoryCache.remove("active_token:" + userId);
        }
    }

    public boolean isTokenValid(String userId, String tokenToCheck, boolean isRefreshToken) {
        String storedValue = null;
        try {
            String key = "active_token:" + userId;
            storedValue = redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Redis error in isTokenValid: ", e);
            log.warn("Redis injoignable (Vérif session user {}). Fallback sur cache mémoire.", userId);
            storedValue = memoryCache.get("active_token:" + userId);
        }

        if (storedValue == null) {
            return false;
        }

        String[] parts = storedValue.split(":");
        if (parts.length != 2) {
            return false;
        }

        String storedAccessTokenHash = parts[0];
        String storedRefreshTokenHash = parts[1];
        String incomingHash = generateHash(tokenToCheck);

        if (isRefreshToken) {
            boolean result = storedRefreshTokenHash.equals(incomingHash);
            log.info("Token session check (refresh): result={}, userId={}", result, userId);
            return result;
        } else {
            boolean result = storedAccessTokenHash.equals(incomingHash);
            log.info("Token session check (access): result={}, userId={}, incomingHash={}, storedHash={}", result, userId, incomingHash, storedAccessTokenHash);
            return result;
        }
    }
}
