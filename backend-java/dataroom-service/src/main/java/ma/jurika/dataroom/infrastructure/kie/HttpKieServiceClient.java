package ma.jurika.dataroom.infrastructure.kie;

import ma.jurika.dataroom.domain.port.KieServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter HTTP vers kie-service. Sync (RestClient), best-effort en mode "fail-safe"
 * (les exceptions sont remappées en {@link KieServiceUnavailableException}, jamais
 * propagées telles quelles à l'API).
 */
public class HttpKieServiceClient implements KieServiceClient {

    private static final Logger log = LoggerFactory.getLogger(HttpKieServiceClient.class);

    private final RestClient client;

    public HttpKieServiceClient(RestClient client) {
        this.client = client;
    }

    @Override
    public KieExtractResponse extract(String docType, String filename, String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Contenu vide");
        }
        if (docType == null || docType.isBlank()) {
            throw new IllegalArgumentException("docType manquant");
        }

        ByteArrayResource fileResource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename != null ? filename : "upload.bin";
            }
        };

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", fileResource);
        form.add("doc_type", docType);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = client.post()
                    .uri("/api/v1/kie/extract")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA_VALUE)
                    .body(form)
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new KieServiceUnavailableException(
                                "kie-service " + res.getStatusCode() + " : " + res.getStatusText());
                    })
                    .body(Map.class);

            if (body == null) {
                throw new KieServiceUnavailableException("kie-service a renvoyé un corps vide");
            }
            return mapResponse(body, docType);

        } catch (KieServiceUnavailableException e) {
            throw e;
        } catch (RestClientException ex) {
            log.warn("kie-service injoignable ({}): {}", docType, ex.getMessage());
            throw new KieServiceUnavailableException("kie-service injoignable : " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    private KieExtractResponse mapResponse(Map<String, Object> raw, String fallbackDocType) {
        Object dt = raw.get("doc_type");
        String docType = dt == null ? fallbackDocType : dt.toString();

        Object src = raw.get("source");
        String source = src == null ? "kie" : src.toString();

        Map<String, String> fields = new LinkedHashMap<>();
        Object rawFields = raw.get("fields");
        if (rawFields instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() == null) continue;
                String k = e.getKey().toString();
                Object v = e.getValue();
                if (v == null) continue;
                fields.put(k, v.toString());
            }
        }

        List<String> warnings = new ArrayList<>();
        Object rawWarnings = raw.get("warnings");
        if (rawWarnings instanceof List<?> l) {
            for (Object w : l) {
                if (w != null) warnings.add(w.toString());
            }
        }

        return new KieExtractResponse(docType, fields, source, warnings);
    }

    @Configuration
    @EnableConfigurationProperties(KieServiceProperties.class)
    public static class HttpKieServiceClientConfig {

        @Bean
        public KieServiceClient kieServiceClient(KieServiceProperties props) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout((int) props.getTimeout().toMillis());
            factory.setReadTimeout((int) props.getTimeout().toMillis());

            RestClient client = RestClient.builder()
                    .baseUrl(props.getBaseUrl())
                    .requestFactory(factory)
                    .build();
            return new HttpKieServiceClient(client);
        }
    }
}
