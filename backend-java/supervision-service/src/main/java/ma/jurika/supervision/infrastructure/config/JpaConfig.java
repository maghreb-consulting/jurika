package ma.jurika.supervision.infrastructure.config;

import ma.jurika.common.persistence.RlsAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
public class JpaConfig {
    @Bean
    public RlsAspect rlsAspect() {
        return new RlsAspect();
    }
}
