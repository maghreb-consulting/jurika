package ma.jurika.common.trial;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

/**
 * Sprint 12 — autoconfig pour les 5 microservices qui activent
 * {@link TrialSoftLockFilter} en mode "remote checker".
 *
 * <p>Conditions :
 *  - {@code @ConditionalOnClass} : ne s'active QUE si Feign est present au
 *    classpath (les services sans Feign — discovery/gateway — voient cette
 *    autoconfig ignoree silencieusement).
 *  - {@code @ConditionalOnMissingBean(TrialAccessChecker.class)} :
 *    auth-service qui possede son TrialAccessCheckerJpaImpl local n'est pas
 *    affecte.
 *  - {@code @ConditionalOnProperty} : opt-in via {@code jurika.trial.remote.enabled=true}
 *    pour eviter d'activer le Feign en environnement de test sans auth-service.
 *
 * <p>Ne wire PAS le TrialSoftLockFilter dans la chain Spring Security
 * automatiquement (chaque service doit ajouter
 * {@code .addFilterAfter(trialSoftLockFilter, JwtAuthFilter.class)}
 * dans son {@code SecurityFilterChain} bean — c'est explicite par design,
 * une auto-injection serait magique et casse-pieds a debugger).
 */
@AutoConfiguration
@ConditionalOnClass(name = {"org.springframework.cloud.openfeign.FeignClient",
                             "com.github.benmanes.caffeine.cache.Caffeine"})
@ConditionalOnProperty(prefix = "jurika.trial.remote", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableFeignClients(basePackageClasses = WorkspaceStatusFeignClient.class)
public class TrialRemoteAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TrialAccessChecker.class)
    public TrialAccessChecker remoteTrialAccessChecker(WorkspaceStatusFeignClient client) {
        return new RemoteTrialAccessChecker(client);
    }

    @Bean
    @ConditionalOnMissingBean(TrialSoftLockFilter.class)
    public TrialSoftLockFilter trialSoftLockFilter(TrialAccessChecker checker, ObjectMapper objectMapper) {
        return new TrialSoftLockFilter(checker, objectMapper);
    }
}
