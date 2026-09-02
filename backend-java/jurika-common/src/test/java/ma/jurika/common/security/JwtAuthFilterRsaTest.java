package ma.jurika.common.security;

import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthFilterRsaTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void parsesRsaAccessTokenAndPopulatesSecurityContext() throws Exception {
        KeyPair kp = generateRsaKeypair();
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        String token = Jwts.builder()
                .subject(userId.toString())
                .issuer("jurika.ma")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(900)))
                .claim(JwtClaims.TYPE, JwtClaims.TYPE_ACCESS)
                .claim(JwtClaims.USER_ID, userId.toString())
                .claim(JwtClaims.WORKSPACE_ID, workspaceId.toString())
                .claim(JwtClaims.EMAIL, "karim@jurika.ma")
                .claim(JwtClaims.ROLE, Role.EMPLOYE.name())
                .claim(JwtClaims.MUST_CHANGE_PASSWORD, false)
                .signWith((PrivateKey) kp.getPrivate(), Jwts.SIG.RS256)
                .compact();

        PublicKey verifKey = kp.getPublic();
        JwtAuthFilter filter = new JwtAuthFilter(() -> verifKey);

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain chain = Mockito.mock(FilterChain.class);
        Mockito.when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(AuthenticatedUser.class);
        AuthenticatedUser principal = (AuthenticatedUser) auth.getPrincipal();
        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.workspaceId()).isEqualTo(workspaceId);
        assertThat(principal.email()).isEqualTo("karim@jurika.ma");
        assertThat(principal.role()).isEqualTo(Role.EMPLOYE);
        assertThat(auth.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_EMPLOYE");
        Mockito.verify(chain).doFilter(request, response);
    }

    @Test
    void invalidTokenIsSilentlyIgnored() throws Exception {
        KeyPair kp = generateRsaKeypair();
        PublicKey key = kp.getPublic();
        JwtAuthFilter filter = new JwtAuthFilter(() -> key);

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain chain = Mockito.mock(FilterChain.class);
        Mockito.when(request.getHeader("Authorization")).thenReturn("Bearer not-a-real-token");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        Mockito.verify(chain).doFilter(request, response);
    }

    private static KeyPair generateRsaKeypair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }
}
