package ma.jurika.ai.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Appelle le microservice Python {@code ocr-service} (PaddleOCR / docTR) en HTTP.
 * <p>
 * Activation : {@code jurika.ocr.fast.enabled=true} ET {@code jurika.ocr.fast.service-url}
 * renseigné. Sans cela, {@link NoopFastOcrProvider} reste le bean primaire et la chaîne
 * vision/Tesseract historique est conservée intacte.
 * <p>
 * <b>Robustesse</b> :
 * <ul>
 *   <li>{@link #isOperational()} fait un ping {@code GET /health} mis en cache (par défaut 30s)
 *       — évite N requêtes inutiles si le service est down.</li>
 *   <li>Toute erreur réseau / timeout / 5xx renvoie un {@link FastOcrResult#degraded} —
 *       jamais d'exception propagée. {@link GenericExtractionService} bascule alors
 *       silencieusement sur la voie vision puis manuelle.</li>
 *   <li>L'image est envoyée en {@code multipart/form-data} avec son MIME hint d'origine.</li>
 * </ul>
 */
@Component
@Primary
@ConditionalOnProperty(name = "jurika.ocr.fast.enabled", havingValue = "true")
public class HttpFastOcrProvider implements FastOcrPort {

    private static final Logger log = LoggerFactory.getLogger(HttpFastOcrProvider.class);

    private final FastOcrProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    // Cache léger du résultat /health pour limiter le bruit réseau quand le micro-service
    // est down (sinon chaque /extract redéclenche un appel TCP qui fail).
    private final AtomicLong healthCheckedAt = new AtomicLong(0L);
    private volatile boolean healthLastUp = false;

    @Autowired
    public HttpFastOcrProvider(FastOcrProperties props, RestTemplateBuilder builder) {
        this.props = props;
        Duration timeout = Duration.ofSeconds(props.timeoutSeconds());
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(Math.min(5, props.timeoutSeconds())))
                .readTimeout(timeout)
                .build();
        log.info("HttpFastOcrProvider actif — url={} timeout={}s lang={} healthCache={}s",
                props.serviceUrl(), props.timeoutSeconds(), props.lang(), props.healthCacheSeconds());
    }

    /** Constructeur de test — RestTemplate injecté directement. */
    HttpFastOcrProvider(FastOcrProperties props, RestTemplate restTemplate) {
        this.props = props;
        this.restTemplate = restTemplate;
    }

    @Override
    public boolean isOperational() {
        if (!props.isConfigured()) return false;
        long now = System.currentTimeMillis();
        long last = healthCheckedAt.get();
        long ttlMs = props.healthCacheSeconds() * 1000L;
        if (now - last < ttlMs) {
            return healthLastUp;
        }
        boolean up = pingHealth();
        healthLastUp = up;
        healthCheckedAt.set(now);
        return up;
    }

    private boolean pingHealth() {
        String url = trimTrailingSlash(props.serviceUrl()) + "/health";
        try {
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            boolean up = resp.getStatusCode().is2xxSuccessful()
                    && resp.getBody() != null
                    && resp.getBody().contains("\"status\":\"UP\"");
            if (!up) {
                log.debug("OCR rapide /health pas UP (status={}, body={})",
                        resp.getStatusCode(), safeShort(resp.getBody(), 200));
            }
            return up;
        } catch (RuntimeException e) {
            log.debug("OCR rapide /health échec ({}) — service présumé down", e.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public FastOcrResult extract(byte[] imageBytes, String filename, String mimeType) {
        if (!props.isConfigured()) {
            return FastOcrResult.degraded("noop", "OCR rapide non configuré");
        }
        if (imageBytes == null || imageBytes.length == 0) {
            return FastOcrResult.degraded("noop", "Image vide — OCR rapide impossible");
        }

        String url = trimTrailingSlash(props.serviceUrl()) + "/ocr";
        String safeMime = (mimeType == null || mimeType.isBlank()) ? "application/octet-stream" : mimeType;
        String safeName = (filename == null || filename.isBlank()) ? "document.bin" : filename;

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        // ByteArrayResource override getFilename() pour que Spring envoie un champ multipart "file"
        // avec le nom de fichier d'origine, sinon le microservice reçoit "file" sans MIME hint.
        ByteArrayResource part = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return safeName;
            }
        };
        body.add("file", part);
        body.add("lang", props.lang());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        // Hint MIME explicite pour le serveur (utile en logs).
        headers.add("X-File-MIME", safeMime);

        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    url, new HttpEntity<>(body, headers), String.class);
            String raw = resp.getBody();
            if (raw == null || raw.isBlank()) {
                return FastOcrResult.degraded("http", "Réponse OCR vide (status=" + resp.getStatusCode() + ")");
            }
            return parseResponse(raw);
        } catch (HttpStatusCodeException e) {
            log.warn("OCR rapide HTTP {} sur {} : {}", e.getStatusCode().value(), url,
                    safeShort(e.getResponseBodyAsString(), 200));
            // Forcer un re-check au prochain isOperational : ce statut peut être transitoire.
            healthCheckedAt.set(0L);
            return FastOcrResult.degraded("http",
                    "OCR rapide HTTP " + e.getStatusCode().value() + " — fallback vision");
        } catch (ResourceAccessException e) {
            log.warn("OCR rapide timeout / connexion KO sur {} : {}", url, e.getMessage());
            healthCheckedAt.set(0L);
            return FastOcrResult.degraded("http",
                    "OCR rapide injoignable (" + e.getClass().getSimpleName() + ")");
        } catch (RuntimeException e) {
            log.warn("OCR rapide erreur inattendue sur {} : {}", url, e.getMessage());
            healthCheckedAt.set(0L);
            return FastOcrResult.degraded("http",
                    "OCR rapide erreur (" + e.getClass().getSimpleName() + ")");
        }
    }

    // ------------------------------------------------------------------------
    //  Parsing — contrat ocr-service /ocr :
    //  { "text": "...", "lines": [...], "confidence": 0.93, "engine": "doctr",
    //    "ms": 1840, "totalMs": 2010, "lang": "latin", "downscaled": true }
    // ------------------------------------------------------------------------

    private FastOcrResult parseResponse(String body) {
        try {
            Map<String, Object> root = mapper.readValue(body, new TypeReference<>() {});
            String text = asString(root.get("text"), "");
            String engine = asString(root.get("engine"), "unknown");
            double confidence = asDouble(root.get("confidence"), 0.0);
            int ms = asInt(root.get("ms"), 0);
            return new FastOcrResult(text, engine, confidence, ms, false, null);
        } catch (RuntimeException | java.io.IOException e) {
            log.warn("OCR rapide parsing échec : {}", e.getMessage());
            return FastOcrResult.degraded("http", "OCR rapide parsing échec");
        }
    }

    private static String asString(Object o, String def) {
        if (o instanceof String s) return s;
        return o == null ? def : o.toString();
    }

    private static double asDouble(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private static int asInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private static String trimTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String safeShort(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
