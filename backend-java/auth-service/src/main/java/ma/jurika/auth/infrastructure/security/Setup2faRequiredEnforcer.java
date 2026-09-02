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
 * CRIT-2 (audit auth 2026-06-02) — Filtre qui bloque toutes les requetes
 * d'un utilisateur authentifie ayant {@code requires_2fa_setup=TRUE} dans son
 * JWT, sauf les endpoints autorises pour completer le setup.
 *
 * <p>Avant ce fix, {@code LoginUseCase} hardcodait {@code requires2faSetup=false}
 * dans la response, et aucun filtre serveur ne s'assurait que le user complete
 * son 2FA. La RG-AU30 (« Choix 2FA bloquant au 1er login ») etait silencieusement
 * contournee : un user pouvait acceder a toute l'app sans jamais activer 2FA.
 *
 * <p>Whitelist :
 * <ul>
 *     <li>{@code /api/v1/auth/2fa/**} : setup TOTP/SMS, generer recovery codes</li>
 *     <li>{@code /api/v1/auth/sms/**} : envoyer/verifier OTP pendant le setup SMS</li>
 *     <li>{@code POST /api/v1/auth/change-password} : si combo mcp+r2s</li>
 *     <li>{@code POST /api/v1/auth/logout} : permettre la deconnexion</li>
 *     <li>{@code GET /api/v1/auth/me} : afficher l'identite (redirection front)</li>
 *     <li>{@code /actuator/**}, {@code /v3/api-docs}, {@code /swagger-ui}</li>
 * </ul>
 *
 * <p>Reponse de blocage : <b>403 SETUP_2FA_REQUIRED</b>. Le frontend doit
 * intercepter et rediriger vers {@code /auth/setup-2fa}.
 *
 * <p>Order 21 = juste apres ChangePasswordEnforcer (20). Si l'user a aussi
 * mustChangePassword=TRUE, il sera bloque a 20 et redirige sur change-password
 * d'abord. Apres change-password reussi, un nouveau token sans mcp mais avec r2s
 * declenche ce filtre.
 */
@Component
@Order(21)
public class Setup2faRequiredEnforcer extends OncePerRequestFilter {

    private static final Set<String> WHITELIST = Set.of(
            "/api/v1/auth/change-password",
            "/api/v1/auth/logout",
            "/api/v1/auth/me"
    );

    private static final Set<String> WHITELIST_PREFIXES = Set.of(
            "/api/v1/auth/2fa",       // setup-totp / confirm / generate-recovery-codes
            "/api/v1/auth/sms",       // send / verify OTP du setup SMS
            "/api/v1/auth/setup-2fa", // initiate TOTP (POST) + /setup-2fa/confirm (sinon le filtre se bloque lui-meme : RG-AU30)
            // 2026-06-04 (fix P2) : coherence avec ChangePasswordEnforcer +
            // /refresh accessible meme avec r2s=true sinon impossible de renouveler
            // un token pendant la phase setup 2FA.
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

        Boolean requires = (Boolean) request.getAttribute(JwtClaims.REQUEST_ATTR_REQUIRES_2FA_SETUP);

        if (Boolean.TRUE.equals(requires) && !isWhitelisted(request.getRequestURI())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"code\":\"SETUP_2FA_REQUIRED\"," +
                    "\"message\":\"Vous devez configurer la double authentification avant d'utiliser l'application.\"," +
                    "\"redirectTo\":\"/auth/setup-2fa\"}");
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
