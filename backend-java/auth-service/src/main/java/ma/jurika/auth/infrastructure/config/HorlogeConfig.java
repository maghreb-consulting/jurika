package ma.jurika.auth.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Horloge du service (lot L0, E10d) : injectee dans la verification TOTP pour
 * que les tests puissent la maitriser (anti-rejeu : un test ne doit pas
 * dependre du hasard des fenetres de 30 s).
 */
@Configuration
public class HorlogeConfig {

    @Bean
    public Clock horloge() {
        return Clock.systemUTC();
    }
}
