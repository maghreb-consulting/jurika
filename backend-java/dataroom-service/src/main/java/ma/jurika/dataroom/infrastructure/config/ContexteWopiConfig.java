package ma.jurika.dataroom.infrastructure.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.application.WopiService;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lot L0 (E15, inventaire W1) : Collabora appelle les routes WOPI sans jeton
 * JWT. Le workspace de la seance, retrouve par le jeton opaque
 * {@code access_token}, devient le workspace courant AVANT le controleur, donc
 * avant toute transaction : la RLS s'applique a ce workspace. Un jeton inconnu
 * ne pose rien ; le service refuse alors la requete comme avant (401).
 * JwtAuthFilter vide le contexte en fin de requete.
 */
@Configuration
public class ContexteWopiConfig implements WebMvcConfigurer {

    private final WopiService wopi;

    public ContexteWopiConfig(@Lazy WopiService wopi) {
        this.wopi = wopi;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                wopi.workspaceDuJeton(request.getParameter("access_token")).ifPresent(TenantContext::set);
                return true;
            }
        }).addPathPatterns("/api/v1/dataroom/wopi/**");
    }
}
