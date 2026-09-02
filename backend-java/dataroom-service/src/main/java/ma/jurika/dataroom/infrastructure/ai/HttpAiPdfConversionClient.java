package ma.jurika.dataroom.infrastructure.ai;

import ma.jurika.dataroom.domain.port.AiPdfConversionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Lot AB (2026-07-05) — Adapter HTTP vers l'endpoint interne d'ai-service
 * {@code POST /internal/documents/convert-to-pdf}. Modele : HttpKieServiceClient.
 *
 * <p>Fail-safe : toute erreur (503 LibreOffice absent, injoignable, PDF vide) est
 * remappee en {@link AiPdfConversionUnavailableException} pour que l'apercu
 * retombe proprement sur le document original.
 */
public class HttpAiPdfConversionClient implements AiPdfConversionClient {

    private static final Logger log = LoggerFactory.getLogger(HttpAiPdfConversionClient.class);

    private final RestClient client;

    public HttpAiPdfConversionClient(RestClient client) {
        this.client = client;
    }

    @Override
    public byte[] convertToPdf(byte[] source, String filename) {
        if (source == null || source.length == 0) {
            throw new IllegalArgumentException("source vide");
        }
        String name = (filename == null || filename.isBlank()) ? "document" : filename;
        try {
            byte[] pdf = client.post()
                    .uri(b -> b.path("/internal/documents/convert-to-pdf")
                            .queryParam("filename", name).build())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_PDF_VALUE)
                    .body(source)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new AiPdfConversionUnavailableException(
                                "ai-service convert-to-pdf " + res.getStatusCode());
                    })
                    .body(byte[].class);
            if (pdf == null || pdf.length == 0) {
                throw new AiPdfConversionUnavailableException("ai-service a renvoye un PDF vide");
            }
            return pdf;
        } catch (AiPdfConversionUnavailableException e) {
            throw e;
        } catch (RestClientException ex) {
            log.warn("ai-service convert-to-pdf injoignable ({}) : {}", name, ex.getMessage());
            throw new AiPdfConversionUnavailableException("ai-service injoignable : " + ex.getMessage(), ex);
        }
    }

    @Configuration
    @EnableConfigurationProperties(AiConversionProperties.class)
    public static class HttpAiPdfConversionClientConfig {

        @Bean
        public AiPdfConversionClient aiPdfConversionClient(AiConversionProperties props) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout((int) props.getTimeout().toMillis());
            factory.setReadTimeout((int) props.getTimeout().toMillis());

            RestClient client = RestClient.builder()
                    .baseUrl(props.getBaseUrl())
                    .requestFactory(factory)
                    .build();
            return new HttpAiPdfConversionClient(client);
        }
    }
}
