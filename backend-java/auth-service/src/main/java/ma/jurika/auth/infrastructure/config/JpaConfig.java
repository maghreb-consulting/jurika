package ma.jurika.auth.infrastructure.config;

import ma.jurika.common.persistence.RlsAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class JpaConfig {

    @Bean
    public RlsAspect rlsAspect() {
        return new RlsAspect();
    }
}
