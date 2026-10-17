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
 *  - {@code /api/v1/billing/cmi/**} : permit ALL (le CMI ne porte pas notre JWT) ; la securite
 *    du retour serveur est la signature verifiee (RG-PAY-05, Lot L7). Stripe est retire.
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
                                // Lot L7 (E6) : controle "contrat en attente" de la passerelle
                                // (appel interne ; /internal/** est refuse a l'entree publique).
                                new AntPathRequestMatcher("/internal/contrat/**"),
                                // Lot L7 (E14) : suppression d'un essai, appelee par auth-service
                                // (meme regime : /internal/** refuse a l'entree publique).
                                new AntPathRequestMatcher("/internal/essais/**"),
                                // Lot L7 (E5) : points d'entree du CMI (retour serveur signe,
                                // retour du navigateur, etat de la liaison) et simulateur de test
                                // (present seulement si CMI_SIMULATEUR_ACTIF=true).
                                new AntPathRequestMatcher("/api/v1/billing/cmi/**"),
                                new AntPathRequestMatcher("/api/v1/billing/cmi-simulateur/**"),
                                // Lot L6b (E2) : mentions legales publiques de l'editeur (vitrine),
                                // lecture seule ; chemin exact, GET seulement.
                                new AntPathRequestMatcher("/api/v1/public/editeur/mentions-legales", "GET"),
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
