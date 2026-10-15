package ma.jurika.dashboard.infrastructure.config;

import ma.jurika.common.security.JwtAuthFilter;
import ma.jurika.common.security.JwtProperties;
import ma.jurika.common.security.JwtPublicKeyProvider;
import ma.jurika.common.security.JwtPublicKeyProviderFactory;
import ma.jurika.common.trial.TrialSoftLockFilter;
import org.springframework.beans.factory.ObjectProvider;
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
        return JwtPublicKeyProviderFactory.build(props, "dashboard-service");
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtPublicKeyProvider provider) {
        return new JwtAuthFilter(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                                   ObjectProvider<TrialSoftLockFilter> filtresEssai) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info",
                                "/actuator/prometheus",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        // Decision du 2026-10-14 (RG-ESS-05) : le filtre commun de l'essai est branche ici aussi
        // (il avait ete retire en UX-3) : pendant l'essai, aucun fichier ne sort de ce service
        // (export CSV compris), quelle que soit la route.
        TrialSoftLockFilter filtreEssai = filtresEssai.getIfAvailable();
        if (filtreEssai != null) {
            http.addFilterAfter(filtreEssai, JwtAuthFilter.class);
        }
        return http.build();
    }
}
