package ma.jurika.ai.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.llm.schema.DocumentSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider LLM <b>vision</b> appelant une API OpenAI-compatible multimodale (Ollama recommandé).
 * <p>
 * Activation : {@code jurika.llm.enabled=true} ET {@code jurika.llm.vision-model} non vide.
 * <p>
 * Construit un message {@code user} multipartie OpenAI-compatible :
 * <pre>
 * messages: [
 *   { role: system, content: "&lt;prompt JSON strict&gt;" },
 *   { role: user,   content: [
 *      { type: text,      text: "Document de type X. Extrais les champs..." },
 *      { type: image_url, image_url: { url: "data:image/png;base64,..." } }
 *   ]}
 * ]
 * </pre>
 * Format identique côté OpenAI Vision, Ollama (depuis 0.5), vLLM, LM Studio multimodal.
 * <p>
 * <b>CNDP/loi 09-08</b> : tout reste local quand {@code baseUrl} pointe vers {@code http://localhost:11434/v1}
 * (Ollama). Aucun envoi Internet, aucune fuite de PII.
 * <p>
 * Robustesse : aucune exception propagée — toute erreur HTTP / réseau / parsing produit un
 * {@link LlmExtractionResult#degraded(String, String, String)}, charge au caller de basculer sur le
 * fallback texte (Tesseract+LLM) ou Noop.
 */
@Component
@Primary
@ConditionalOnExpression(
        "${jurika.llm.enabled:false} && '${jurika.llm.vision-model:}' != ''"
)
public class OllamaVisionLlmProvider implements LlmVisionExtractionPort {

    private static final Logger log = LoggerFactory.getLogger(OllamaVisionLlmProvider.class);

    private final LlmProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public OllamaVisionLlmProvider(LlmProperties props, RestTemplateBuilder builder) {
        this.props = props;
        Duration timeout = Duration.ofSeconds(props.visionTimeoutSeconds());
        this.restTemplate = builder
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .build();
        log.info("OllamaVisionLlmProvider actif — baseUrl={} visionModel={} timeout={}s",
                props.baseUrl(), props.visionModel(), props.visionTimeoutSeconds());
    }

    /** Constructeur de test — RestTemplate injecté directement. */
    OllamaVisionLlmProvider(LlmProperties props, RestTemplate restTemplate) {
        this.props = props;
        this.restTemplate = restTemplate;
    }

    @Override
    public boolean isOperational() {
        return props.visionEnabled();
    }

    @Override
    public LlmExtractionResult extractFromImage(byte[] imageBytes, String mimeType, DocumentSchema schema) {
        if (schema == null) {
            return LlmExtractionResult.degraded("ollama-vision", props.visionModel(),
                    "Schéma absent — extraction vision impossible");
        }
        if (imageBytes == null || imageBytes.length == 0) {
            return LlmExtractionResult.degraded("ollama-vision", props.visionModel(),
                    "Image vide — extraction vision impossible");
        }
        if (!props.visionEnabled()) {
            return LlmExtractionResult.degraded("ollama-vision", props.visionModel(),
                    "LLM_VISION_MODEL non défini — extraction vision désactivée");
        }

        String safeMime = (mimeType == null || mimeType.isBlank()) ? "image/png" : mimeType.trim();
        // EX1 2026-06-09 — Downscale + recompress avant base64 quand l'image est plus
        // large que props.visionMaxImageWidth(). Pour une CIN 220-DPI A4 (~1700-2300px),
        // ramener a 1600px reduit la taille du base64 de 35-50% et la latence vision
        // d'autant. Si l'image est deja plus petite, on garde l'original (no-op).
        long t0 = System.nanoTime();
        byte[] payloadBytes = imageBytes;
        String payloadMime = safeMime;
        int maxW = props.visionMaxImageWidth();
        if (maxW > 0) {
            byte[] downscaled = downscaleIfLarger(imageBytes, maxW);
            if (downscaled != null && downscaled.length < imageBytes.length) {
                payloadBytes = downscaled;
                payloadMime = "image/jpeg";
                log.info("VISION downscale {} bytes -> {} bytes (max width {}px, en {} ms)",
                        imageBytes.length, downscaled.length, maxW,
                        (System.nanoTime() - t0) / 1_000_000L);
            }
        }
        String dataUrl = "data:" + payloadMime + ";base64,"
                + Base64.getEncoder().encodeToString(payloadBytes);

        String url = trimTrailingSlash(props.baseUrl()) + "/chat/completions";
        Map<String, Object> body = buildRequestBody(dataUrl, schema);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (props.hasApiKey()) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey());
        }

        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    url, new HttpEntity<>(body, headers), String.class);
            String raw = resp.getBody();
            if (raw == null || raw.isBlank()) {
                return LlmExtractionResult.degraded("ollama-vision", props.visionModel(),
                        "Réponse VISION vide (status=" + resp.getStatusCode() + ")");
            }
            return parseResponse(raw, schema);
        } catch (HttpStatusCodeException e) {
            log.warn("LLM VISION HTTP {} sur {} : {}", e.getStatusCode().value(), url,
                    safeShort(e.getResponseBodyAsString(), 300));
            return LlmExtractionResult.degraded("ollama-vision", props.visionModel(),
                    "Erreur VISION HTTP " + e.getStatusCode().value() + " — fallback texte");
        } catch (RuntimeException e) {
            log.warn("LLM VISION erreur réseau sur {} : {}", url, e.getMessage());
            return LlmExtractionResult.degraded("ollama-vision", props.visionModel(),
                    "Erreur réseau VISION (" + e.getClass().getSimpleName() + ") — fallback texte");
        }
    }

    // ------------------------------------------------------------------------
    //  Construction prompt + body multimodal
    // ------------------------------------------------------------------------

    private Map<String, Object> buildRequestBody(String dataUrl, DocumentSchema schema) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.visionModel());
        body.put("temperature", props.temperature());
        body.put("response_format", Map.of("type", "json_object"));

        List<Map<String, Object>> messages = new ArrayList<>(2);

        // System : prompt JSON strict identique au pipeline texte.
        Map<String, Object> system = new LinkedHashMap<>();
        system.put("role", "system");
        system.put("content", OpenAiCompatibleLlmProvider.buildSystemPrompt(schema));
        messages.add(system);

        // User : message multimodal (texte + image_url base64).
        List<Map<String, Object>> userParts = new ArrayList<>(2);
        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("type", "text");
        textPart.put("text", buildVisionInstruction(schema));
        userParts.add(textPart);

        Map<String, Object> imagePart = new LinkedHashMap<>();
        imagePart.put("type", "image_url");
        imagePart.put("image_url", Map.of("url", dataUrl));
        userParts.add(imagePart);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("role", "user");
        user.put("content", userParts);
        messages.add(user);

        body.put("messages", messages);
        return body;
    }

    /** Instruction ciblée vision (lecture directe de l'image, sans intermédiaire OCR). */
    static String buildVisionInstruction(DocumentSchema schema) {
        return "Voici un document scanné de type " + schema.typeCode() + ". "
                + "Lis directement l'image et extrais les champs définis dans le prompt système. "
                + "Si une valeur est illisible ou absente, retourne null pour ce champ (ne devine pas). "
                + "Respecte strictement les formats demandés (dates ISO YYYY-MM-DD, nombres natifs).";
    }

    // ------------------------------------------------------------------------
    //  Parsing réponse — identique au provider texte (réutilisation du contrat).
    // ------------------------------------------------------------------------

    private LlmExtractionResult parseResponse(String body, DocumentSchema schema) {
        try {
            Map<String, Object> root = mapper.readValue(body, new TypeReference<>() {});
            Object choicesObj = root.get("choices");
            if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
                return LlmExtractionResult.degraded("ollama-vision", props.visionModel(),
                        "Réponse VISION sans 'choices'");
            }
            Object first = choices.get(0);
            if (!(first instanceof Map<?, ?> firstMap)) {
                return LlmExtractionResult.degraded("ollama-vision", props.visionModel(),
                        "Réponse VISION 'choices[0]' invalide");
            }
            Object message = firstMap.get("message");
            if (!(message instanceof Map<?, ?> messageMap)) {
                return LlmExtractionResult.degraded("ollama-vision", props.visionModel(),
                        "Réponse VISION 'message' absent");
            }
            Object content = messageMap.get("content");
            // Ollama peut renvoyer content soit String, soit liste [{type:text,text:"..."},...]
            String contentStr = extractContentString(content);
            if (contentStr == null || contentStr.isBlank()) {
                return LlmExtractionResult.degraded("ollama-vision", props.visionModel(),
                        "Contenu VISION vide");
            }
            Map<String, Object> raw = parseJsonObjectLoose(contentStr);
            Map<String, Object> fields = projectOnSchema(raw, schema);
            return new LlmExtractionResult(
                    fields,
                    Collections.emptyMap(),
                    "ollama-vision",
                    props.visionModel(),
                    false,
                    null
            );
        } catch (RuntimeException | java.io.IOException e) {
            log.warn("LLM VISION parsing échec : {}", e.getMessage());
            return LlmExtractionResult.degraded("ollama-vision", props.visionModel(),
                    "Parsing réponse VISION échec");
        }
    }

    private static String extractContentString(Object content) {
        if (content instanceof String s) return s;
        if (content instanceof List<?> parts) {
            StringBuilder sb = new StringBuilder();
            for (Object p : parts) {
                if (p instanceof Map<?, ?> m) {
                    Object t = m.get("text");
                    if (t instanceof String ts) sb.append(ts);
                }
            }
            return sb.toString();
        }
        return null;
    }

    private Map<String, Object> parseJsonObjectLoose(String content) throws java.io.IOException {
        String trimmed = content.trim();
        try {
            return mapper.readValue(trimmed, new TypeReference<>() {});
        } catch (RuntimeException | java.io.IOException ignored) {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String sub = trimmed.substring(start, end + 1);
                return mapper.readValue(sub, new TypeReference<>() {});
            }
            throw new java.io.IOException("Pas d'objet JSON dans la réponse VISION");
        }
    }

    private Map<String, Object> projectOnSchema(Map<String, Object> raw, DocumentSchema schema) {
        Map<String, Object> out = new HashMap<>();
        for (DocumentSchema.FieldDef f : schema.fields()) {
            out.put(f.name(), raw.get(f.name()));
        }
        return out;
    }

    // ------------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------------

    private static String trimTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String safeShort(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /**
     * EX1 2026-06-09 — Downscale + recompresse en JPEG (qualite haute) si la
     * largeur de l'image source depasse {@code maxWidthPx}. Retourne null si
     * l'image est deja sous le seuil ou si le decodage echoue (le caller garde
     * alors l'original). Conserve le ratio. Pour les CIN 1700-2300px@220DPI,
     * le passage a 1600px JPEG-85 reduit typiquement la taille de 35-50%.
     */
    static byte[] downscaleIfLarger(byte[] src, int maxWidthPx) {
        if (src == null || src.length == 0 || maxWidthPx <= 0) return null;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(src));
            if (img == null) return null;
            int w = img.getWidth();
            int h = img.getHeight();
            if (w <= maxWidthPx) return null; // deja assez petit
            int newW = maxWidthPx;
            int newH = (int) Math.round((double) h * newW / w);
            BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                // Fond blanc pour preserver la lisibilite des images avec alpha.
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, newW, newH);
                g.drawImage(img, 0, 0, newW, newH, null);
            } finally {
                g.dispose();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // JPEG est suffisant pour OCR vision (la vision tolere artefacts JPG).
            if (!ImageIO.write(resized, "jpg", out)) return null;
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            log.debug("VISION downscale skip ({}) : {}", e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }
}
