package ma.jurika.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ma.jurika.common.observability.SentryContextEnricher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String BEARER = "Bearer ";
    private static final String MDC_WORKSPACE = "workspaceId";
    private static final String MDC_USER = "userId";

    private final JwtPublicKeyProvider keyProvider;

    public JwtAuthFilter(JwtPublicKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER.length());
        try {
            java.security.Key key = keyProvider.verificationKey();
            io.jsonwebtoken.JwtParserBuilder parserBuilder = Jwts.parser();
            if (key instanceof javax.crypto.SecretKey sk) {
                parserBuilder.verifyWith(sk);
            } else if (key instanceof java.security.PublicKey pk) {
                parserBuilder.verifyWith(pk);
            } else {
                throw new IllegalStateException("Cle de verification JWT inconnue");
            }
            Claims claims = parserBuilder
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String type = claims.get(JwtClaims.TYPE, String.class);
            if (!JwtClaims.TYPE_ACCESS.equals(type)) {
                chain.doFilter(request, response);
                return;
            }

            UUID userId = UUID.fromString(claims.get(JwtClaims.USER_ID, String.class));
            UUID workspaceId = UUID.fromString(claims.get(JwtClaims.WORKSPACE_ID, String.class));
            String email = claims.get(JwtClaims.EMAIL, String.class);
            Role role = Role.valueOf(claims.get(JwtClaims.ROLE, String.class));
            Boolean mustChangePassword = claims.get(JwtClaims.MUST_CHANGE_PASSWORD, Boolean.class);
            Boolean requires2faSetup = claims.get(JwtClaims.REQUIRES_2FA_SETUP, Boolean.class);

            AuthenticatedUser principal = new AuthenticatedUser(userId, workspaceId, email, role);
            TenantContext.set(workspaceId);
            MDC.put(MDC_WORKSPACE, workspaceId.toString());
            MDC.put(MDC_USER, userId.toString());
            SentryContextEnricher.enrich(workspaceId, userId, MDC.get("correlationId"));

            // Expose les flags pour les enforcers downstream
            request.setAttribute(JwtClaims.REQUEST_ATTR_MUST_CHANGE_PASSWORD,
                    Boolean.TRUE.equals(mustChangePassword));
            request.setAttribute(JwtClaims.REQUEST_ATTR_REQUIRES_2FA_SETUP,
                    Boolean.TRUE.equals(requires2faSetup));

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);

            chain.doFilter(request, response);
        } catch (Exception ex) {
            // Token invalide/expire : on N'INTERROMPT PAS la requete.
            // On laisse Spring Security decider via les @PreAuthorize / SecurityFilterChain.
            // Cela permet aux endpoints publics (/auth/login, /auth/register, etc.)
            // de fonctionner meme si le client envoie un Authorization stale.
            log.debug("Invalid JWT (ignored, passing through): {}", ex.getMessage());
            SecurityContextHolder.clearContext();
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove(MDC_WORKSPACE);
            MDC.remove(MDC_USER);
            SentryContextEnricher.clearScope();
        }
    }
}
