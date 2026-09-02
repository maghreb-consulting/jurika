package ma.jurika.dataroom.infrastructure.config;

import ma.jurika.common.persistence.RlsAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableAsync
@EnableTransactionManagement
public class JpaConfig {
    @Bean
    public RlsAspect rlsAspect() {
        return new RlsAspect();
    }
}
