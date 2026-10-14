package ma.jurika.dataroom.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.common.trial.ApercuPages;
import ma.jurika.common.trial.RenduApercu;
import ma.jurika.dataroom.domain.port.AiApercuClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Decision du 2026-10-14 : client de la route interne d'apercu d'ai-service. Un 422 d'ai-service
 * (format sans apercu, document illisible) est rendu tel quel ; toute autre erreur est une
 * indisponibilite (503). Jamais de repli sur le fichier d'origine.
 */
@Component
public class HttpAiApercuClient implements AiApercuClient {

    private static final Logger log = LoggerFactory.getLogger(HttpAiApercuClient.class);

    private final RestClient client;
    private final ObjectMapper json = new ObjectMapper();

    public HttpAiApercuClient(AiConversionProperties props) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) props.getTimeout().toMillis());
        f.setReadTimeout((int) props.getTimeout().toMillis());
        this.client = RestClient.builder().baseUrl(props.getBaseUrl()).requestFactory(f).build();
    }

    @Override
    public ApercuPages apercu(byte[] octets, String filename, String contentType, boolean filigrane) {
        try {
            ApercuPages a = client.post()
                    .uri(b -> b.path("/internal/documents/apercu")
                            .queryParam("filename", filename == null ? "" : filename)
                            .queryParam("type", contentType == null ? "" : contentType)
                            .queryParam("filigrane", filigrane)
                            .build())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(octets)
                    .retrieve()
                    .body(ApercuPages.class);
            if (a == null || a.pages() == null || a.pages().isEmpty() || a.filigrane() != filigrane) {
                throw new RenduApercu.ApercuIndisponibleException(
                        "Aper\u00e7u incomplet re\u00e7u du service de mise en page.", null);
            }
            return a;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 422) {
                throw new RenduApercu.ApercuImpossibleException(message(e.getResponseBodyAsString()), e);
            }
            throw indisponible(e);
        } catch (RestClientException e) {
            throw indisponible(e);
        }
    }

    private static RenduApercu.ApercuIndisponibleException indisponible(Exception e) {
        log.warn("Apercu par ai-service indisponible : {}", e.getMessage());
        return new RenduApercu.ApercuIndisponibleException(
                "L\u2019aper\u00e7u de ce document est momentan\u00e9ment indisponible : r\u00e9essayez dans "
                        + "quelques instants.", e);
    }

    private String message(String corps) {
        try {
            JsonNode n = json.readTree(corps);
            if (n.hasNonNull("message")) return n.get("message").asText();
        } catch (Exception ignoree) {
            // corps non JSON : message generique
        }
        return "Aper\u00e7u indisponible pour ce document.";
    }
}
