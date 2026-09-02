package fr.maghreb.gje.security;

import fr.maghreb.gje.config.TenantContext;
import fr.maghreb.gje.repositories.UserRepository;
import fr.maghreb.gje.services.SessionActivityService;
import fr.maghreb.gje.services.TokenSessionService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final TokenSessionService tokenSessionService;
    private final SessionActivityService sessionActivityService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        if (!jwtService.isTokenValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        String tokenType = jwtService.extractTokenType(token);
        String path = request.getServletPath();

        // Allow TEMP only for specific auth endpoints
        if ("TEMP".equals(tokenType)) {
            if (!path.contains("/auth/setup-2fa") && !path.contains("/auth/verify-2fa")) {
                filterChain.doFilter(request, response);
                return;
            }
        } else if (!"ACCESS".equals(tokenType)) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = jwtService.extractUserId(token);
        String workspaceId = jwtService.extractWorkspaceId(token);

        // Strict Single-Session Enforcement
        if ("ACCESS".equals(tokenType)) {
            if (!tokenSessionService.isTokenValid(userId, token, false)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Session invalide, reconnectez-vous\"}");
                return;
            }

            // Sliding Session: Inactivity Tracking
            if (!sessionActivityService.isActive(userId)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{" +
                        "\"error\": \"session_inactive\"," +
                        "\"message\": \"Session expirée après 30 minutes d'inactivité. Veuillez vous reconnecter.\"," +
                        "\"redirect\": \"/login\"" +
                        "}");
                return;
            }
            // Touch activity to reset the 30min timer
            sessionActivityService.touch(userId);
        }

        TenantContext.setTenantId(workspaceId);

        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                userRepository.findById(UUID.fromString(userId)).ifPresent(user -> {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    user, null, user.getAuthorities());
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                });
            }
        } catch (Exception e) {
            // Ignore DB errors (RLS restrictions) for permitAll contexts
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
