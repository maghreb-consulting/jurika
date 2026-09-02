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
// Sprint 8 -- @Scheduled pour AlertesEcheancesScheduler (RG-DF20)
@EnableScheduling
public class DataroomApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataroomApplication.class, args);
    }

    /** Bean Clock injectable pour AlertesEcheancesScheduler (testable via override). */
    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
