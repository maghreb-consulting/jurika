package ma.jurika.workflow.infrastructure.client;

import ma.jurika.workflow.domain.port.AiClient;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AiClientAdapter implements AiClient {

    private final AiFeignClient feign;

    public AiClientAdapter(AiFeignClient feign) {
        this.feign = feign;
    }

    @Override
    public Map<String, Object> extractCnFromPdf(byte[] pdfBytes) {
        return feign.extractCn(pdfBytes);
    }

    @Override
    public Map<String, Object> extractCinFromImage(byte[] imageBytes) {
        return feign.extractCin(imageBytes);
    }

    @Override
    public Map<String, Object> generateStatuts(Map<String, Object> data) {
        return feign.generateStatuts(data);
    }
}
