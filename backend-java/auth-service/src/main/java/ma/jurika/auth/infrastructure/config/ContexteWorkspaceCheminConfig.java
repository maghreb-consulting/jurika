package ma.jurika.auth.infrastructure.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ma.jurika.common.security.TenantContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lot L0 (E13b) : pour les routes dont le workspace CIBLE est dans le chemin
 * ({@code /api/v1/admin/workspaces/{workspaceId}/**} du super-admin,
 * {@code /internal/workspaces/{workspaceId}/**} appelees par les autres
 * services), ce workspace devient le workspace courant AVANT le controleur,
 * donc avant toute transaction : la RLS s'applique a ce workspace et a lui
 * seul. L'autorisation reste celle du controleur (@PreAuthorize pour le
 * super-admin) ; JwtAuthFilter vide le contexte en fin de requete.
 */
@Configuration
public class ContexteWorkspaceCheminConfig implements WebMvcConfigurer {

    static final Pattern WORKSPACE_DANS_LE_CHEMIN = Pattern.compile(
            "^(?:/api/v1/admin/workspaces|/internal/workspaces)/([0-9a-fA-F-]{36})(?:/.*)?$");

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                Matcher m = WORKSPACE_DANS_LE_CHEMIN.matcher(request.getRequestURI());
                if (m.matches()) {
                    try {
                        TenantContext.set(UUID.fromString(m.group(1)));
                    } catch (IllegalArgumentException identifiantInvalide) {
                        // Le controleur rejettera le parametre (400) : rien a poser.
                    }
                }
                return true;
            }
        }).addPathPatterns("/api/v1/admin/workspaces/**", "/internal/workspaces/**");
    }
}
