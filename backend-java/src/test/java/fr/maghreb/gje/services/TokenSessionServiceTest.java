package fr.maghreb.gje.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
public class TokenSessionServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TokenSessionService tokenSessionService;

    private final String userId = UUID.randomUUID().toString();
    private final String accessToken = "eyJhbGc.fake_access_token.signature";
    private final String refreshToken = "eyJhbGc.fake_refresh_token.signature";

    @BeforeEach
    void setUp() {
        // Prepare mock setup
    }

    @Test
    void testRegisterSession() {
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        tokenSessionService.registerSession(userId, accessToken, refreshToken);

        Mockito.verify(valueOperations, Mockito.times(1))
                .set(eq("active_token:" + userId), any(String.class), eq(24L), eq(TimeUnit.HOURS));
    }

    @Test
    void testInvalidateSession() {
        tokenSessionService.invalidateSession(userId);

        Mockito.verify(redisTemplate, Mockito.times(1)).delete("active_token:" + userId);
    }

    @Test
    void testIsTokenValid_Success() {
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        String accessHash = tokenSessionService.generateHash(accessToken);
        String refreshHash = tokenSessionService.generateHash(refreshToken);
        String storedValue = accessHash + ":" + refreshHash;

        Mockito.when(valueOperations.get("active_token:" + userId)).thenReturn(storedValue);

        // Verify Access token
        assertTrue(tokenSessionService.isTokenValid(userId, accessToken, false));

        // Verify Refresh token
        assertTrue(tokenSessionService.isTokenValid(userId, refreshToken, true));
    }

    @Test
    void testIsTokenValid_FailsOnMismatchedToken() {
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        String accessHash = tokenSessionService.generateHash(accessToken);
        String refreshHash = tokenSessionService.generateHash(refreshToken);
        String storedValue = accessHash + ":" + refreshHash;

        Mockito.when(valueOperations.get("active_token:" + userId)).thenReturn(storedValue);

        // Provide wrong token
        assertFalse(tokenSessionService.isTokenValid(userId, "eyJhbGc.compromised_token.xxx", false));
    }

    @Test
    void testIsTokenValid_FailOpenOnRedisConnectionError() {
        Mockito.when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

        // Fail-open means it returns true if Redis throws an exception
        assertTrue(tokenSessionService.isTokenValid(userId, accessToken, false));
    }
}
