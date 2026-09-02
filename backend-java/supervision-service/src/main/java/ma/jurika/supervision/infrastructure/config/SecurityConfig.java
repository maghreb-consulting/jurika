package ma.jurika.supervision.infrastructure.config;

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

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public JwtPublicKeyProvider jwtPublicKeyProvider(JwtProperties props) {
        return JwtPublicKeyProviderFactory.build(props, "supervision-service");
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtPublicKeyProvider provider) {
        return new JwtAuthFilter(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                // Sprint 11 TASK 6 -- ingestion business events
                                // (auth applicative via header X-Internal-Token,
                                // active si jurika.internal.token-required=true)
                                "/internal/events"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        // UX-3 (2026-06-02) : TrialSoftLockFilter supprime (concept trial efface).
        return http.build();
    }
}
