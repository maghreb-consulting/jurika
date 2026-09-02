package ma.jurika.supervision;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {"ma.jurika.supervision", "ma.jurika.common"})
@EnableDiscoveryClient
public class SupervisionApplication {
    public static void main(String[] args) {
        SpringApplication.run(SupervisionApplication.class, args);
    }
}
