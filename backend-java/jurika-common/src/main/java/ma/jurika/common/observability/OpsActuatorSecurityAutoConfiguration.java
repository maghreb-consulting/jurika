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
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Sprint 2 / TASK 2 — sécurisation centralisée des endpoints {@code /actuator/**} sur
 * les services servlet (auth, ticket, workflow, dataroom, ai, supervision).
 *
 * <p>Chaîne de filtres dédiée avec ordre élevé qui intercepte uniquement {@code /actuator/**} :
 * <ul>
 *   <li>{@code /actuator/health/**} + {@code /actuator/info} sont publics (probes Docker/K8s).</li>
 *   <li>Tous les autres endpoints (prometheus, metrics, loggers, env, ...) exigent
 *       l'utilisateur in-memory {@code ops} via HTTP Basic, qui détient {@code ROLE_OPS}.</li>
 * </ul>
 *
 * <p>Activée uniquement si la propriété {@code jurika.observability.prometheus.password}
 * est définie ; sinon les endpoints actuator restent ouverts pour ne pas casser le dev.
 */
@AutoConfiguration
@ConditionalOnClass(SecurityFilterChain.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "jurika.observability.prometheus.password")
public class OpsActuatorSecurityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OpsActuatorSecurityAutoConfiguration.class);

    public static final String ROLE = "OPS";
    public static final String OPS_USERNAME = "ops";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain opsActuatorSecurityFilterChain(HttpSecurity http,
                                                              InMemoryUserDetailsManager opsUserDetailsService) throws Exception {
        http
                .securityMatcher(new AntPathRequestMatcher("/actuator/**"))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().hasRole(ROLE))
                .httpBasic(Customizer.withDefaults())
                .userDetailsService(opsUserDetailsService);
        log.info("Sprint 2 TASK 2 : /actuator/** securise via HTTP Basic (user={}, role={})",
                OPS_USERNAME, ROLE);
        return http.build();
    }

    @Bean
    public InMemoryUserDetailsManager opsUserDetailsService(
            @Value("${jurika.observability.prometheus.password}") String password,
            PasswordEncoder passwordEncoder) {
        UserDetails ops = User.withUsername(OPS_USERNAME)
                .password(passwordEncoder.encode(password))
                .roles(ROLE)
                .build();
        return new InMemoryUserDetailsManager(ops);
    }

    @Bean
    public PasswordEncoder opsPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
