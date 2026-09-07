package ma.jurika.dataroom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication(scanBasePackages = {"ma.jurika.dataroom", "ma.jurika.common"})
@EnableDiscoveryClient
// Sprint 7 / TASK 5 -- @Async pour ClientAccessLogger (insert non bloquant)
@EnableAsync
// Lot 1 (2026-09-04) — le dernier @Scheduled du service (alertes d'echeances
// fiscales) a disparu avec le dossier fiscal. On conserve @EnableScheduling :
// le cout est nul et un futur planificateur n'aura pas a le redecouvrir.
@EnableScheduling
public class DataroomApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataroomApplication.class, args);
    }

    /** Bean Clock injectable, pour que tout traitement date reste testable. */
    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
