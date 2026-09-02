package ma.jurika.common.events;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Sprint 12 — autoconfig pour le Feign client {@link BusinessEventPublisher}.
 * Active uniquement si Feign est present au classpath.
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.cloud.openfeign.FeignClient")
@EnableFeignClients(basePackageClasses = BusinessEventPublisher.class)
public class BusinessEventsAutoConfiguration {
}
