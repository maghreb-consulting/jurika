package fr.maghreb.gje.services.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.maghreb.gje.dto.document.AIExtractionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component("geminiProvider")
@RequiredArgsConstructor
@Slf4j
public class GeminiExtractionProvider implements AIProvider {

    @Value("${gemini.api.key:}")
    private String apiKey;

    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate;

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty() && !apiKey.contains("AIzaSyVotreCleApiSecreteIci");
    }

    @Override
    public AIExtractionResponse extract(String base64Content) {
        if (!isAvailable()) {
            log.warn("Clé API Gemini absente ou invalide.");
            return buildFallbackResponse();
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            
            String prompt = "Tu es un expert en droit des sociétés marocain. Extrait les informations juridiques du document fourni au format JSON strict. " +
                "Ne retourne QUE le JSON, aucun texte autour. " +
                "Schema JSON attendu: {" +
                "\"denomination\": \"string\", \"formeJuridique\": \"SARL\"|\"SA\"|\"SNC\"|\"SARL_AU\"|\"SAS\"|\"SUCCURSALE\", " +
                "\"capitalSocial\": number, \"siegeSocial\": \"string\", \"activitePrincipale\": \"string\", " +
                "\"ice\": \"string\", \"numeroRC\": \"string\", \"gerants\": [{\"nomPrenom\": \"string\", \"cin\": \"string\"}], " +
                "\"associes\": [{\"nomPrenom\": \"string\", \"parts\": number}], \"dateConstitution\": \"YYYY-MM-DD\", " +
                "\"confidence_scores\": { \"denomination\": 0.9, \"formeJuridique\": 0.9 } }"; // Truncated for brevity

            List<Map<String, Object>> parts = new ArrayList<>();
            parts.add(Map.of("text", prompt));
            
            if (base64Content != null && !base64Content.isEmpty()) {
                parts.add(Map.of("inline_data", Map.of(
                    "mime_type", "image/jpeg",
                    "data", base64Content
                )));
            }
            
            payload.put("contents", List.of(Map.of("parts", parts)));
            payload.put("generationConfig", Map.of("responseMimeType", "application/json"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            String url = GEMINI_URL + apiKey;
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return parseAndMapResponse(response.getBody());
            }

        } catch (Exception e) {
            log.error("Erreur lors de l'extraction par Gemini IA: {}", e.getMessage());
        }

        return buildFallbackResponse();
    }

    @SuppressWarnings("unchecked")
    private AIExtractionResponse parseAndMapResponse(String jsonResponse) {
        try {
            Map<String, Object> root = objectMapper.readValue(jsonResponse, Map.class);
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) root.get("candidates");
            Map<String, Object> contentMap = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) contentMap.get("parts");
            String content = (String) parts.get(0).get("text");
            
            Map<String, Object> data = objectMapper.readValue(content, Map.class);
            Map<String, Object> scores = (Map<String, Object>) data.get("confidence_scores");

            Map<String, Double> confidenceScores = new HashMap<>();
            Map<String, AIExtractionResponse.ExtractionStatus> statuses = new HashMap<>();
            
            List<String> fields = List.of("denomination", "formeJuridique", "capitalSocial", "siegeSocial", "activitePrincipale", "ice", "numeroRC", "dateConstitution");
            for (String field : fields) {
                Double score = scores != null && scores.get(field) != null ? Double.valueOf(scores.get(field).toString()) : 0.0;
                confidenceScores.put(field, score);
                statuses.put(field, mapScoreToStatus(score));
            }

            return AIExtractionResponse.builder()
                .denomination((String) data.get("denomination"))
                .formeJuridique((String) data.get("formeJuridique"))
                .capitalSocial(data.get("capitalSocial") != null ? Double.valueOf(data.get("capitalSocial").toString()) : null)
                .siegeSocial((String) data.get("siegeSocial"))
                .activitePrincipale((String) data.get("activitePrincipale"))
                .ice((String) data.get("ice"))
                .numeroRC((String) data.get("numeroRC"))
                .dateConstitution((String) data.get("dateConstitution"))
                .confidenceScores(confidenceScores)
                .statuses(statuses)
                .build();

        } catch (Exception e) {
            log.error("Échec du parsing de la réponse Gemini: {}", e.getMessage());
            return buildFallbackResponse();
        }
    }

    private AIExtractionResponse.ExtractionStatus mapScoreToStatus(Double score) {
        if (score >= 0.85) return AIExtractionResponse.ExtractionStatus.CONFIRMED;
        if (score >= 0.50) return AIExtractionResponse.ExtractionStatus.TO_VERIFY;
        return AIExtractionResponse.ExtractionStatus.TO_FILL;
    }

    private AIExtractionResponse buildFallbackResponse() {
        return AIExtractionResponse.builder()
            .statuses(new HashMap<>(Map.of(
                "denomination", AIExtractionResponse.ExtractionStatus.TO_FILL,
                "formeJuridique", AIExtractionResponse.ExtractionStatus.TO_FILL,
                "capitalSocial", AIExtractionResponse.ExtractionStatus.TO_FILL,
                "ice", AIExtractionResponse.ExtractionStatus.TO_FILL
            )))
            .confidenceScores(new HashMap<>())
            .build();
    }
}
