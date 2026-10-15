package ma.jurika.ai.workflow.depot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Decision du 2026-10-14 (RG-ESS-05) : depot d'un acte genere dans la Data Room PAR LE SERVEUR.
 *
 * <p>Pendant l'essai, le navigateur ne recoit jamais le .docx ; il ne peut donc plus le
 * televerser lui-meme apres la generation. ai-service genere l'acte et l'envoie a
 * dataroom-service avec le jeton de l'utilisateur (meme route, memes controles que le depot
 * depuis l'ecran : responsable du dossier, quotas, limites de l'essai). Hors essai, la meme
 * route sert aussi.
 */
@Component
public class DepotDataroomClient {

    private static final Logger log = LoggerFactory.getLogger(DepotDataroomClient.class);
    private static final String TYPE_DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /** Modes de depot, comme les ecrans : brouillon de generation, nouveau document, nouvelle version. */
    public enum Mode { BROUILLON, NOUVEAU, NOUVELLE_VERSION }

    /** Parametres du depot (ceux que l'ecran envoyait avec le fichier). */
    public record Depot(UUID dossierId, String documentType, String title, String filename, UUID ticketId,
                        Mode mode, UUID existingDocumentId, String motif) {}

    /** Refus de dataroom-service, rendu tel quel a l'ecran (statut et message). */
    public static class DepotRefuseException extends RuntimeException {
        private final int statut;
        private final Map<String, Object> corps;

        public DepotRefuseException(int statut, Map<String, Object> corps) {
            super(String.valueOf(corps.get("message")));
            this.statut = statut;
            this.corps = corps;
        }

        public int statut() { return statut; }
        public Map<String, Object> corps() { return corps; }
    }

    private final RestClient client;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public DepotDataroomClient(@Value("${jurika.dataroom.internal-url:http://dataroom-service:8084}") String url) {
        this(RestClient.builder().baseUrl(url).build());
    }

    DepotDataroomClient(RestClient client) {
        this.client = client;
    }

    public Map<String, Object> deposer(String autorisation, Depot d, byte[] octets) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        HttpHeaders entetePartie = new HttpHeaders();
        entetePartie.setContentType(MediaType.parseMediaType(TYPE_DOCX));
        form.add("file", new org.springframework.http.HttpEntity<>(new ByteArrayResource(octets) {
            @Override
            public String getFilename() {
                return d.filename();
            }
        }, entetePartie));
        String chemin;
        switch (d.mode()) {
            case NOUVELLE_VERSION -> {
                chemin = "/api/v1/dataroom/documents/" + d.existingDocumentId() + "/versions";
                if (d.motif() != null && !d.motif().isBlank()) form.add("motif", d.motif());
            }
            case BROUILLON -> {
                chemin = "/api/v1/dataroom/dossiers/" + d.dossierId() + "/juridique/brouillons";
                form.add("documentType", d.documentType());
                form.add("title", d.title());
                form.add("ticketId", d.ticketId().toString());
            }
            default -> {
                chemin = "/api/v1/dataroom/dossiers/" + d.dossierId() + "/juridique/upload";
                form.add("documentType", d.documentType());
                form.add("title", d.title());
                if (d.ticketId() != null) form.add("ticketId", d.ticketId().toString());
            }
        }
        try {
            String reponse = client.post()
                    .uri(chemin)
                    .header(HttpHeaders.AUTHORIZATION, autorisation)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return reponse == null || reponse.isBlank() ? Map.of() : json.readValue(reponse, new TypeReference<>() {});
        } catch (RestClientResponseException e) {
            Map<String, Object> corps = lireCorps(e.getResponseBodyAsString());
            log.info("Depot de l'acte refuse par la Data Room ({}) : {}", e.getStatusCode().value(), corps.get("message"));
            throw new DepotRefuseException(e.getStatusCode().value(), corps);
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Data Room injoignable pour le depot d'un acte", e);
            Map<String, Object> corps = new LinkedHashMap<>();
            corps.put("code", "DATAROOM_INDISPONIBLE");
            corps.put("message", "L\u2019acte a \u00e9t\u00e9 g\u00e9n\u00e9r\u00e9 mais la Data Room est momentan\u00e9ment "
                    + "injoignable : il n\u2019a pas \u00e9t\u00e9 enregistr\u00e9. R\u00e9essayez dans quelques instants.");
            throw new DepotRefuseException(503, corps);
        }
    }

    /** Type MIME de l'acte depose. */
    public static String typeDocx() {
        return TYPE_DOCX;
    }

    private Map<String, Object> lireCorps(String texte) {
        try {
            Map<String, Object> m = json.readValue(texte, new TypeReference<>() {});
            if (m.get("message") != null) return m;
        } catch (Exception ignoree) {
            // corps non JSON : message generique ci-dessous
        }
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("code", "DEPOT_REFUSE");
        corps.put("message", "La Data Room a refus\u00e9 le d\u00e9p\u00f4t de l\u2019acte.");
        return corps;
    }
}
