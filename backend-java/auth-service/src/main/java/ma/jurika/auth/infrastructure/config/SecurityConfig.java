package ma.jurika.auth.infrastructure.config;

import ma.jurika.auth.infrastructure.security.ChangePasswordEnforcer;
import ma.jurika.auth.infrastructure.security.Setup2faRequiredEnforcer;
import ma.jurika.common.security.JwtAuthFilter;
import ma.jurika.common.security.JwtPublicKeyProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtPublicKeyProvider provider) {
        return new JwtAuthFilter(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    JwtAuthFilter jwtAuthFilter,
                                                    ChangePasswordEnforcer changePasswordEnforcer,
                                                    Setup2faRequiredEnforcer setup2faRequiredEnforcer) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Pattern matchers explicites (AntPath) — evite l'ambiguite
                        // MvcRequestMatcher en Spring Security 6.4 quand plusieurs
                        // HandlerMappings/ServletRegistrations coexistent (actuator, swagger).
                        .requestMatchers(
                                new AntPathRequestMatcher("/api/v1/auth/register"),
                                new AntPathRequestMatcher("/api/v1/auth/workspace-check"),
                                new AntPathRequestMatcher("/api/v1/auth/login"),
                                new AntPathRequestMatcher("/api/v1/auth/login/sms/challenge"),
                                new AntPathRequestMatcher("/api/v1/auth/verify-2fa"),
                                new AntPathRequestMatcher("/api/v1/auth/verify-recovery-code"),
                                new AntPathRequestMatcher("/api/v1/auth/verify-email"),
                                new AntPathRequestMatcher("/api/v1/auth/resend-verification"),
                                new AntPathRequestMatcher("/api/v1/auth/refresh"),
                                new AntPathRequestMatcher("/api/v1/auth/password-reset/**"),
                                new AntPathRequestMatcher("/api/v1/public/**"),
                                // Sprint 12 -- endpoints internes pour billing-service + 5 microservices.
                                // Filtres au gateway (pas de route /internal/** exposee publiquement).
                                new AntPathRequestMatcher("/internal/**"),
                                new AntPathRequestMatcher("/actuator/health"),
                                new AntPathRequestMatcher("/actuator/health/**"),
                                new AntPathRequestMatcher("/actuator/info"),
                                new AntPathRequestMatcher("/v3/api-docs/**"),
                                new AntPathRequestMatcher("/swagger-ui/**"),
                                new AntPathRequestMatcher("/swagger-ui.html")
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(changePasswordEnforcer, JwtAuthFilter.class)
                // CRIT-2 (audit 2026-06-02) -- bloque tout sauf /auth/2fa/* tant que
                // requires_2fa_setup=TRUE dans le JWT (RG-AU30 enforce serveur).
                .addFilterAfter(setup2faRequiredEnforcer, ChangePasswordEnforcer.class);
        // UX-3 (2026-06-02) : TrialSoftLockFilter supprime (concept trial efface du produit).

        return http.build();
    }
}
