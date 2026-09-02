package ma.jurika.ai;

import ma.jurika.ai.llm.FastOcrProperties;
import ma.jurika.ai.llm.LlmProperties;
import ma.jurika.ai.rag.RagProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {"ma.jurika.ai", "ma.jurika.common"})
@EnableDiscoveryClient
@EnableFeignClients
@EnableAsync
@EnableConfigurationProperties({LlmProperties.class, FastOcrProperties.class, RagProperties.class})
public class AiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}
