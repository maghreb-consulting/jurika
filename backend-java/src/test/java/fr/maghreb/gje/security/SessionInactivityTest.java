package fr.maghreb.gje.security;

import fr.maghreb.gje.models.User;
import fr.maghreb.gje.repositories.UserRepository;
import fr.maghreb.gje.services.SessionActivityService;
import fr.maghreb.gje.services.TokenSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.flyway.validate-on-migrate=false",
    "spring.flyway.ignore-missing-migrations=true",
    "spring.jpa.hibernate.ddl-auto=update"
})
@AutoConfigureMockMvc
public class SessionInactivityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private TokenSessionService tokenSessionService;

    @MockBean
    private SessionActivityService sessionActivityService;

    private String validToken;
    private final UUID userId = UUID.randomUUID();
    private final UUID workspaceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(userId)
                .workspaceId(workspaceId)
                .email("test@example.com")
                .role(User.Role.EMPLOYE)
                .fullName("Test User")
                .isActive(true)
                .build();

        validToken = jwtService.generateAccessToken(user);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tokenSessionService.isTokenValid(anyString(), anyString(), eq(false))).thenReturn(true);
    }

    @Test
    void whenSessionIsActive_shouldAllowRequestAndTouchActivity() throws Exception {
        when(sessionActivityService.isActive(userId.toString())).thenReturn(true);

        mockMvc.perform(get("/api/v1/dossiers")
                .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk());

        verify(sessionActivityService).touch(userId.toString());
    }

    @Test
    void whenSessionIsInactive_shouldReturn401WithSpecificBody() throws Exception {
        when(sessionActivityService.isActive(userId.toString())).thenReturn(false);

        mockMvc.perform(get("/api/v1/dossiers")
                .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("session_inactive"))
                .andExpect(jsonPath("$.message", containsString("30 minutes")));

        verify(sessionActivityService, never()).touch(anyString());
    }
}
