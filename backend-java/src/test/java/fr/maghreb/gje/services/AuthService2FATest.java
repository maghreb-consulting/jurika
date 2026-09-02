package fr.maghreb.gje.services;

import fr.maghreb.gje.security.EncryptionUtil;
import fr.maghreb.gje.dto.auth.VerifyTotpRequest;
import fr.maghreb.gje.models.User;
import fr.maghreb.gje.repositories.UserRepository;
import fr.maghreb.gje.security.JwtService;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
public class AuthService2FATest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private EncryptionUtil encryptionUtil;

    // Use InjectMocks to inject components automatically without spring Context
    @InjectMocks
    private AuthService authService;

    private User mockUser;
    private final UUID userId = UUID.randomUUID();
    private final UUID workspaceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(userId);
        mockUser.setWorkspaceId(workspaceId);
        mockUser.setTotpSecret("encrypted_secret");
        
    }

    @Test
    void testVerify2FAMaxAttempts() {
        // Mock token validation
        DefaultClaims claims = new DefaultClaims();
        claims.put("type", "TEMP");
        claims.put("workspace_id", workspaceId.toString());
        claims.setSubject(userId.toString());

        Mockito.when(jwtService.extractAllClaims("fake-temp-token")).thenReturn(claims);
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        Mockito.when(encryptionUtil.decrypt("encrypted_secret")).thenReturn("decrypted_secret");

        // Request with invalid OTP
        VerifyTotpRequest req = new VerifyTotpRequest();
        req.setTempToken("fake-temp-token");
        req.setOtpCode("000000"); // wrong setup

        // Attempt 1: Code OTP invalide -> throws generic OTP exception
        RuntimeException ex1 = assertThrows(RuntimeException.class, () -> authService.verify2FA(req));
        assertTrue(ex1.getMessage().contains("Code OTP"));

        // Attempt 2: Code OTP invalide -> throws max limit
        RuntimeException ex2 = assertThrows(RuntimeException.class, () -> authService.verify2FA(req));
        assertTrue(ex2.getMessage().contains("MAX_OTP_ATTEMPTS"));

        // Attempt 3: Tripped limits at start, should throw MAX_OTP unconditionally
        RuntimeException ex3 = assertThrows(RuntimeException.class, () -> authService.verify2FA(req));
        assertTrue(ex3.getMessage().contains("MAX_OTP_ATTEMPTS"));
    }
}
