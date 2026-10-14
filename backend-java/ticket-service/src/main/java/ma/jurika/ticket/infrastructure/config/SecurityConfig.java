package ma.jurika.ticket.infrastructure.config;

import org.springframework.beans.factory.ObjectProvider;
import ma.jurika.common.security.JwtAuthFilter;
import ma.jurika.common.trial.TrialSoftLockFilter;
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
        return JwtPublicKeyProviderFactory.build(props, "ticket-service");
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtPublicKeyProvider provider) {
        return new JwtAuthFilter(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ObjectProvider<TrialSoftLockFilter> filtresEssai, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Lot L4c (E1) : routes internes (bloquees a la passerelle, ports lies a
                        // 127.0.0.1), comme ai-service et workflow-service.
                        .requestMatchers("/internal/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        // Lot L7 (E4) : controles serveur de l'essai gratuit (RG-ESS-05 aucun telechargement,
        // RG-ESS-10 lecture seule), apres JwtAuthFilter qui pose le workspace courant.
        TrialSoftLockFilter filtreEssai = filtresEssai.getIfAvailable();
        if (filtreEssai != null) {
            http.addFilterAfter(filtreEssai, JwtAuthFilter.class);
        }
        return http.build();
    }
}
