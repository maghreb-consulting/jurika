package fr.maghreb.gje.controllers;

import fr.maghreb.gje.dto.auth.VerifyTotpRequest;
import fr.maghreb.gje.services.AuthService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthController2FATest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Test
    public void testVerify2FAMaxAttemptsReturns429() throws Exception {
        // Enforce the mocked AuthService to throw the MAX_OTP_ATTEMPTS exception specifically
        Mockito.when(authService.verify2FA(Mockito.any(VerifyTotpRequest.class)))
               .thenThrow(new RuntimeException("MAX_OTP_ATTEMPTS:Nombre maximum de tentatives OTP atteint. Reconnectez-vous."));

        // Simulate the payload
        String jsonPayload = """
            {
                "tempToken": "fake-jwt-token",
                "otpCode": "123456"
            }
            """;

        // Perform the request and expect 429 Too Many Requests along with custom JSON format
        mockMvc.perform(post("/api/v1/auth/verify-2fa")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("otp_max_attempts"))
                .andExpect(jsonPath("$.message").value("Nombre maximum de tentatives OTP atteint. Reconnectez-vous."))
                .andExpect(jsonPath("$.requires_login").value(true));
    }
}
