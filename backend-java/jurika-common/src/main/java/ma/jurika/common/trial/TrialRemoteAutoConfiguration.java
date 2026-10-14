package ma.jurika.common.trial;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Sprint 12 -- enregistre le client Feign {@link WorkspaceStatusFeignClient}
 * (auth-service) dans les services qui ont Feign et Caffeine (billing).
 *
 * <p>Lot L7 (E4) : le verificateur d'essai distant et le filtre de l'essai ne
 * sont plus declares ici (ils exigeaient Caffeine, absent de la plupart des
 * services : le controle n'etait actif nulle part). Ils sont dans
 * {@link EssaiAutoConfiguration}, actifs dans tout service web.
 */
@AutoConfiguration
@ConditionalOnClass(name = {"org.springframework.cloud.openfeign.FeignClient",
                             "com.github.benmanes.caffeine.cache.Caffeine"})
@ConditionalOnProperty(prefix = "jurika.trial.remote", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableFeignClients(basePackageClasses = WorkspaceStatusFeignClient.class)
public class TrialRemoteAutoConfiguration {
}
