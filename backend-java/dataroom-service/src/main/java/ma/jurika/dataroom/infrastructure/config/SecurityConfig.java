package ma.jurika.dataroom.infrastructure.config;

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
        return JwtPublicKeyProviderFactory.build(props, "dataroom-service");
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
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Sprint 14 ter E2 — TestSeedController : protege par double verrou
                        // @Profile("!prod") + @ConditionalOnProperty(jurika.test.seed.enabled=true).
                        // En prod l'endpoint n'existe meme pas, donc permitAll ici est sans risque.
                        .requestMatchers("/api/v1/test/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        // UX-3 (2026-06-02) : TrialSoftLockFilter supprime (concept trial efface).
        return http.build();
    }
}
