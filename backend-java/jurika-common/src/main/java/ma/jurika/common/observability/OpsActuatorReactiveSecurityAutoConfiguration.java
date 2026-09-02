package ma.jurika.common.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

/**
 * Sprint 2 / TASK 2 — équivalent réactif de {@link OpsActuatorSecurityAutoConfiguration}.
 *
 * <p>Le gateway (Spring Cloud Gateway, WebFlux) reçoit deux {@code SecurityWebFilterChain} :
 * <ul>
 *   <li>Ordre HIGHEST_PRECEDENCE : intercepte {@code /actuator/**} avec HTTP Basic ops.</li>
 *   <li>Ordre par défaut (chain "passe-tout") : laisse passer tout le reste pour que les
 *       services downstream gardent la responsabilité de l'auth JWT.</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.security.config.web.server.ServerHttpSecurity")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(name = "jurika.observability.prometheus.password")
@EnableWebFluxSecurity
public class OpsActuatorReactiveSecurityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OpsActuatorReactiveSecurityAutoConfiguration.class);

    public static final String ROLE = "OPS";
    public static final String OPS_USERNAME = "ops";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityWebFilterChain opsActuatorWebFilterChain(ServerHttpSecurity http) {
        http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/actuator/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .authorizeExchange(auth -> auth
                        .pathMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .anyExchange().hasRole(ROLE))
                .httpBasic(Customizer.withDefaults());
        log.info("Sprint 2 TASK 2 reactive : /actuator/** securise (user={}, role={})", OPS_USERNAME, ROLE);
        return http.build();
    }

    /**
     * Chaîne par défaut pour le gateway réactif : permettre toutes les autres routes
     * (les services downstream font leur propre vérification JWT).
     */
    @Bean
    public SecurityWebFilterChain gatewayPassthroughChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(auth -> auth.anyExchange().permitAll());
        return http.build();
    }

    @Bean
    public MapReactiveUserDetailsService opsReactiveUserDetailsService(
            @Value("${jurika.observability.prometheus.password}") String password,
            PasswordEncoder passwordEncoder) {
        UserDetails ops = User.withUsername(OPS_USERNAME)
                .password(passwordEncoder.encode(password))
                .roles(ROLE)
                .build();
        return new MapReactiveUserDetailsService(ops);
    }

    @Bean
    public PasswordEncoder opsReactivePasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
