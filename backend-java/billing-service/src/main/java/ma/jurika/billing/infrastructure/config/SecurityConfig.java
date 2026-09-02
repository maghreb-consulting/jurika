package ma.jurika.billing.infrastructure.config;

import ma.jurika.common.security.JwtAuthFilter;
import ma.jurika.common.security.JwtProperties;
import ma.jurika.common.security.JwtPublicKeyProvider;
import ma.jurika.common.security.JwtPublicKeyProviderFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Sprint 12 — Security config billing-service.
 *
 * <p>Regles :
 *  - {@code /api/v1/billing/webhook/**} : permit ALL (Stripe ne porte pas notre
 *    JWT) ; la securite est la signature HMAC verifie dans le controller (RG-BL18).
 *  - {@code /actuator/**} : permit (basic auth ops si configuree separement).
 *  - {@code /swagger-ui/**}, {@code /v3/api-docs/**} : permit.
 *  - Tout le reste de {@code /api/v1/billing/**} : authentifie + {@code ROLE_ADMIN_CABINET}
 *    par @PreAuthorize sur les controllers (cf. PLAN §2.6).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Sprint 12 fix — bean manquant qui empechait billing-service de booter.
     * Aligne sur le pattern des autres services (workflow, dataroom, ticket, etc.).
     */
    @Bean
    public JwtPublicKeyProvider jwtPublicKeyProvider(JwtProperties props) {
        return JwtPublicKeyProviderFactory.build(props, "billing-service");
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtPublicKeyProvider provider) {
        return new JwtAuthFilter(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // AntPath explicit -- evite l'ambiguite MvcRequestMatcher en
                        // Spring Security 6.4 sur les paths /api/v1/billing/webhook/**.
                        .requestMatchers(
                                new AntPathRequestMatcher("/api/v1/billing/webhook/**"),
                                new AntPathRequestMatcher("/actuator/**"),
                                new AntPathRequestMatcher("/v3/api-docs/**"),
                                new AntPathRequestMatcher("/swagger-ui.html"),
                                new AntPathRequestMatcher("/swagger-ui/**")
                        ).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
