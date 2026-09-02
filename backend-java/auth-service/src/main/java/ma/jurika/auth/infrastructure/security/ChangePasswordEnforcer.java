package ma.jurika.auth.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ma.jurika.common.security.JwtClaims;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Filtre qui bloque toutes les requetes d'un utilisateur authentifie ayant
 * {@code must_change_password=TRUE}, sauf les endpoints autorises :
 * <ul>
 *     <li>{@code POST /api/v1/auth/change-password} : le seul endpoint qui resout le blocage</li>
 *     <li>{@code POST /api/v1/auth/logout} : permettre de se deconnecter</li>
 *     <li>{@code GET /api/v1/auth/me} : afficher l'identite (utile pour la redirection front)</li>
 *     <li>{@code /actuator/**} : monitoring</li>
 * </ul>
 *
 * <p>Reponse en cas de blocage : <b>403 PASSWORD_CHANGE_REQUIRED</b>.
 * Le frontend doit intercepter ce code et rediriger vers l'ecran "Changer le mot de passe".
 *
 * <p>S'execute APRES JwtAuthFilter (qui pose l'attribut de requete), donc Order = 0
 * suffit avec OncePerRequestFilter (cf. SecurityConfig pour l'ordre exact).
 */
@Component
@Order(20)
public class ChangePasswordEnforcer extends OncePerRequestFilter {

    private static final Set<String> WHITELIST = Set.of(
            "/api/v1/auth/change-password",
            "/api/v1/auth/logout",
            "/api/v1/auth/me"
    );

    private static final Set<String> WHITELIST_PREFIXES = Set.of(
            // 2026-06-04 (fix P2) : endpoints publics (pricing, leads, events, signup,
            // verify-email, password-reset, refresh) ne doivent JAMAIS etre bloques
            // par un claim mcp=true residuel. Cas typique : user qui rouvre signup
            // tab avec un vieux JWT en localStorage du compte precedent.
            "/api/v1/public",
            "/api/v1/auth/refresh",
            "/actuator",
            "/v3/api-docs",
            "/swagger-ui"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {

        Boolean mustChange = (Boolean) request.getAttribute(JwtClaims.REQUEST_ATTR_MUST_CHANGE_PASSWORD);

        if (Boolean.TRUE.equals(mustChange) && !isWhitelisted(request.getRequestURI())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"code\":\"PASSWORD_CHANGE_REQUIRED\"," +
                    "\"message\":\"Vous devez changer votre mot de passe avant d'utiliser l'application.\"," +
                    "\"redirectTo\":\"/auth/change-password\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isWhitelisted(String uri) {
        if (WHITELIST.contains(uri)) return true;
        for (String prefix : WHITELIST_PREFIXES) {
            if (uri.startsWith(prefix)) return true;
        }
        return false;
    }
}
