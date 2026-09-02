package ma.jurika.workflow.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;

import java.util.Map;

@FeignClient(name = "ai-service")
public interface AiFeignClient {

    @PostMapping(value = "/api/v1/ai/extract-cn", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Map<String, Object> extractCn(@RequestPart("file") byte[] file);

    @PostMapping(value = "/api/v1/ai/extract-cin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Map<String, Object> extractCin(@RequestPart("file") byte[] file);

    @PostMapping("/api/v1/ai/generate-statuts")
    Map<String, Object> generateStatuts(@RequestBody Map<String, Object> data);
}
